package com.vidya.ai.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Plays PCM / WAV speech returned by the voice APIs. */
object AudioPlayer {

    class Audio(val pcm: ByteArray, val rate: Int, val channels: Int)

    fun parseWav(bytes: ByteArray): Audio? {
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

    suspend fun play(a: Audio) = withContext(Dispatchers.IO) {
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
}
