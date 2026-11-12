package com.vidya.ai.ui.assistant

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidya.ai.core.DeviceActions
import com.vidya.ai.core.DeviceControl
import com.vidya.ai.core.GeminiClient
import com.vidya.ai.core.GeminiSpeaker
import com.vidya.ai.core.LangUtil
import com.vidya.ai.core.Languages
import com.vidya.ai.core.LocalAnswers
import com.vidya.ai.core.MicPermission
import com.vidya.ai.core.NetworkMonitor
import com.vidya.ai.core.SecureStore
import com.vidya.ai.core.VoiceRecorder
import com.vidya.ai.core.WebSearch
import com.vidya.ai.data.ChatMessage
import com.vidya.ai.data.ChatRepository
import com.vidya.ai.data.Conversation
import com.vidya.ai.data.MemoryRepository
import com.vidya.ai.ui.components.OrbState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

data class AssistantUiState(
    val conv: Conversation,
    val orb: OrbState = OrbState.Idle,
    val partial: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val handsFree: Boolean = false,
    val pending: PendingAction? = null
)

/** A call / WhatsApp message waiting for the user's confirmation. */
data class PendingAction(val kind: String, val name: String, val number: String, val message: String)

class AssistantViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = SecureStore(app)
    private val repo = ChatRepository(app)
    private val memory = MemoryRepository(store)
    private val gemini = GeminiClient()
    private val speaker = GeminiSpeaker()
    private val actions = DeviceActions(app)
    private val recorder = VoiceRecorder()
    private val main = Handler(Looper.getMainLooper())
    private val ids = AtomicLong(System.currentTimeMillis())

    private val _ui = MutableStateFlow(AssistantUiState(conv = newConv()))
    val ui: StateFlow<AssistantUiState> = _ui.asStateFlow()

    private val _history = MutableStateFlow<List<Conversation>>(emptyList())
    val history: StateFlow<List<Conversation>> = _history.asStateFlow()

    val online: StateFlow<Boolean> = NetworkMonitor(app).isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // Fallback: Google's own voice window (launched from the UI layer)
    private val _dialogEvents = Channel<Unit>(Channel.BUFFERED)
    val dialogEvents = _dialogEvents.receiveAsFlow()
    val speechLang: String get() = store.sttLang

    private var recJob: Job? = null
    private var speakJob: Job? = null
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var job: Job? = null
    private var voiceTurn = false
    private var sttAttempt = 0
    private var resultDelivered = false

    init {
        refreshHistory()
        initTts("com.google.android.tts")      // Google's neural voices; falls back to default engine
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
        maybeSaveMemory(msg)
        val key = store.geminiKey
        if (key.isBlank()) {
            _ui.update { it.copy(orb = OrbState.Idle, error = "पहले Settings में Gemini API key डालिए।") }
            return
        }
        tts?.stop()
        speakJob?.cancel()
        voiceTurn = voice
        val now = System.currentTimeMillis()
        val conv = _ui.value.conv
        val withUser = conv.copy(
            title = conv.title.ifBlank { msg.take(40) },
            updated = now,
            messages = conv.messages + ChatMessage(ids.incrementAndGet(), "user", msg, now)
        )
        _ui.update { it.copy(conv = withUser, orb = OrbState.Thinking, busy = true, error = null, pending = null) }
        persist(withUser)

        job?.cancel()
        job = viewModelScope.launch {
            // 1) instant answers from the phone itself (date / time): always correct, no delay
            LocalAnswers.tryAnswer(msg)?.let { reply ->
                finishWith(reply, voice, null)
                return@launch
            }
            // 2) phone-control commands (call, WhatsApp, YouTube, music, open app ...)
            if (DeviceControl.looksLikeCommand(msg)) {
                val cmd = gemini.parseCommand(key, store.model, msg).getOrNull()
                if (cmd != null && handleCommand(cmd, voice)) return@launch
            }
            // 3) normal answer; time-sensitive questions get real, fresh web results first
            var hits: List<WebSearch.Hit> = emptyList()
            if (store.searchEnabled && WebSearch.needsFresh(msg)) hits = WebSearch.news(msg)

            val modelId = ids.incrementAndGet()
            val result = gemini.generate(
                key, store.model, withUser.messages, voice,
                buildContext() + WebSearch.asContext(hits), store.searchEnabled, nowLine()
            ) { partial ->
                // answer streams in word by word
                val ts = System.currentTimeMillis()
                _ui.update { st ->
                    val msgs = st.conv.messages
                    val last = msgs.lastOrNull()
                    val newMsgs = if (last != null && last.id == modelId)
                        msgs.dropLast(1) + last.copy(text = partial)
                    else msgs + ChatMessage(modelId, "model", partial, ts)
                    st.copy(conv = st.conv.copy(messages = newMsgs))
                }
            }
            result
                .onSuccess { reply0 ->
                    var reply = reply0
                    if (hits.isNotEmpty() && !reply.contains("\n\nस्रोत:")) {
                        reply += WebSearch.sourcesFooter(hits)
                    } else if (store.searchEnabled && hits.isEmpty() && gemini.lastSearchDropped &&
                        WebSearch.needsFresh(msg)
                    ) {
                        reply += "\n\n⚠ इंटरनेट खोज नहीं चल पाई, यह जवाब पुरानी जानकारी पर आधारित हो सकता है।"
                    }
                    val t = System.currentTimeMillis()
                    val c = _ui.value.conv
                    val done = c.copy(
                        updated = t,
                        messages = c.messages.filterNot { it.id == modelId } +
                            ChatMessage(modelId, "model", reply, t)
                    )
                    _ui.update { it.copy(conv = done, busy = false, orb = OrbState.Idle) }
                    persist(done)
                    if (voice) speak(reply)
                }
                .onFailure { e ->
                    _ui.update { st ->
                        st.copy(
                            conv = st.conv.copy(messages = st.conv.messages.filterNot { it.id == modelId }),
                            busy = false, orb = OrbState.Idle, error = e.message ?: "Error"
                        )
                    }
                }
        }
    }

    private fun nowLine(): String =
        SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.ENGLISH).format(Date()) +
            " (" + TimeZone.getDefault().id + ")"

    /** Adds an assistant message (optionally with a pending confirmation) and speaks it for voice turns. */
    private fun finishWith(reply: String, voice: Boolean, pending: PendingAction?) {
        val t = System.currentTimeMillis()
        val c = _ui.value.conv
        val done = c.copy(
            updated = t,
            messages = c.messages + ChatMessage(ids.incrementAndGet(), "model", reply, t)
        )
        _ui.update { it.copy(conv = done, busy = false, orb = OrbState.Idle, pending = pending) }
        persist(done)
        if (voice) speak(reply)
    }

    private suspend fun handleCommand(cmd: JSONObject, voice: Boolean): Boolean {
        val action = cmd.optString("action")
        val name = cmd.optString("name")
        val nameEn = cmd.optString("name_en")
        val number = cmd.optString("number").filter { it.isDigit() || it == '+' }
        when (action) {
            "call", "whatsapp" -> {
                val isCall = action == "call"
                var display = name.ifBlank { nameEn }
                var num = if (number.filter { it.isDigit() }.length >= 7) number else ""
                if (num.isEmpty() && display.isNotBlank()) {
                    val list = withContext(Dispatchers.IO) { actions.findContact(listOf(name, nameEn)) }
                    val hit = list.firstOrNull { it.name.equals(name, true) || it.name.equals(nameEn, true) }
                        ?: list.firstOrNull()
                    if (hit != null) { display = hit.name; num = hit.number }
                }
                if (isCall && num.isEmpty()) {
                    finishWith(
                        "\"$display\" का नंबर नहीं मिला। Tools → Permissions में Contacts allow करें, या नंबर बोलिए।",
                        voice, null
                    )
                    return true
                }
                val p = PendingAction(if (isCall) "call" else "whatsapp", display, num, cmd.optString("message"))
                val ask = if (isCall) "$display को call लगाऊँ? नीचे देखकर पुष्टि करें।"
                else "$display के लिए WhatsApp संदेश तैयार है। नीचे देखकर पुष्टि करें।"
                finishWith(ask, voice, p)
                return true
            }
            "end_call" -> { finishWith(actions.endCall(), voice, null); return true }
            "open_app" -> { finishWith(actions.openApp(cmd.optString("app")), voice, null); return true }
            "youtube_search" -> {
                val q = cmd.optString("query").ifBlank { name }
                if (q.isNotBlank()) { finishWith(actions.youtubeSearch(q), voice, null); return true }
            }
            "play_music" -> {
                val q = cmd.optString("query").ifBlank { name }
                if (q.isNotBlank()) { finishWith(actions.playMusic(q), voice, null); return true }
            }
        }
        return false
    }

    fun confirmPending(editedMessage: String) {
        val p = _ui.value.pending ?: return
        _ui.update { it.copy(pending = null) }
        val result = when (p.kind) {
            "call" -> actions.call(p.number)
            "whatsapp" -> {
                val r = actions.whatsapp(p.number.ifBlank { null }, editedMessage)
                "$r\nकिसे: ${p.name.ifBlank { "(आप चुनेंगे)" }}\nसंदेश: $editedMessage"
            }
            else -> ""
        }
        if (result.isNotBlank()) finishWith(result, false, null)
    }

    fun cancelPending() {
        _ui.update { it.copy(pending = null) }
        finishWith("ठीक है, रद्द कर दिया।", false, null)
    }

    /** Date/time, names, language and memories that are sent with every request. */
    private fun buildContext(): String {
        val now = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
        return buildString {
            append("Current date and time on the user's device: $now (${TimeZone.getDefault().id}). ")
            append("Use it for any question about today's date or time. ")
            append("The user has named you \"${store.assistantName}\"; introduce yourself by that name. ")
            if (store.userName.isNotBlank()) append("The user's name is ${store.userName}. ")
            append("The user's preferred language is ${Languages.label(store.sttLang)}. ")
            if (store.searchEnabled) append("For news, prices, scores, weather or anything recent, use Google Search. ")
            if (store.memoryEnabled) {
                val m = memory.all().takeLast(30)
                if (m.isNotEmpty()) {
                    append("Things the user asked you to remember: ")
                    m.forEach { append("- ").append(it.text).append(" ") }
                }
            }
        }
    }

    private val rememberRegex = Regex(
        "^(याद रखो|yaad rakho|remember that|remember)\\s*[:,\\-]?\\s+(.+)$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private fun maybeSaveMemory(msg: String) {
        if (!store.memoryEnabled) return
        val m = rememberRegex.find(msg) ?: return
        memory.add(m.groupValues[2].trim())
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
            val c = withContext(Dispatchers.IO) { repo.load(id) } ?: return@launch
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

    // ---------- voice input ----------
    fun toggleHandsFree() = _ui.update { it.copy(handsFree = !it.handsFree) }

    fun toggleMic() {
        when (_ui.value.orb) {
            OrbState.Listening ->
                if (store.voiceEngine == "gemini") recorder.finish() else recognizer?.stopListening()
            OrbState.Speaking -> { tts?.stop(); speakJob?.cancel(); startListening() }   // interrupt the AI
            OrbState.Thinking -> stop()
            OrbState.Idle -> startListening()
        }
    }

    fun stop() {
        job?.cancel()
        recJob?.cancel()
        speakJob?.cancel()
        tts?.stop()
        recognizer?.cancel()
        _ui.update { it.copy(orb = OrbState.Idle, busy = false, partial = "") }
    }

    fun onDialogResult(text: String) = send(text, voice = true)
    fun onDialogCancel() = setOrb(OrbState.Idle)

    private fun switchToDialog() {
        store.useDialog = true
        _ui.update { it.copy(error = "In-app mic नहीं चला, इसलिए Google की voice window से सुन रही हूँ।") }
        startListening()
    }

    /** Default voice path: record with our own mic permission, Gemini transcribes. */
    private fun startRecording() {
        val key = store.geminiKey
        if (key.isBlank()) { showError("पहले Settings में Gemini API key डालिए।"); return }
        if (!MicPermission.has(app)) { showError("Mic permission चाहिए। Settings tab में दें।"); return }
        recJob?.cancel()
        _ui.update { it.copy(orb = OrbState.Listening, partial = "", error = null) }
        recJob = viewModelScope.launch {
            val rec = recorder.record()
            val wav = rec.wav
            if (wav == null) {
                _ui.update { it.copy(orb = OrbState.Idle, partial = "", error = rec.error) }
                return@launch
            }
            _ui.update { it.copy(orb = OrbState.Thinking, busy = true, partial = "") }
            gemini.transcribe(key, store.model, wav, store.sttLang)
                .onSuccess { text ->
                    val t = text.trim()
                    if (t.isBlank() || t.contains("NO_SPEECH")) {
                        _ui.update {
                            it.copy(orb = OrbState.Idle, busy = false, error = "आवाज़ साफ़ नहीं सुनाई दी। दोबारा बोलिए।")
                        }
                    } else {
                        _ui.update { it.copy(busy = false) }
                        send(t, voice = true)
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(orb = OrbState.Idle, busy = false, error = e.message ?: "Error") }
                }
        }
    }

    /** Speech services to try in order: (service, null = system default; pass language?) */
    private fun sttPlans(): List<Pair<ComponentName?, Boolean>> = buildList {
        val g = app.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .firstOrNull { it.serviceInfo.packageName == "com.google.android.googlequicksearchbox" }
        if (g != null) add(ComponentName(g.serviceInfo.packageName, g.serviceInfo.name) to true)
        add(null to true)
        add(null to false)
    }

    private fun startListening(attempt: Int = 0) {
        if (store.voiceEngine == "gemini") { startRecording(); return }
        if (store.useDialog) {
            _ui.update { it.copy(orb = OrbState.Listening, partial = "") }
            _dialogEvents.trySend(Unit)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            showError("इस फ़ोन में speech recognition उपलब्ध नहीं है। Google app install/update करें।")
            return
        }
        sttAttempt = attempt
        resultDelivered = false
        val (service, withLang) = sttPlans()[attempt]
        recognizer?.destroy()
        val r = if (service != null) SpeechRecognizer.createSpeechRecognizer(app, service)
                else SpeechRecognizer.createSpeechRecognizer(app)
        r.setRecognitionListener(listener)
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if (withLang) putExtra(RecognizerIntent.EXTRA_LANGUAGE, store.sttLang)
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
            resultDelivered = true
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            _ui.update { it.copy(partial = "") }
            if (t.isNullOrBlank()) setOrb(OrbState.Idle) else send(t, voice = true)
        }

        override fun onError(error: Int) {
            // Some services send a late error (e.g. 11) after a successful result: ignore it.
            if (resultDelivered || _ui.value.orb != OrbState.Listening) return
            _ui.update { it.copy(partial = "", orb = OrbState.Idle) }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Unit
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    if (MicPermission.has(app)) switchToDialog()
                    else showError("Mic permission चाहिए। Settings tab में दें।")
                }
                11, 12, 13 -> {   // server disconnected / language not supported / unavailable
                    if (sttAttempt + 1 < sttPlans().size) {
                        val next = sttAttempt + 1
                        _ui.update { it.copy(orb = OrbState.Listening) }
                        main.postDelayed({ startListening(next) }, 400)
                    } else {
                        switchToDialog()
                    }
                }
                SpeechRecognizer.ERROR_AUDIO ->
                    showError("Mic कोई और app इस्तेमाल कर रही है। उसे बंद करके दोबारा कोशिश करें।")
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER ->
                    showError("Speech service के लिए internet चाहिए।")
                else -> showError("Speech error ($error)। दोबारा कोशिश करें।")
            }
        }
    }

    // ---------- speaking ----------
    private fun initTts(engine: String?) {
        val onInit = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.setSpeechRate(0.95f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { setOrb(OrbState.Speaking) }
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == LAST_UTTERANCE) main.post { onSpeakDone() }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { main.post { onSpeakDone() } }
                })
            } else if (engine != null) {
                tts?.shutdown()
                tts = null
                ttsReady = false
                initTts(null)
            }
        }
        tts = if (engine != null) TextToSpeech(app, onInit, engine) else TextToSpeech(app, onInit)
    }

    fun speakMessage(text: String) {
        voiceTurn = false
        speak(text)
    }

    private fun speak(text: String) {
        val clean = LangUtil.stripMarkdown(text)
        if (clean.isBlank()) { onSpeakDone(); return }
        val key = store.geminiKey
        if (store.ttsEngine == "gemini" && key.isNotBlank()) {
            speakJob?.cancel()
            setOrb(OrbState.Thinking)
            speakJob = viewModelScope.launch {
                val chunks = speaker.chunksOf(clean)
                val played = try {
                    speaker.speak(key, chunks, store.ttsVoice) { setOrb(OrbState.Speaking) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    0
                }
                if (played >= chunks.size) onSpeakDone()
                else {
                    showError("Gemini आवाज़ नहीं मिली (limit?), बाकी हिस्सा फ़ोन की आवाज़ में बोला।")
                    speakWithAndroid(chunks.drop(played).joinToString(" "))
                }
            }
        } else {
            speakWithAndroid(clean)
        }
    }

    private fun speakWithAndroid(clean: String) {
        val t = tts
        if (t == null || !ttsReady) { onSpeakDone(); return }
        val loc = LangUtil.detect(clean, store.sttLang)
        val r = t.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            showError("इस भाषा की voice फ़ोन में install नहीं है। Phone Settings → Text-to-speech में voice data डाउनलोड करें।")
        } else {
            try {
                val cands = t.voices.orEmpty().filter {
                    it.locale.language == loc.language && !it.features.contains("notInstalled")
                }
                val best = cands.filter { it.locale.country == loc.country }.ifEmpty { cands }
                    .maxByOrNull { it.quality }
                if (best != null) t.voice = best
            } catch (e: Exception) { /* keep default voice */ }
        }
        t.setPitch(1.0f)
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
