package com.vidya.ai.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Natural, human-like voice using Gemini text-to-speech. */
class GeminiSpeaker {

    private class Audio(val pcm: ByteArray, val rate: Int, val channels: Int)

    // Newest first. 3.8 models use a newer request schema than 3.1 / 2.5 models.
    private val models = listOf("gemini-3.8-flash-lite-tts", "gemini-3.1-flash-tts-preview")

    /** Speaks [text]. Returns true if audio was played. Throws CancellationException when cancelled. */
    suspend fun speak(apiKey: String, text: String, voice: String, onStart: () -> Unit): Boolean =
        coroutineScope {
            val chunks = splitText(text).take(4)
            val fetches = chunks.map { c -> async(Dispatchers.IO) { fetch(apiKey, c, voice) } }
            var played = false
            try {
                for (f in fetches) {
                    val audio = f.await() ?: break
                    if (!played) { played = true; onStart() }
                    play(audio)
                }
            } finally {
                fetches.forEach { it.cancel() }
            }
            played
        }

    private fun fetch(apiKey: String, text: String, voice: String): Audio? {
        for (m in models) {
            try {
                val (code, raw) = post(apiKey, m, body(m, text, voice))
                if (code in 200..299) {
                    val a = parseAudio(raw)
                    if (a != null) return a
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // try the next model
            }
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
            connectTimeout = 20_000
            readTimeout = 45_000
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

    private fun parseAudio(raw: String): Audio? {
        val parts = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            val inl = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data") ?: continue
            val b64 = inl.optString("data")
            if (b64.isBlank()) continue
            val mime = inl.optString("mimeType", inl.optString("mime_type", ""))
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            return if (bytes.size > 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") parseWav(bytes)
            else Audio(bytes, rateFrom(mime), 1)
        }
        return null
    }

    private fun rateFrom(mime: String): Int =
        Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24_000

    private fun parseWav(bytes: ByteArray): Audio? {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var rate = 24_000
        var ch = 1
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = bb.getInt(pos + 4)
            if (id == "fmt ") {
                ch = bb.getShort(pos + 10).toInt()
                rate = bb.getInt(pos + 12)
            }
            if (id == "data") {
                val start = pos + 8
                val end = if (size <= 0 || start + size > bytes.size) bytes.size else start + size
                return Audio(bytes.copyOfRange(start, end), rate, if (ch == 2) 2 else 1)
            }
            if (size < 0) break
            pos += 8 + size + (size and 1)
        }
        return null
    }

    private suspend fun play(a: Audio) = withContext(Dispatchers.IO) {
        val frameBytes = 2 * a.channels
        val size = a.pcm.size - a.pcm.size % frameBytes
        if (size <= 0) return@withContext
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(a.rate)
                    .setChannelMask(
                        if (a.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                    )
                    .build()
            )
            .setBufferSizeInBytes(size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(a.pcm, 0, size)
            val frames = size / frameBytes
            track.play()
            val deadline = SystemClock.elapsedRealtime() + frames * 1000L / a.rate + 1_500
            while (track.playbackHeadPosition < frames && SystemClock.elapsedRealtime() < deadline) {
                ensureActive()
                delay(40)
            }
        } finally {
            try { track.stop() } catch (e: Exception) { /* ignore */ }
            track.release()
        }
    }

    private fun splitText(t: String, max: Int = 700): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (s in t.split(Regex("(?<=[.!?।\\n])\\s*"))) {
            if (s.isBlank()) continue
            if (sb.isNotEmpty() && sb.length + s.length > max) {
                out += sb.toString().trim()
                sb.clear()
            }
            sb.append(s).append(' ')
        }
        if (sb.isNotBlank()) out += sb.toString().trim()
        return out
    }
}
