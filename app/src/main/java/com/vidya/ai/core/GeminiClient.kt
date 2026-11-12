package com.vidya.ai.core

import android.util.Base64
import com.vidya.ai.data.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class GeminiClient {

    private fun systemPrompt(voice: Boolean) = buildString {
        append("You are VIDYA AI, a friendly, accurate personal assistant. ")
        append("Reply in the same language the user writes in (Hindi, Hinglish, English or any other). ")
        append("Do not use markdown symbols like ** or #. Use plain text. ")
        if (voice) append("This is a spoken conversation: answer in 1 to 3 short, natural sentences unless asked for detail. If the user speaks Hindi or Hinglish, reply in Hindi written in Devanagari script, never in Roman letters, so the voice pronounces it correctly. ")
        else append("Be clear and well organised; use short paragraphs or numbered lines. Use code blocks only when writing code. ")
        append("If you are not sure about a fact, say so instead of guessing.")
    }

    private fun chatBody(history: List<ChatMessage>, voice: Boolean): String {
        // Gemini wants alternating roles starting with "user": merge same-role neighbours.
        val merged = mutableListOf<Pair<String, String>>()
        history.takeLast(24).dropWhile { it.role != "user" }.forEach { m ->
            val last = merged.lastOrNull()
            if (last != null && last.first == m.role) {
                merged[merged.lastIndex] = last.first to (last.second + "\n" + m.text)
            } else merged.add(m.role to m.text)
        }
        val contents = JSONArray()
        merged.forEach { (role, text) ->
            contents.put(
                JSONObject().put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", systemPrompt(voice)))))
            .put("contents", contents)
            .toString()
    }

    suspend fun generate(
        apiKey: String, model: String, history: List<ChatMessage>, voice: Boolean
    ): Result<String> = run(apiKey, model, chatBody(history, voice))

    /** Speech-to-text using Gemini audio understanding (works without Google's speech service). */
    suspend fun transcribe(apiKey: String, model: String, wav: ByteArray, lang: String): Result<String> {
        val prompt = "Transcribe this audio exactly as spoken. Keep the original language " +
            "(write Hindi and Hinglish speech in Devanagari script, other Indian languages in their own script). " +
            "The speaker most likely uses language tag $lang. " +
            "Output ONLY the transcript, with no quotes or comments. " +
            "If there is no clear human speech, output exactly [NO_SPEECH]."
        val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
        val body = JSONObject().put("contents", JSONArray().put(
            JSONObject().put("role", "user").put("parts", JSONArray()
                .put(JSONObject().put("inline_data",
                    JSONObject().put("mime_type", "audio/wav").put("data", b64)))
                .put(JSONObject().put("text", prompt)))
        )).toString()
        return run(apiKey, model, body)
    }

    private fun post(apiKey: String, model: String, body: String): Pair<Int, String> {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to raw
    }

    /**
     * Tries the chosen model first. If the server is busy (5xx) it retries once;
     * if a model is busy / over quota / misspelt it falls back to lighter models.
     */
    private suspend fun run(apiKey: String, model: String, body: String): Result<String> =
        withContext(Dispatchers.IO) {
            val models = listOf(
                SecureStore.sanitizeModel(model), "gemini-2.5-flash-lite", "gemini-3.1-flash-lite"
            ).distinct()
            var firstError: Exception? = null
            try {
                for (m in models) {
                    var attempt = 0
                    while (attempt < 2) {
                        val (code, raw) = post(apiKey, m, body)
                        if (code in 200..299) return@withContext Result.success(parse(raw))
                        if (firstError == null) firstError = Exception(friendly(code, raw))
                        val badModel = code == 400 && raw.contains("GenerateContentRequest.model")
                        if (code in 500..504) {
                            attempt++
                            if (attempt < 2) delay(1500)
                        } else if (code == 404 || code == 429 || badModel) {
                            break                       // try the next model
                        } else {
                            return@withContext Result.failure(Exception(friendly(code, raw)))
                        }
                    }
                }
                Result.failure(firstError ?: Exception("जवाब नहीं मिला।"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnknownHostException) {
                Result.failure(Exception("Gemini server का पता नहीं मिल रहा (DNS)। VPN / Private DNS / ad-block बंद करें, या WiFi ↔ mobile data बदलकर देखें।"))
            } catch (e: SocketTimeoutException) {
                Result.failure(Exception("Server से जवाब आने में बहुत देर लगी (timeout)। दोबारा कोशिश करें।"))
            } catch (e: SSLException) {
                Result.failure(Exception("सुरक्षित connection (SSL) नहीं बन पाया। फ़ोन की date/time जाँचें और VPN/proxy बंद करें।"))
            } catch (e: IOException) {
                Result.failure(Exception("Network error: ${e.javaClass.simpleName}: ${e.message}"))
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "कुछ गड़बड़ हो गई।"))
            }
        }

    /** Step-by-step connection check shown in Settings. */
    suspend fun diagnose(apiKey: String): String = withContext(Dispatchers.IO) {
        val host = "generativelanguage.googleapis.com"
        val sb = StringBuilder()
        try {
            sb.append("1) DNS ठीक: ${InetAddress.getByName(host).hostAddress}\n")
        } catch (e: Exception) {
            return@withContext "1) DNS फ़ेल (${e.javaClass.simpleName})। Server का पता नहीं मिला। VPN / Private DNS बंद करें या दूसरा network आज़माएँ।"
        }
        try {
            val conn = (URL("https://$host/v1beta/models?pageSize=1").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("x-goog-api-key", apiKey)
            }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            sb.append("2) HTTP $code\n")
            if (code in 200..299) sb.append("✔ Connection और API key दोनों ठीक हैं।") else sb.append(friendly(code, raw))
        } catch (e: Exception) {
            sb.append("2) Connect फ़ेल: ${e.javaClass.simpleName}: ${e.message}")
        }
        sb.toString()
    }

    private fun parse(raw: String): String {
        val root = JSONObject(raw)
        val cands = root.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) {
            val reason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw Exception("Gemini ने जवाब नहीं दिया" + if (reason.isNotBlank()) " ($reason)।" else "।")
        }
        val cand = cands.getJSONObject(0)
        val parts = cand.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        if (sb.isBlank()) throw Exception("खाली जवाब मिला (${cand.optString("finishReason")})।")
        return sb.toString().trim()
    }

    private fun friendly(code: Int, raw: String): String {
        val detail = try { JSONObject(raw).getJSONObject("error").optString("message") } catch (e: Exception) { "" }
        val head = when (code) {
            400 -> when {
                detail.contains("API key", true) -> "API key सही नहीं है। Settings में जाँचिए।"
                detail.contains("model", true) -> "Model का नाम गलत है। Settings → Gemini model में \"Default\" दबाइए।"
                else -> "Request में गड़बड़ है।"
            }
            401, 403 -> "API key को permission नहीं मिली। Key जाँचिए।"
            404 -> "Model का नाम सही नहीं है। Settings में model बदलिए (जैसे gemini-2.5-flash)।"
            429 -> "Free limit पूरी हो गई। थोड़ी देर बाद कोशिश करें।"
            in 500..599 -> "Gemini server अभी busy है (app ने दूसरे model भी आज़माए)। थोड़ी देर बाद कोशिश करें।"
            else -> "Error $code"
        }
        return if (detail.isNotBlank()) "$head\n($detail)" else head
    }
}
