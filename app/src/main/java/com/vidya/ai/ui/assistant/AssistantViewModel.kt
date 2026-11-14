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
import com.vidya.ai.core.AppGuide
import com.vidya.ai.core.AppPerm
import com.vidya.ai.core.Permissions
import com.vidya.ai.core.AudioPlayer
import com.vidya.ai.core.DeviceActions
import com.vidya.ai.core.DeviceControl
import com.vidya.ai.core.GeminiClient
import com.vidya.ai.core.GeminiSpeaker
import com.vidya.ai.core.GroqClient
import com.vidya.ai.core.LangUtil
import com.vidya.ai.core.Languages
import com.vidya.ai.core.LocalAnswers
import com.vidya.ai.core.MicPermission
import com.vidya.ai.core.NetworkMonitor
import com.vidya.ai.core.SarvamSpeaker
import com.vidya.ai.core.SecureStore
import com.vidya.ai.core.SpeechQueue
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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
    val pending: PendingAction? = null,
    val timing: String = "",
    val guide: GuideAction? = null,
    val tourOn: Boolean = false
)

/** A call / WhatsApp message waiting for the user's confirmation. */
data class PendingAction(
    val kind: String, val name: String, val number: String, val message: String,
    val options: List<String> = emptyList()
)

/** A "go there" suggestion from the guide (route = screen to open), or a tour step card. */
data class GuideAction(val title: String, val text: String, val route: String?, val tour: Boolean = false)

class AssistantViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = SecureStore(app)
    private val repo = ChatRepository(app)
    private val memory = MemoryRepository(store)
    private val gemini = GeminiClient()
    private val speaker = GeminiSpeaker()
    private val actions = DeviceActions(app)
    private val groq = GroqClient()
    private val sarvam = SarvamSpeaker()
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

    // streaming speech: sentences are voiced while the answer is still being written
    @Volatile private var queue: SpeechQueue? = null
    private var emitted = 0
    private var firstChunk = true
    private var turnStart = 0L
    private var timingBase = ""
    private var lastVoiceEngine = ""
    private var sarvamDownUntil = 0L
    private var geminiTtsDownUntil = 0L

    // in-app guide
    private var permWait: CompletableDeferred<Unit>? = null
    private val _permEvents = Channel<Array<String>>(Channel.BUFFERED)
    val permEvents = _permEvents.receiveAsFlow()
    private var tourJob: Job? = null
    private var narrateJob: Job? = null
    private var androidDone: CompletableDeferred<Unit>? = null
    private val _navEvents = Channel<String>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

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
        if (_ui.value.tourOn) {
            stopTour()
            if (AppGuide.isStop(msg)) { finishWith("ठीक है, tour रोक दिया।", false, null); return }
        }
        maybeSaveMemory(msg)
        val key = store.geminiKey
        val groqKey = store.groqKey
        if (key.isBlank() && groqKey.isBlank()) {
            guideTo("api_key", "पहले API key डालनी होगी, उसके बिना मैं जवाब नहीं दे पाऊँगी।")
            return
        }
        tts?.stop()
        speakJob?.cancel()
        narrateJob?.cancel()
        queue?.cancel()
        queue = null
        emitted = 0
        firstChunk = true
        voiceTurn = voice
        val now = System.currentTimeMillis()
        turnStart = now
        val conv = _ui.value.conv
        val withUser = conv.copy(
            title = conv.title.ifBlank { msg.take(40) },
            updated = now,
            messages = conv.messages + ChatMessage(ids.incrementAndGet(), "user", msg, now)
        )
        _ui.update { it.copy(conv = withUser, orb = OrbState.Thinking, busy = true, error = null, pending = null, timing = "") }
        persist(withUser)

        job?.cancel()
        job = viewModelScope.launch {
            // 1) instant answers from the phone itself (date / time): always correct, no delay
            LocalAnswers.tryAnswer(msg)?.let { reply ->
                finishWith(reply, voice, null)
                return@launch
            }
            // 1b) in-app guide: where is what, how to set it up, full tour
            if (AppGuide.isTourRequest(msg)) {
                finishWith("ठीक है, ऐप का पूरा tour शुरू करती हूँ। रोकने के लिए \"रुको\" बोलिए या रोकें दबाइए।", false, null)
                startTour()
                return@launch
            }
            AppGuide.match(msg)?.let { item ->
                answerGuide(item, voice)
                return@launch
            }
            // 2) phone-control commands (call, WhatsApp, YouTube, music, open app ...)
            // the web search starts now and runs while the command check happens (max 2 s)
            var searchHits: List<WebSearch.Hit> = emptyList()
            val searchJob: Job? = if (store.searchEnabled && WebSearch.needsFresh(msg))
                launch { searchHits = withTimeoutOrNull(2_000) { WebSearch.news(msg) } ?: emptyList() }
            else null
            if (DeviceControl.looksLikeCommand(msg)) {
                val cmd = withTimeoutOrNull(3_500) {
                    var c = if (groqKey.isNotBlank()) groq.parseCommand(groqKey, msg).getOrNull() else null
                    if (c == null && key.isNotBlank()) c = gemini.parseCommand(key, store.model, msg).getOrNull()
                    c
                }
                if (cmd != null && handleCommand(cmd, voice)) {
                    searchJob?.cancel()
                    return@launch
                }
            }
            searchJob?.join()
            val hits: List<WebSearch.Hit> = searchHits

            val modelId = ids.incrementAndGet()
            val ctx = buildContext() + WebSearch.asContext(hits) +
                (if (AppGuide.looksLikeAppHelp(msg)) AppGuide.mapText() else "")
            val streamSpeech = voice && naturalEngines().isNotEmpty()
            var tFirst = 0L
            val onPartial: (String) -> Unit = { partial ->
                if (tFirst == 0L) tFirst = System.currentTimeMillis()
                val ts = System.currentTimeMillis()
                _ui.update { st ->
                    val msgs = st.conv.messages
                    val last = msgs.lastOrNull()
                    val newMsgs = if (last != null && last.id == modelId)
                        msgs.dropLast(1) + last.copy(text = partial)
                    else msgs + ChatMessage(modelId, "model", partial, ts)
                    st.copy(conv = st.conv.copy(messages = newMsgs))
                }
                if (streamSpeech) feedSpeech(partial, false)
            }
            // primary AI first; if its limit is over or it fails, the other one takes over automatically
            val order = if (store.primaryAi == "groq") listOf("groq", "gemini") else listOf("gemini", "groq")
            var usedProv = ""
            val errors = mutableListOf<String>()
            var result: Result<String> = Result.failure(Exception("कोई AI key नहीं मिली।"))
            for (prov in order) {
                if (prov == "groq" && groqKey.isBlank()) continue
                if (prov == "gemini" && key.isBlank()) continue
                queue?.cancel(); queue = null; emitted = 0; firstChunk = true; tFirst = 0L
                result = if (prov == "groq") {
                    groq.generate(groqKey, withUser.messages, voice, gemini.systemPrompt(voice, ctx), nowLine(), onPartial)
                } else {
                    gemini.generate(key, store.model, withUser.messages, voice, ctx, store.searchEnabled, nowLine(), onPartial)
                }
                if (result.isSuccess) { usedProv = prov; break }
                errors += (if (prov == "groq") "Groq" else "Gemini") + ": " + (result.exceptionOrNull()?.message ?: "?")
            }
            if (result.isFailure && errors.isNotEmpty()) result = Result.failure(Exception(errors.joinToString("\n")))
            result
                .onSuccess { reply0 ->
                    var reply = reply0
                    if (hits.isNotEmpty() && !reply.contains("\n\nस्रोत:")) {
                        reply += WebSearch.sourcesFooter(hits)
                    } else if (store.searchEnabled && hits.isEmpty() && (usedProv == "groq" || gemini.lastSearchDropped) &&
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
                    _ui.update { it.copy(conv = done, busy = false, orb = if (voice && streamSpeech) it.orb else OrbState.Idle) }
                    persist(done)
                    val tEnd = System.currentTimeMillis()
                    timingBase = "⏱ $usedProv · पहला शब्द " + sec((if (tFirst == 0L) tEnd else tFirst) - turnStart) +
                        "s · जवाब पूरा " + sec(tEnd - turnStart) + "s"
                    _ui.update { it.copy(timing = timingBase) }
                    if (voice) {
                        if (streamSpeech) {
                            feedSpeech(reply0.substringBefore("\n\nस्रोत:").substringBefore("\n\n⚠"), true)
                            val q = queue
                            if (q != null) q.close() else onSpeakDone()
                        } else {
                            speak(reply)
                        }
                    }
                }
                .onFailure { e ->
                    queue?.cancel()
                    queue = null
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
    private fun finishWith(
        reply: String, voice: Boolean, pending: PendingAction?,
        spoken: String? = null, guide: GuideAction? = null, forceSpeak: Boolean = false
    ) {
        val t = System.currentTimeMillis()
        val c = _ui.value.conv
        val done = c.copy(
            updated = t,
            messages = c.messages + ChatMessage(ids.incrementAndGet(), "model", reply, t)
        )
        _ui.update {
            it.copy(
                conv = done, busy = false, orb = OrbState.Idle, pending = pending,
                guide = guide ?: it.guide?.takeIf { g -> g.tour }
            )
        }
        persist(done)
        if (voice || forceSpeak) speak(spoken ?: reply)
    }

    /** Asks for the permission right now (system dialog) if it is missing; waits for the answer. */
    private suspend fun ensurePermission(p: AppPerm): Boolean {
        if (Permissions.isGranted(app, p)) return true
        val req = Permissions.runtimePerms(p)
        if (req.isEmpty()) return false
        val d = CompletableDeferred<Unit>()
        permWait = d
        _permEvents.trySend(req)
        withTimeoutOrNull(60_000) { d.await() }
        permWait = null
        return Permissions.isGranted(app, p)
    }

    fun onPermissionResult() { permWait?.complete(Unit) }

    private suspend fun handleCommand(cmd: JSONObject, voice: Boolean): Boolean {
        val action = cmd.optString("action")
        val name = cmd.optString("name")
        val nameEn = cmd.optString("name_en")
        val number = cmd.optString("number").filter { it.isDigit() || it == '+' }
        val query = cmd.optString("query").ifBlank { name }
        when (action) {
            "call", "whatsapp" -> {
                val isCall = action == "call"
                var display = name.ifBlank { nameEn }
                var num = if (number.filter { it.isDigit() }.length >= 7) number else ""
                var options: List<String> = emptyList()
                if (num.isEmpty() && display.isNotBlank()) {
                    ensurePermission(AppPerm.Contacts)
                    val list = withContext(Dispatchers.IO) { actions.findContact(listOf(name, nameEn)) }
                    val best = list.firstOrNull { it.name.equals(name, true) || it.name.equals(nameEn, true) }
                        ?: list.firstOrNull()
                    if (best != null) { display = best.name; num = best.number }
                    if (list.size > 1) options = list.take(4).map { it.name + "|" + it.number }
                }
                if (isCall && num.isEmpty()) {
                    finishWith(
                        "\"$display\" का नंबर नहीं मिला। Contacts की अनुमति चालू करें, या नंबर बोलिए।",
                        voice, null,
                        guide = GuideAction("Permissions", "", "tools/permissions")
                    )
                    return true
                }
                val p = PendingAction(if (isCall) "call" else "whatsapp", display, num, cmd.optString("message"), options)
                val ask = if (isCall) "$display को call लगाऊँ? नीचे देखकर पुष्टि करें।"
                else "$display के लिए WhatsApp संदेश तैयार है। नीचे देखकर पुष्टि करें।"
                finishWith(ask, voice, p)
                return true
            }
            "end_call" -> {
                ensurePermission(AppPerm.Calls)
                finishWith(actions.endCall(), voice, null)
                return true
            }
            "answer_call" -> {
                ensurePermission(AppPerm.Calls)
                finishWith(actions.answerCall(), voice, null)
                return true
            }
            "open_app" -> { finishWith(actions.openApp(cmd.optString("app")), voice, null); return true }
            "youtube_search" -> {
                if (query.isNotBlank()) { finishWith(actions.youtubeSearch(query), voice, null); return true }
            }
            "youtube_play" -> {
                if (query.isNotBlank()) {
                    val key = store.youtubeKey
                    val r = withContext(Dispatchers.IO) { actions.youtubePlay(query, key) }
                    finishWith(
                        r, voice, null,
                        guide = if (key.isBlank()) GuideAction("YouTube key डालिए", "वीडियो सीधे चलाने के लिए", "tools/phone") else null
                    )
                    return true
                }
            }
            "play_music" -> {
                if (query.isNotBlank()) {
                    val r = withContext(Dispatchers.IO) {
                        actions.playMusic(query, cmd.optString("app"), store.musicApp, store.youtubeKey)
                    }
                    finishWith(r, voice, null)
                    return true
                }
            }
            "media_control" -> {
                finishWith(actions.mediaControl(cmd.optString("query")), voice, null)
                return true
            }
        }
        return false
    }

    fun confirmPending(editedMessage: String) {
        val p = _ui.value.pending ?: return
        _ui.update { it.copy(pending = null) }
        viewModelScope.launch {
            var guide: GuideAction? = null
            val result = when (p.kind) {
                "call" -> {
                    ensurePermission(AppPerm.Calls)
                    actions.call(p.number)
                }
                "whatsapp" -> {
                    val auto = store.waAutoSend && Permissions.isGranted(app, AppPerm.Accessibility)
                    val r = actions.whatsapp(p.number.ifBlank { null }, editedMessage, auto)
                    var extra = ""
                    if (store.waAutoSend && !auto) {
                        extra = "\nअपने आप Send के लिए Tools → Permissions → Accessibility चालू कीजिए।"
                        guide = GuideAction("Accessibility चालू कीजिए", "", "tools/permissions")
                    }
                    r + "\nकिसे: " + p.name.ifBlank { "(आप चुनेंगे)" } + "\nसंदेश: " + editedMessage + extra
                }
                else -> ""
            }
            if (result.isNotBlank()) finishWith(result, false, null, guide = guide)
        }
    }

    fun pickOption(i: Int) {
        val p = _ui.value.pending ?: return
        val o = p.options.getOrNull(i) ?: return
        val parts = o.split("|", limit = 2)
        if (parts.size == 2) _ui.update { it.copy(pending = p.copy(name = parts[0], number = parts[1])) }
    }

    fun cancelPending() {
        _ui.update { it.copy(pending = null) }
        finishWith("ठीक है, रद्द कर दिया।", false, null)
    }

    // ---------------------------------------------------------------- in-app guide

    fun goTo(route: String) {
        _ui.update { it.copy(guide = null) }
        _navEvents.trySend(route)
    }

    fun dismissGuide() = _ui.update { it.copy(guide = null) }

    /** Stops the user from going the wrong way: says why, where to go, and offers to take them there. */
    fun guideTo(id: String, why: String) {
        val item = AppGuide.byId(id) ?: return
        finishWith(
            why + "\n\n📍 " + item.where,
            false, null,
            spoken = why + " " + item.say,
            guide = GuideAction(item.title, "", item.route),
            forceSpeak = store.guideSpeak
        )
    }

    private fun answerGuide(item: AppGuide.Item, voice: Boolean) {
        val text = buildString {
            append("📍 ").append(item.title).append("\n")
            append("जगह: ").append(item.where).append("\n\n")
            item.steps.forEachIndexed { i, s -> append(i + 1).append(". ").append(s).append("\n") }
        }.trim()
        finishWith(
            text, voice, null,
            spoken = item.say,
            guide = item.route?.let { GuideAction(item.title, "", it) },
            forceSpeak = store.guideSpeak
        )
    }

    fun speakGuide(item: AppGuide.Item) {
        voiceTurn = false
        speak(item.say)
    }

    /** Speaks [text] and waits until it has finished (natural voice first, phone voice as backup). */
    private suspend fun speakAndWait(text: String) {
        val clean = LangUtil.stripMarkdown(text)
        if (clean.isBlank()) return
        setOrb(OrbState.Thinking)
        var chunks = speaker.chunksOf(clean)
        val langTag = LangUtil.detect(clean, store.sttLang).toLanguageTag()
        for (e in naturalEngines()) {
            if (chunks.isEmpty()) break
            val played = try {
                if (e == "sarvam") {
                    sarvam.speak(store.sarvamKey, chunks, store.sarvamVoice, langTag) { setOrb(OrbState.Speaking) }
                } else {
                    speaker.speak(store.geminiKey, chunks, store.ttsVoice) { setOrb(OrbState.Speaking) }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (ex: Exception) {
                0
            }
            chunks = chunks.drop(played)
        }
        if (chunks.isEmpty()) { setOrb(OrbState.Idle); return }
        val done = CompletableDeferred<Unit>()
        androidDone = done
        speakWithAndroid(chunks.joinToString(" "))
        done.await()
    }

    /** First visit of a screen: Vidya says what it is for (only once per screen). */
    fun onScreen(route: String) {
        if (!store.guideSpeak || _ui.value.tourOn) return
        if (store.seenGuide(route)) return
        val g = AppGuide.screen(route) ?: return
        store.markSeen(route)
        narrateJob?.cancel()
        narrateJob = viewModelScope.launch {
            delay(700)
            if (_ui.value.orb != OrbState.Idle || _ui.value.busy) return@launch
            voiceTurn = false
            var say = g.say
            val noKeys = store.geminiKey.isBlank() && store.groqKey.isBlank()
            if (route == "home" && noKeys) {
                say += " सबसे पहले टूल्स टैब में एपीआई की डालिए, उसके बिना मैं जवाब नहीं दे पाऊँगी।"
                _ui.update { it.copy(guide = GuideAction("पहला काम: API Key डालिए", "Tools टैब → 6. API Key", "tools/keys")) }
            }
            speakAndWait(say)
        }
    }

    fun startTour() {
        stopTour()
        tts?.stop(); speakJob?.cancel(); queue?.cancel(); queue = null
        voiceTurn = false
        _ui.update { it.copy(tourOn = true, busy = false, orb = OrbState.Idle) }
        tourJob = viewModelScope.launch {
            try {
                val steps = AppGuide.tour
                for ((i, s) in steps.withIndex()) {
                    _ui.update { it.copy(guide = GuideAction("Tour ${i + 1}/${steps.size}: ${s.title}", s.say, null, true)) }
                    _navEvents.trySend(s.route)
                    delay(600)
                    speakAndWait(s.say)
                    delay(500)
                }
            } finally {
                _ui.update {
                    it.copy(
                        tourOn = false, guide = null,
                        orb = if (it.orb == OrbState.Speaking || it.orb == OrbState.Thinking) OrbState.Idle else it.orb
                    )
                }
            }
        }
    }

    fun stopTour() {
        val wasOn = _ui.value.tourOn
        tourJob?.cancel()
        tourJob = null
        if (wasOn) {
            androidDone?.complete(Unit)
            tts?.stop()
            _ui.update { it.copy(tourOn = false, guide = null, orb = OrbState.Idle) }
        }
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
            OrbState.Speaking -> { stopTour(); narrateJob?.cancel(); tts?.stop(); speakJob?.cancel(); queue?.cancel(); queue = null; startListening() }   // interrupt the AI
            OrbState.Thinking -> stop()
            OrbState.Idle -> startListening()
        }
    }

    fun stop() {
        stopTour()
        narrateJob?.cancel()
        job?.cancel()
        recJob?.cancel()
        speakJob?.cancel()
        queue?.cancel()
        queue = null
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
        if (key.isBlank() && store.groqKey.isBlank()) { guideTo("api_key", "पहले API key डालनी होगी, उसके बिना मैं सुन या जवाब नहीं दे पाऊँगी।"); return }
        if (!MicPermission.has(app)) { guideTo("permissions", "माइक की अनुमति चालू करनी होगी।"); return }
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
            transcribeSmart(wav)
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

    /** Whisper on Groq first (fast, big free limit); Gemini is the backup. */
    private suspend fun transcribeSmart(wav: ByteArray): Result<String> {
        var r: Result<String> = Result.failure(Exception("कोई key नहीं मिली।"))
        if (store.groqKey.isNotBlank()) r = groq.transcribe(store.groqKey, wav, store.sttLang)
        if ((r.isFailure || r.getOrNull().isNullOrBlank()) && store.geminiKey.isNotBlank()) {
            r = gemini.transcribe(store.geminiKey, store.model, wav, store.sttLang)
        }
        return r
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
                    else guideTo("permissions", "माइक की अनुमति चालू करनी होगी।")
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

    private fun sec(ms: Long): String = "%.1f".format(ms / 1000.0)

    private fun naturalEngines(): List<String> {
        val pref = store.ttsEngine
        if (pref == "android") return emptyList()
        return (listOf(pref) + listOf("sarvam", "gemini")).distinct().filter {
            (it == "sarvam" && store.sarvamKey.isNotBlank()) || (it == "gemini" && store.geminiKey.isNotBlank())
        }
    }

    /** One sentence -> natural voice audio. An engine that just failed is skipped for a minute. */
    private fun fetchVoice(text: String): AudioPlayer.Audio? {
        val clean = LangUtil.stripMarkdown(text)
        if (clean.isBlank()) return null
        val langTag = LangUtil.detect(clean, store.sttLang).toLanguageTag()
        for (e in naturalEngines()) {
            val now = System.currentTimeMillis()
            if (e == "sarvam" && sarvam.supports(langTag) && now > sarvamDownUntil) {
                val a = sarvam.fetchOne(store.sarvamKey, clean, store.sarvamVoice, langTag)
                if (a != null) { lastVoiceEngine = "Sarvam"; return a }
                sarvamDownUntil = System.currentTimeMillis() + 60_000
            }
            if (e == "gemini" && now > geminiTtsDownUntil) {
                val a = speaker.fetchOne(store.geminiKey, clean, store.ttsVoice)
                if (a != null) { lastVoiceEngine = "Gemini"; return a }
                geminiTtsDownUntil = System.currentTimeMillis() + 60_000
            }
        }
        return null
    }

    private fun startQueue(): SpeechQueue {
        val q = SpeechQueue(
            viewModelScope,
            fetch = { s -> fetchVoice(s) },
            onStart = {
                setOrb(OrbState.Speaking)
                _ui.update {
                    it.copy(timing = timingBase + " · आवाज़ शुरू " + sec(System.currentTimeMillis() - turnStart) +
                        "s (" + lastVoiceEngine + ")")
                }
            },
            onFinish = { leftover ->
                if (leftover.isEmpty()) onSpeakDone()
                else {
                    showError("स्वाभाविक आवाज़ नहीं मिली (limit या key?), बाकी हिस्सा फ़ोन की आवाज़ में बोला।")
                    speakWithAndroid(leftover.joinToString(" "))
                }
            }
        )
        queue = q
        return q
    }

    /** End index (exclusive) of the next sentence chunk that is at least [minLen] long, or -1. */
    private fun nextChunk(text: String, from: Int, minLen: Int, final: Boolean): Int {
        var i = maxOf(from, from + minLen - 1)
        while (i < text.length) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?' || c == '।' || c == '\n') {
                val ok = if (i == text.lastIndex) final else (c == '\n' || text[i + 1].isWhitespace())
                if (ok) return i + 1
            }
            i++
        }
        return -1
    }

    /** Sends every finished sentence of the growing answer to the voice queue. */
    private fun feedSpeech(text: String, final: Boolean) {
        while (emitted < text.length) {
            val end = nextChunk(text, emitted, if (firstChunk) 20 else 70, final)
            val e = if (end >= 0) end else if (final) text.length else return
            val piece = text.substring(emitted, e)
            emitted = e
            if (LangUtil.stripMarkdown(piece).isBlank()) continue
            firstChunk = false
            (queue ?: startQueue()).add(piece)
        }
    }

    private fun speak(text: String) {
        val clean = LangUtil.stripMarkdown(text)
        if (clean.isBlank()) { onSpeakDone(); return }
        val pref = store.ttsEngine
        val engines = if (pref == "android") emptyList() else
            (listOf(pref) + listOf("sarvam", "gemini")).distinct().filter {
                (it == "sarvam" && store.sarvamKey.isNotBlank()) || (it == "gemini" && store.geminiKey.isNotBlank())
            }
        if (engines.isEmpty()) { speakWithAndroid(clean); return }

        speakJob?.cancel()
        setOrb(OrbState.Thinking)
        speakJob = viewModelScope.launch {
            var chunks = speaker.chunksOf(clean)
            val langTag = LangUtil.detect(clean, store.sttLang).toLanguageTag()
            for (e in engines) {
                if (chunks.isEmpty()) break
                val played = try {
                    if (e == "sarvam") {
                        sarvam.speak(store.sarvamKey, chunks, store.sarvamVoice, langTag) { setOrb(OrbState.Speaking) }
                    } else {
                        speaker.speak(store.geminiKey, chunks, store.ttsVoice) { setOrb(OrbState.Speaking) }
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (ex: Exception) {
                    0
                }
                chunks = chunks.drop(played)
            }
            if (chunks.isEmpty()) onSpeakDone()
            else {
                showError("स्वाभाविक आवाज़ नहीं मिली (limit या key?), बाकी हिस्सा फ़ोन की आवाज़ में बोला।")
                speakWithAndroid(chunks.joinToString(" "))
            }
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
        androidDone?.complete(Unit)
        androidDone = null
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
