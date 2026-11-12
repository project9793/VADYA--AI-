package com.vidya.ai.core

import com.vidya.ai.data.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.net.URL

class GeminiClient {

    private fun systemPrompt(voice: Boolean) = buildString {
        append("You are VIDYA AI, a friendly, accurate personal assistant. ")
        append("Reply in the same language the user writes in (Hindi, Hinglish, English or any other). ")
        append("Do not use markdown symbols like ** or #. Use plain text. ")
        if (voice) append("This is a spoken conversation: answer in 1 to 3 short, natural sentences unless asked for detail. ")
        else append("Be clear and well organised; use short paragraphs or numbered lines. Use code blocks only when writing code. ")
        append("If you are not sure about a fact, say so instead of guessing.")
    }

    suspend fun generate(
        apiKey: String, model: String, history: List<ChatMessage>, voice: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
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
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt(voice)))))
                .put("contents", contents)

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()

            if (code !in 200..299) Result.failure(Exception(friendly(code, raw)))
            else Result.success(parse(raw))
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Gemini server का पता नहीं मिल रहा (DNS)। VPN / Private DNS / ad-block बंद करें, या WiFi ↔ mobile data बदलकर देखें। फिर Settings → Connection test चलाइए।"))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Server से जवाब आने में बहुत देर लगी (timeout)। Network slow है, दोबारा कोशिश करें।"))
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
            400 -> if (detail.contains("API key", true)) "API key सही नहीं है। Settings में जाँचिए।" else "Request में गड़बड़ है।"
            401, 403 -> "API key को permission नहीं मिली। Key जाँचिए।"
            404 -> "Model का नाम सही नहीं है। Settings में model बदलिए (जैसे gemini-2.5-flash)।"
            429 -> "Free limit पूरी हो गई। थोड़ी देर बाद कोशिश करें।"
            in 500..599 -> "Gemini server अभी busy है। थोड़ी देर बाद कोशिश करें।"
            else -> "Error $code"
        }
        return if (detail.isNotBlank()) "$head\n($detail)" else head
    }
}
