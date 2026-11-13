package com.vidya.ai.core

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Natural Indian-language voice (Sarvam Bulbul). Falls back through a few request styles. */
class SarvamSpeaker {

    @Volatile private var variant = 0       // remembers the request style that worked

    /** Sarvam supports these languages; anything else returns null so another voice is used. */
    private fun langOf(tag: String): String? {
        val l = tag.substringBefore('-').lowercase()
        return when (l) {
            "hi", "bn", "ta", "te", "kn", "ml", "mr", "gu", "pa" -> "$l-IN"
            "en" -> "en-IN"
            "or" -> "od-IN"
            else -> null
        }
    }

    suspend fun speak(
        apiKeys: String, chunks: List<String>, speaker: String, langTag: String, onStart: () -> Unit
    ): Int = coroutineScope {
        val lang = langOf(langTag) ?: return@coroutineScope 0
        val keys = Keys.list(apiKeys)
        val fetches = chunks.take(4).map { c -> async(Dispatchers.IO) { fetch(keys, c, speaker, lang) } }
        var played = 0
        try {
            for (f in fetches) {
                val a = f.await() ?: break
                if (played == 0) onStart()
                AudioPlayer.play(a)
                played++
            }
        } finally {
            fetches.forEach { it.cancel() }
        }
        played
    }

    fun supports(langTag: String): Boolean = langOf(langTag) != null

    /** One sentence -> audio (blocking). null = unsupported language or failure. */
    fun fetchOne(apiKeys: String, text: String, speaker: String, langTag: String): AudioPlayer.Audio? {
        val lang = langOf(langTag) ?: return null
        return fetch(Keys.list(apiKeys), text, speaker, lang)
    }

    private fun fetch(keys: List<String>, text: String, speaker: String, lang: String): AudioPlayer.Audio? {
        for (k in keys) {
            val order = (variant until 3) + (0 until variant)
            for (v in order) {
                try {
                    val (code, raw) = post(k, body(v, text, speaker, lang))
                    if (code in 200..299) {
                        val a = parse(raw)
                        if (a != null) { variant = v; return a }
                    }
                    if (code == 401 || code == 403 || code == 429) break     // key problem: next key
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // try the next request style
                }
            }
        }
        return null
    }

    private fun body(v: Int, text: String, speaker: String, lang: String): String {
        val o = JSONObject().put("text", text)
        when (v) {
            0 -> o.put("target_language_code", lang).put("model", "bulbul:v3").put("speaker", speaker)
            1 -> o.put("language_code", lang).put("model", "bulbul:v3").put("speaker", speaker)
            else -> o.put("target_language_code", lang).put("model", "bulbul:v2").put("speaker", "anushka")
        }
        return o.toString()
    }

    private fun post(apiKey: String, body: String): Pair<Int, String> {
        val conn = (URL("https://api.sarvam.ai/text-to-speech").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("api-subscription-key", apiKey)
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to raw
    }

    private fun parse(raw: String): AudioPlayer.Audio? {
        val audios = JSONObject(raw).optJSONArray("audios") ?: return null
        if (audios.length() == 0) return null
        val bytes = Base64.decode(audios.getString(0), Base64.DEFAULT)
        return if (bytes.size > 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") AudioPlayer.parseWav(bytes)
        else null
    }
}
