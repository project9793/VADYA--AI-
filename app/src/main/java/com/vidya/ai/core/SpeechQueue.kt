package com.vidya.ai.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Sentences are sent to the voice API as soon as they are written (several in parallel) and played
 * strictly in order. If a sentence cannot be voiced, it and the rest are returned in [onFinish].
 */
class SpeechQueue(
    scope: CoroutineScope,
    private val fetch: (String) -> AudioPlayer.Audio?,
    private val onStart: () -> Unit,
    private val onFinish: (List<String>) -> Unit
) {
    private val input = Channel<String>(Channel.UNLIMITED)
    private val job: Job = scope.launch { process() }

    fun add(text: String) { input.trySend(text) }
    fun close() { input.close() }
    fun cancel() { input.close(); job.cancel() }

    private suspend fun process() {
        val leftover = mutableListOf<String>()
        coroutineScope {
            val out = Channel<Pair<String, Deferred<AudioPlayer.Audio?>>>(Channel.UNLIMITED)
            launch {
                for (s in input) out.send(s to async(Dispatchers.IO) { fetch(s) })
                out.close()
            }
            var failed = false
            var started = false
            for ((s, d) in out) {
                if (failed) { leftover += s; d.cancel(); continue }
                val a = d.await()
                if (a == null) { failed = true; leftover += s; continue }
                if (!started) { started = true; onStart() }
                AudioPlayer.play(a)
            }
        }
        onFinish(leftover)
    }
}
