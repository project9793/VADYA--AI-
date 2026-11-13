package com.vidya.ai.core

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Natural, human-like voice using Gemini text-to-speech. */
class GeminiSpeaker {

    // Newest first. 3.8 models use a newer request schema than 3.1 / 2.5 models.
    private val models = listOf("gemini-3.8-flash-lite-tts", "gemini-3.1-flash-tts-preview")

    /** First chunk is short so speech starts quickly; the rest are longer. */
    fun chunksOf(text: String): List<String> = splitText(text)

    /** Returns how many chunks were played (the caller speaks the rest with another voice). */
    suspend fun speak(apiKeys: String, chunks: List<String>, voice: String, onStart: () -> Unit): Int =
        coroutineScope {
            val fetches = chunks.take(4).map { c -> async(Dispatchers.IO) { fetch(apiKeys, c, voice) } }
            var played = 0
            try {
                for (f in fetches) {
                    val audio = f.await() ?: break
                    if (played == 0) onStart()
                    AudioPlayer.play(audio)
                    played++
                }
            } finally {
                fetches.forEach { it.cancel() }
            }
            played
        }

    /** One sentence -> audio (blocking). */
    fun fetchOne(apiKeys: String, text: String, voice: String): AudioPlayer.Audio? = fetch(apiKeys, text, voice)

    private fun fetch(apiKeys: String, text: String, voice: String): AudioPlayer.Audio? {
        for (k in Keys.list(apiKeys)) {
            var quota = false
            for (m in models) {
                try {
                    val (code, raw) = post(k, m, body(m, text, voice))
                    if (code in 200..299) {
                        val a = parseAudio(raw)
                        if (a != null) return a
                    }
                    if (code == 429) quota = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // try the next model
                }
            }
            if (!quota) break          // not a quota problem: another key will not help
        }
        return null
    }

    private fun body(model: String, text: String, voice: String): String {
        val voiceCfg = if (model.contains("3.8")) JSONObject().put("voice", voice)
        else JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))
        return JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            ))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", voiceCfg)))
            .toString()
    }

    private fun post(apiKey: String, model: String, body: String): Pair<Int, String> {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 20_000
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

    private fun parseAudio(raw: String): AudioPlayer.Audio? {
        val parts = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            val inl = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data") ?: continue
            val b64 = inl.optString("data")
            if (b64.isBlank()) continue
            val mime = inl.optString("mimeType", inl.optString("mime_type", ""))
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            return if (bytes.size > 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") AudioPlayer.parseWav(bytes)
            else AudioPlayer.Audio(bytes, rateFrom(mime), 1)
        }
        return null
    }

    private fun rateFrom(mime: String): Int =
        Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24_000

    private fun splitText(t: String, max: Int = 600): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (s in t.split(Regex("(?<=[.!?।\\n])\\s*"))) {
            if (s.isBlank()) continue
            sb.append(s).append(' ')
            val limit = if (out.isEmpty()) 80 else max      // short first chunk = quick start
            if (sb.length >= limit) {
                out += sb.toString().trim()
                sb.clear()
            }
        }
        if (sb.isNotBlank()) out += sb.toString().trim()
        return out
    }
}
