package com.vidya.ai.core

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
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Groq: very fast, generous free limits. Several keys can be rotated. */
class GroqClient {

    private val base = "https://api.groq.com/openai/v1"
    private val chatModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant")
    private val sttModel = "whisper-large-v3-turbo"

    private fun conn(path: String, key: String): HttpURLConnection =
        (URL(base + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 25_000
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("User-Agent", "VidyaAI/1.0")
        }

    private fun postJson(key: String, path: String, body: String): Pair<Int, String> {
        val c = conn(path, key).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val raw = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return code to raw
    }

    private suspend fun postStream(key: String, body: String, onPartial: (String) -> Unit): Pair<Int, String> {
        val c = conn("/chat/completions", key).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        if (code !in 200..299) {
            val raw = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            return code to raw
        }
        val sb = StringBuilder()
        c.inputStream.bufferedReader().use { r ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = r.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val d = line.removePrefix("data:").trim()
                if (d == "[DONE]") break
                if (d.isEmpty()) continue
                val t = try {
                    JSONObject(d).optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("delta")?.opt("content") as? String ?: ""
                } catch (e: Exception) { "" }
                if (t.isNotEmpty()) {
                    sb.append(t)
                    onPartial(sb.toString().trim())
                }
            }
        }
        c.disconnect()
        val msg = JSONObject().put("role", "assistant").put("content", sb.toString())
        return 200 to JSONObject().put("choices", JSONArray().put(JSONObject().put("message", msg))).toString()
    }

    private fun parseChat(raw: String): String {
        val content = JSONObject(raw).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content").trim()
        if (content.isBlank()) throw Exception("Groq से खाली जवाब मिला।")
        return content
    }

    private fun messages(history: List<ChatMessage>, system: String, nowLine: String): JSONArray {
        val arr = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        val h = history.takeLast(24).dropWhile { it.role != "user" }
        h.forEachIndexed { i, m ->
            var text = m.text.substringBefore("\n\nस्रोत:").substringBefore("\n\n⚠")
            if (i == h.lastIndex && m.role == "user" && nowLine.isNotBlank()) {
                text += "\n\n[Device date/time right now: $nowLine]"
            }
            arr.put(JSONObject().put("role", if (m.role == "model") "assistant" else "user").put("content", text))
        }
        return arr
    }

