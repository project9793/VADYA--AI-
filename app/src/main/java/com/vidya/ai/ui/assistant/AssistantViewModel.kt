package com.vidya.ai.ui.assistant

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidya.ai.core.GeminiClient
import com.vidya.ai.core.LangUtil
import com.vidya.ai.core.NetworkMonitor
import com.vidya.ai.core.SecureStore
import com.vidya.ai.data.ChatMessage
import com.vidya.ai.data.ChatRepository
import com.vidya.ai.data.Conversation
import com.vidya.ai.ui.components.OrbState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class AssistantUiState(
    val conv: Conversation,
    val orb: OrbState = OrbState.Idle,
    val partial: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val handsFree: Boolean = false
)

class AssistantViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = SecureStore(app)
    private val repo = ChatRepository(app)
    private val gemini = GeminiClient()
    private val main = Handler(Looper.getMainLooper())
    private val ids = AtomicLong(System.currentTimeMillis())

    private val _ui = MutableStateFlow(AssistantUiState(conv = newConv()))
    val ui: StateFlow<AssistantUiState> = _ui.asStateFlow()

    private val _history = MutableStateFlow<List<Conversation>>(emptyList())
    val history: StateFlow<List<Conversation>> = _history.asStateFlow()

    val online: StateFlow<Boolean> = NetworkMonitor(app).isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var job: Job? = null
    private var voiceTurn = false

    init {
        refreshHistory()
        tts = TextToSpeech(app) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { setOrb(OrbState.Speaking) }
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == LAST_UTTERANCE) main.post { onSpeakDone() }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { main.post { onSpeakDone() } }
                })
            }
        }
    }

    // ---------- helpers ----------
    private fun newConv() = Conversation(UUID.randomUUID().toString(), "", System.currentTimeMillis(), emptyList())
    private fun setOrb(o: OrbState) = _ui.update { it.copy(orb = o) }
    fun showError(msg: String) = _ui.update { it.copy(error = msg) }

    private fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) { _history.value = repo.loadAll() }
    }

    private fun persist(c: Conversation) {
        if (c.messages.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.save(c)
            _history.value = repo.loadAll()
        }
    }

    // ---------- sending ----------
    fun sendText(text: String) = send(text, voice = false)

    private fun send(text: String, voice: Boolean) {
        val msg = text.trim()
        if (msg.isEmpty()) return
        val key = store.geminiKey
        if (key.isBlank()) {
            _ui.update { it.copy(orb = OrbState.Idle, error = "पहले Settings में Gemini API key डालिए।") }
            return
        }
        tts?.stop()
        voiceTurn = voice
        val now = System.currentTimeMillis()
        val conv = _ui.value.conv
        val withUser = conv.copy(
            title = conv.title.ifBlank { msg.take(40) },
            updated = now,
            messages = conv.messages + ChatMessage(ids.incrementAndGet(), "user", msg, now)
        )
        _ui.update { it.copy(conv = withUser, orb = OrbState.Thinking, busy = true, error = null) }
        persist(withUser)

        job?.cancel()
        job = viewModelScope.launch {
            gemini.generate(key, store.model, withUser.messages, voice)
                .onSuccess { reply ->
                    val t = System.currentTimeMillis()
                    val c = _ui.value.conv
                    val done = c.copy(
                        updated = t,
                        messages = c.messages + ChatMessage(ids.incrementAndGet(), "model", reply, t)
                    )
                    _ui.update { it.copy(conv = done, busy = false, orb = OrbState.Idle) }
                    persist(done)
                    if (voice) speak(reply)
                }
                .onFailure { e ->
                    _ui.update { it.copy(busy = false, orb = OrbState.Idle, error = e.message ?: "Error") }
                }
        }
    }

    fun regenerate() {
        val msgs = _ui.value.conv.messages
        val idx = msgs.indexOfLast { it.role == "user" }
        if (idx < 0) return
        val lastUser = msgs[idx]
        _ui.update { it.copy(conv = it.conv.copy(messages = msgs.take(idx))) }
        send(lastUser.text, voice = false)
    }

    // ---------- conversations ----------
    fun newChat() {
        stop()
        _ui.update { AssistantUiState(conv = newConv(), handsFree = it.handsFree) }
    }

    fun openConversation(id: String) {
        viewModelScope.launch {
            val c = kotlinx.coroutines.withContext(Dispatchers.IO) { repo.load(id) } ?: return@launch
            stop()
            _ui.update { it.copy(conv = c, error = null) }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(id)
            _history.value = repo.loadAll()
        }
        if (_ui.value.conv.id == id) newChat()
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.clearAll()
            _history.value = emptyList()
        }
        newChat()
    }

    // ---------- voice ----------
    fun toggleHandsFree() = _ui.update { it.copy(handsFree = !it.handsFree) }

    fun toggleMic() {
        when (_ui.value.orb) {
            OrbState.Listening -> recognizer?.stopListening()
            OrbState.Speaking -> { tts?.stop(); startListening() }   // interrupt the AI
            OrbState.Thinking -> stop()
            OrbState.Idle -> startListening()
        }
    }

    fun stop() {
        job?.cancel()
        tts?.stop()
        recognizer?.cancel()
        _ui.update { it.copy(orb = OrbState.Idle, busy = false, partial = "") }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            showError("इस फ़ोन में speech recognition उपलब्ध नहीं है। Google app install/update करें।")
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(app)
        r.setRecognitionListener(listener)
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, store.sttLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        _ui.update { it.copy(orb = OrbState.Listening, partial = "", error = null) }
        r.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) _ui.update { it.copy(partial = t) }
        }

        override fun onResults(results: Bundle?) {
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            _ui.update { it.copy(partial = "") }
            if (t.isNullOrBlank()) setOrb(OrbState.Idle) else send(t, voice = true)
        }

        override fun onError(error: Int) {
            _ui.update { it.copy(partial = "", orb = OrbState.Idle) }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Unit
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    showError("Mic permission चाहिए। Settings tab में दें।")
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER ->
                    showError("Speech service के लिए internet चाहिए।")
                else -> showError("Speech error ($error)। दोबारा कोशिश करें।")
            }
        }
    }

    // ---------- speaking ----------
    fun speakMessage(text: String) {
        voiceTurn = false
        speak(text)
    }

    private fun speak(text: String) {
        val t = tts
        val clean = LangUtil.stripMarkdown(text)
        if (t == null || !ttsReady || clean.isBlank()) { onSpeakDone(); return }
        val r = t.setLanguage(LangUtil.detect(clean, store.sttLang))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            showError("इस भाषा की voice फ़ोन में install नहीं है। Phone Settings → Text-to-speech में voice data डाउनलोड करें।")
        }
        val chunks = chunk(clean)
        chunks.forEachIndexed { i, c ->
            val last = i == chunks.lastIndex
            t.speak(
                c,
                if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                if (last) LAST_UTTERANCE else "part$i"
            )
        }
    }

    private fun onSpeakDone() {
        setOrb(OrbState.Idle)
        if (_ui.value.handsFree && voiceTurn) startListening()
    }

    private fun chunk(s: String, max: Int = 3000): List<String> {
        val out = mutableListOf<String>()
        var rest = s
        while (rest.length > max) {
            var cut = rest.lastIndexOfAny(charArrayOf('.', '।', '?', '!', '\n'), max)
            if (cut < max / 2) cut = max
            out += rest.substring(0, cut + 1).trim()
            rest = rest.substring(cut + 1)
        }
        if (rest.isNotBlank()) out += rest.trim()
        return out
    }

    override fun onCleared() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onCleared()
    }

    companion object { private const val LAST_UTTERANCE = "vidya_last" }
}
