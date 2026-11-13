package com.vidya.ai.core

import android.util.Base64
import com.vidya.ai.data.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

    private val lite = "gemini-2.5-flash-lite"
    private val lite3 = "gemini-3.1-flash-lite"

    /** True when Google Search had to be dropped for the last request. */
    @Volatile var lastSearchDropped = false

    fun systemPrompt(voice: Boolean, extra: String) = buildString {
        append("You are a friendly, accurate personal assistant inside the VIDYA AI app. ")
        append("Reply in the same language the user writes in (Hindi, Hinglish, English or any other). ")
        append("Do not use markdown symbols like ** or #. Use plain text. ")
        if (voice) append("This is a spoken conversation: answer in 1 to 3 short, natural sentences unless asked for detail. If the user speaks Hindi or Hinglish, reply in Hindi written in Devanagari script, never in Roman letters, so the voice pronounces it correctly. ")
        else append("Be clear and well organised; use short paragraphs or numbered lines. Use code blocks only when writing code. ")
        append("Start with the answer immediately, no preamble. If you are not sure about a fact, say so instead of guessing. ")
        append("Your built-in knowledge is OUTDATED (it ends in early 2025). Anything that can change over time ")
        append("(news, elections and who holds office, sports, prices, weather, product releases, 'latest', 'today', 'now') ")
        append("must be answered ONLY from the web results given below or from Google Search, never from memory; ")
        append("if you cannot verify it, say so. Never state a date or time from memory: use the device date given below. ")
        append(extra)
    }

    private fun chatBody(
        model: String, history: List<ChatMessage>, voice: Boolean, extra: String, search: Boolean,
        nowLine: String
    ): String {
        // Gemini wants alternating roles starting with "user": merge same-role neighbours.
        val merged = mutableListOf<Pair<String, String>>()
        history.takeLast(24).dropWhile { it.role != "user" }.forEach { m ->
            val last = merged.lastOrNull()
            if (last != null && last.first == m.role) {
                merged[merged.lastIndex] = last.first to (last.second + "\n" + m.text)
            } else merged.add(m.role to m.text)
        }
        if (nowLine.isNotBlank() && merged.isNotEmpty() && merged.last().first == "user") {
            merged[merged.lastIndex] = "user" to (merged.last().second + "\n\n[Device date/time right now: $nowLine]")
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
                JSONArray().put(JSONObject().put("text", systemPrompt(voice, extra)))))
            .put("contents", contents)
        if (search) {
            body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        }
        // 2.5 models "think" before answering; switching it off makes replies much faster.
        if (model.contains("2.5")) {
            body.put("generationConfig",
                JSONObject().put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
        }
        return body.toString()
    }

    /**
     * [extra] = date/time, names, language, memories. [search] = Google Search for live info.
     * [onPartial] receives the text-so-far while the answer streams in.
     */
    suspend fun generate(
        apiKey: String, model: String, history: List<ChatMessage>, voice: Boolean,
        extra: String = "", search: Boolean = false, nowLine: String = "",
        onPartial: ((String) -> Unit)? = null
    ): Result<String> {
        val m = SecureStore.sanitizeModel(model)
        return execute(apiKey, listOf(m, lite, lite3).distinct(), search, onPartial) { mm, tools ->
            chatBody(mm, history, voice, extra, tools, nowLine)
        }
    }

    /** Turns a spoken phone-control request into JSON (call, whatsapp, open_app, ...). */
    suspend fun parseCommand(apiKey: String, model: String, text: String): Result<JSONObject> {
        val prompt = DeviceControl.prompt(text)
        val m = SecureStore.sanitizeModel(model)
        val r = execute(apiKey, listOf(lite, m, lite3).distinct(), false, null) { mm, _ ->
            val gen = JSONObject().put("responseMimeType", "application/json")
            if (mm.contains("2.5")) gen.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            JSONObject().put("contents", JSONArray().put(
                JSONObject().put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            )).put("generationConfig", gen).toString()
        }
        return r.mapCatching { raw ->
            JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        }
    }

    /** Speech-to-text with Gemini audio understanding (fast lite model first). */
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
        val m = SecureStore.sanitizeModel(model)
        return execute(apiKey, listOf(lite, m, lite3).distinct(), false, null) { _, _ -> body }
    }

    private fun openConn(apiKey: String, model: String, stream: Boolean): HttpURLConnection {
        val method = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:$method")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
    }

    private fun post(apiKey: String, model: String, body: String): Pair<Int, String> {
        val conn = openConn(apiKey, model, false)
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to raw
    }

    /** Streaming call. Returns a synthetic normal response so parse() can be reused. */
    private suspend fun postStream(
        apiKey: String, model: String, body: String, onPartial: (String) -> Unit
    ): Pair<Int, String> {
        val conn = openConn(apiKey, model, true)
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val raw = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            return code to raw
        }
        val sb = StringBuilder()
        val titles = LinkedHashSet<String>()
        var finish = ""
        conn.inputStream.bufferedReader().use { reader ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val json = line.removePrefix("data:").trim()
                if (json.isEmpty()) continue
                val o = try { JSONObject(json) } catch (e: Exception) { continue }
                val cand = o.optJSONArray("candidates")?.optJSONObject(0) ?: continue
                var added = false
                val parts = cand.optJSONObject("content")?.optJSONArray("parts")
                if (parts != null) for (i in 0 until parts.length()) {
                    val t = parts.getJSONObject(i).optString("text")
                    if (t.isNotEmpty()) { sb.append(t); added = true }
                }
                val gc = cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
                if (gc != null) for (i in 0 until gc.length()) {
                    val title = gc.getJSONObject(i).optJSONObject("web")?.optString("title").orEmpty()
                    if (title.isNotBlank()) titles.add(title)
                }
                val fr = cand.optString("finishReason")
                if (fr.isNotBlank()) finish = fr
                if (added) onPartial(sb.toString().trim())
            }
        }
        conn.disconnect()

        val chunks = JSONArray()
        titles.forEach { chunks.put(JSONObject().put("web", JSONObject().put("title", it))) }
        val cand = JSONObject()
            .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sb.toString()))))
            .put("finishReason", finish)
            .put("groundingMetadata", JSONObject().put("groundingChunks", chunks))
        return 200 to JSONObject().put("candidates", JSONArray().put(cand)).toString()
    }

    /**
     * Tries models in order. Server busy (5xx) -> one quick retry; model busy / over quota / misspelt
     * -> next model; Google Search problem -> same request without search.
     */
    private suspend fun execute(
        apiKey: String, models: List<String>, tools: Boolean,
        onPartial: ((String) -> Unit)?, build: (String, Boolean) -> String
    ): Result<String> = withContext(Dispatchers.IO) {
        var useTools = tools
        lastSearchDropped = false
        val keys = Keys.list(apiKey).ifEmpty { listOf(apiKey) }
        var firstError: Exception? = null
        try {
            for (m in models) {
                var ki = 0
                var attempt = 0
                while (attempt < 2) {
                    val body = build(m, useTools)
                    val (code, raw) =
                        if (onPartial != null) postStream(keys[ki], m, body, onPartial)
                        else post(keys[ki], m, body)
                    if (code in 200..299) return@withContext Result.success(parse(raw))
                    if (firstError == null) firstError = Exception(friendly(code, raw))
                    val badModel = code == 400 && raw.contains("GenerateContentRequest.model")
                    if (useTools && !badModel &&
                        ((code == 400 && !raw.contains("API key", true)) || code == 429)
                    ) {
                        useTools = false              // retry without Google Search
                        lastSearchDropped = true
                        continue
                    }
                    if (code in 500..504) {
                        attempt++
                        if (attempt < 2) delay(600)
                    } else if (code == 429 && ki + 1 < keys.size) {
                        ki++                          // next API key
                    } else if (code == 404 || code == 429 || badModel) {
                        break                         // next model
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

    /** Step-by-step connection check shown in the API Key screen. */
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
                setRequestProperty("x-goog-api-key", Keys.list(apiKey).firstOrNull() ?: apiKey)
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

        val titles = LinkedHashSet<String>()
        val chunks = cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
        if (chunks != null) for (i in 0 until chunks.length()) {
            val t = chunks.getJSONObject(i).optJSONObject("web")?.optString("title").orEmpty()
            if (t.isNotBlank()) titles.add(t)
        }
        var out = sb.toString().trim()
        if (titles.isNotEmpty()) out += "\n\nस्रोत:\n" + titles.take(4).joinToString("\n") { "• $it" }
        return out
    }

    private fun friendly(code: Int, raw: String): String {
        val detail = try { JSONObject(raw).getJSONObject("error").optString("message") } catch (e: Exception) { "" }
        val head = when (code) {
            400 -> when {
                detail.contains("API key", true) -> "API key सही नहीं है। Tools → API Key में जाँचिए।"
                detail.contains("model", true) -> "Model का नाम गलत है। Tools → API Key → Gemini model में \"Default\" दबाइए।"
                else -> "Request में गड़बड़ है।"
            }
            401, 403 -> "API key को permission नहीं मिली। Key जाँचिए।"
            404 -> "Model का नाम सही नहीं है। Tools → API Key में model बदलिए (जैसे gemini-2.5-flash)।"
            429 -> "Free limit पूरी हो गई। थोड़ी देर बाद कोशिश करें।"
            in 500..599 -> "Gemini server अभी busy है (app ने दूसरे model भी आज़माए)। थोड़ी देर बाद कोशिश करें।"
            else -> "Error $code"
        }
        return if (detail.isNotBlank()) "$head\n($detail)" else head
    }
}