    suspend fun generate(
        apiKeys: String, history: List<ChatMessage>, voice: Boolean, system: String,
        nowLine: String, onPartial: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val keys = Keys.list(apiKeys)
        val msgs = messages(history, system, nowLine)
        var firstError: Exception? = null
        try {
            outer@ for (model in chatModels) {
                var ki = 0
                var attempt = 0
                while (ki < keys.size && attempt < 2) {
                    val body = JSONObject().put("model", model).put("messages", msgs)
                        .put("temperature", 0.6).put("max_tokens", if (voice) 400 else 1500)
                        .put("stream", onPartial != null).toString()
                    val (code, raw) =
                        if (onPartial != null) postStream(keys[ki], body, onPartial)
                        else postJson(keys[ki], "/chat/completions", body)
                    if (code in 200..299) return@withContext Result.success(parseChat(raw))
                    if (firstError == null) firstError = Exception(friendly(code, raw))
                    when {
                        code == 429 -> ki++                                   // next key, then next model
                        code in 500..599 -> { attempt++; if (attempt < 2) delay(500) }
                        code == 404 || (code == 400 && raw.contains("model", true)) -> continue@outer
                        else -> return@withContext Result.failure(Exception(friendly(code, raw)))
                    }
                }
            }
            Result.failure(firstError ?: Exception("Groq से जवाब नहीं मिला।"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Groq server नहीं मिल रहा। Internet / VPN जाँचें।"))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Groq से जवाब आने में देर लगी (timeout)।"))
        } catch (e: IOException) {
            Result.failure(Exception("Groq network error: ${e.javaClass.simpleName}"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Groq में गड़बड़ हुई।"))
        }
    }

    private fun whisperLang(tag: String): String? {
        val l = tag.substringBefore('-').lowercase()
        return when (l) {
            "hi", "en", "ml", "kn", "ta", "te", "mr", "bn", "gu", "pa", "ur", "ne",
            "es", "fr", "de", "ar", "ja", "zh", "as", "sa" -> l
            "bho" -> "hi"
            else -> null
        }
    }

    /** Speech-to-text with Whisper (multilingual). */
    suspend fun transcribe(apiKeys: String, wav: ByteArray, langTag: String): Result<String> =
        withContext(Dispatchers.IO) {
            val keys = Keys.list(apiKeys)
            val boundary = "----vidya" + System.currentTimeMillis()
            val lang = whisperLang(langTag)
            var firstError: Exception? = null
            try {
                for (k in keys) {
                    val c = conn("/audio/transcriptions", k).apply {
                        requestMethod = "POST"
                        readTimeout = 45_000
                        doOutput = true
                        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    }
                    c.outputStream.use { out ->
                        fun field(name: String, value: String) {
                            out.write(
                                ("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                                    .toByteArray()
                            )
                        }
                        field("model", sttModel)
                        field("response_format", "json")
                        field("temperature", "0")
                        if (lang != null) field("language", lang)
                        out.write(
                            ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n" +
                                "Content-Type: audio/wav\r\n\r\n").toByteArray()
                        )
                        out.write(wav)
                        out.write("\r\n--$boundary--\r\n".toByteArray())
                    }
                    val code = c.responseCode
                    val raw = (if (code in 200..299) c.inputStream else c.errorStream)
                        ?.bufferedReader()?.use { it.readText() } ?: ""
                    c.disconnect()
                    if (code in 200..299) {
                        return@withContext Result.success(JSONObject(raw).optString("text").trim())
                    }
                    if (firstError == null) firstError = Exception(friendly(code, raw))
                    if (code != 429) break
                }
                Result.failure(firstError ?: Exception("Groq speech नहीं चला।"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Groq speech error"))
            }
        }

    /** Spoken phone-control request -> JSON (same prompt as Gemini). */
    suspend fun parseCommand(apiKeys: String, text: String): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            var err: Exception? = null
            try {
                for (k in Keys.list(apiKeys)) {
                    val body = JSONObject().put("model", "llama-3.1-8b-instant")
                        .put("messages", JSONArray().put(
                            JSONObject().put("role", "user").put("content", DeviceControl.prompt(text))
                        ))
                        .put("temperature", 0)
                        .put("response_format", JSONObject().put("type", "json_object"))
                        .toString()
                    val (code, raw) = postJson(k, "/chat/completions", body)
                    if (code in 200..299) return@withContext Result.success(JSONObject(parseChat(raw)))
                    if (err == null) err = Exception(friendly(code, raw))
                    if (code != 429) break
                }
                Result.failure(err ?: Exception("Groq नहीं चला।"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun diagnose(apiKeys: String): String = withContext(Dispatchers.IO) {
        val k = Keys.list(apiKeys).firstOrNull() ?: return@withContext "key खाली है।"
        try {
            val c = conn("/models", k).apply { connectTimeout = 10_000; readTimeout = 10_000 }
            val code = c.responseCode
            val raw = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            if (code in 200..299) "✔ Groq key ठीक है।" else friendly(code, raw)
        } catch (e: Exception) {
            "Groq से connect नहीं हुआ: ${e.javaClass.simpleName}"
        }
    }

    private fun friendly(code: Int, raw: String): String {
        val detail = try { JSONObject(raw).getJSONObject("error").optString("message") } catch (e: Exception) { "" }
        val head = when (code) {
            401 -> "Groq API key सही नहीं है।"
            403 -> "Groq ने request रोक दी (403)। VPN बंद करके देखें।"
            429 -> "Groq की limit पूरी हो गई।"
            in 500..599 -> "Groq server busy है।"
            else -> "Groq error $code"
        }
        return if (detail.isNotBlank()) "$head\n($detail)" else head
    }
}
