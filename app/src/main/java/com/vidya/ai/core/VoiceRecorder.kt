package com.vidya.ai.core

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Records the microphone with the app's own permission and stops when the speaker pauses. */
class VoiceRecorder {

    class Recording(val wav: ByteArray?, val error: String?)

    @Volatile private var finishRequested = false

    /** Stop recording now and keep what was recorded. */
    fun finish() { finishRequested = true }

    @SuppressLint("MissingPermission")
    suspend fun record(
        maxMs: Long = 25_000,
        noSpeechMs: Long = 8_000,
        endSilenceMs: Long = 1_400
    ): Recording = withContext(Dispatchers.IO) {
        finishRequested = false
        val rate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return@withContext Recording(null, "इस फ़ोन पर mic शुरू नहीं हो सका।")

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, rate)
            )
        } catch (e: Exception) {
            return@withContext Recording(null, "Mic शुरू नहीं हुआ: ${e.message}")
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return@withContext Recording(
                null,
                "Mic शुरू नहीं हुआ। कोई और app mic इस्तेमाल कर रही है, या फ़ोन की Privacy settings में Microphone access बंद है।"
            )
        }

        val out = ByteArrayOutputStream()
        val chunk = ShortArray(rate / 10)          // 100 ms
        var speech = false
        try {
            rec.startRecording()
            val start = SystemClock.elapsedRealtime()
            var noise = 0.0
            var calib = 0
            var lastVoice = start
            while (true) {
                ensureActive()
                val n = rec.read(chunk, 0, chunk.size)
                if (n < 0) break
                if (n == 0) continue

                val bytes = ByteArray(n * 2)
                var sum = 0.0
                for (i in 0 until n) {
                    val v = chunk[i].toInt()
                    bytes[2 * i] = (v and 0xFF).toByte()
                    bytes[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
                    sum += v.toDouble() * v.toDouble()
                }
                out.write(bytes)

                val rms = sqrt(sum / n)
                val now = SystemClock.elapsedRealtime()
                if (calib < 3) {
                    noise += rms / 3.0
                    calib++
                } else {
                    val threshold = minOf(maxOf(700.0, noise * 3.0), 2500.0)
                    if (rms > threshold) { speech = true; lastVoice = now }
                }
                if (finishRequested) break
                if (speech && now - lastVoice > endSilenceMs) break
                if (!speech && now - start > noSpeechMs) break
                if (now - start > maxMs) break
            }
        } finally {
            try { rec.stop() } catch (e: Exception) { /* ignore */ }
            rec.release()
        }

        val pcm = out.toByteArray()
        if (pcm.size < 8_000 || (!speech && !finishRequested)) {
            return@withContext Recording(
                null,
                "आवाज़ नहीं सुनाई दी। Mic के पास बोलिए। बार-बार ऐसा हो तो फ़ोन की Settings → Privacy में Microphone access चालू करें।"
            )
        }
        Recording(pcmToWav(pcm, rate), null)
    }

    private fun pcmToWav(pcm: ByteArray, rate: Int): ByteArray {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + pcm.size); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16)
        h.putShort(1.toShort()); h.putShort(1.toShort())
        h.putInt(rate); h.putInt(rate * 2)
        h.putShort(2.toShort()); h.putShort(16.toShort())
        h.put("data".toByteArray()); h.putInt(pcm.size)
        return h.array() + pcm
    }
}
