package com.vidya.ai.ui.tools

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidya.ai.core.GeminiClient
import com.vidya.ai.core.GeminiSpeaker
import com.vidya.ai.core.GroqClient
import com.vidya.ai.core.SarvamSpeaker
import com.vidya.ai.core.SecureStore
import com.vidya.ai.core.WebSearch
import com.vidya.ai.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vidya.ai.data.MemoryItem
import com.vidya.ai.data.MemoryRepository
import kotlinx.coroutines.launch

/** Holds everything the Tools screens show and change (activity-scoped, shared by all screens). */
class ToolsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    private val gemini = GeminiClient()
    private val groq = GroqClient()
    private val speaker = GeminiSpeaker()
    private val sarvam = SarvamSpeaker()
    private val memRepo = MemoryRepository(store)

    var userName by mutableStateOf(store.userName); private set
    var assistantName by mutableStateOf(store.assistantName); private set
    var ttsEngine by mutableStateOf(store.ttsEngine); private set
    var ttsVoice by mutableStateOf(store.ttsVoice); private set
    var sarvamVoice by mutableStateOf(store.sarvamVoice); private set
    var primaryAi by mutableStateOf(store.primaryAi); private set
    var sttLang by mutableStateOf(store.sttLang); private set
    var voiceEngine by mutableStateOf(store.voiceEngine); private set
    var searchEnabled by mutableStateOf(store.searchEnabled); private set
    var memoryEnabled by mutableStateOf(store.memoryEnabled); private set
    var guideSpeak by mutableStateOf(store.guideSpeak); private set
    var musicApp by mutableStateOf(store.musicApp); private set
    var waAutoSend by mutableStateOf(store.waAutoSend); private set
    val savedYoutube: String get() = store.youtubeKey
    var hasGeminiKey by mutableStateOf(store.geminiKey.isNotBlank()); private set
    var hasGroqKey by mutableStateOf(store.groqKey.isNotBlank()); private set
    var hasSarvamKey by mutableStateOf(store.sarvamKey.isNotBlank()); private set
    val memories = mutableStateListOf<MemoryItem>().also { it.addAll(memRepo.all()) }

    val savedGemini: String get() = store.geminiKey
    val savedGroq: String get() = store.groqKey
    val savedSarvam: String get() = store.sarvamKey
    val savedSearch: String get() = store.searchKey
    val savedModel: String get() = store.model

    fun saveUserName(v: String) { store.userName = v.trim(); userName = store.userName }
    fun saveAssistantName(v: String) {
        store.assistantName = v.trim().ifBlank { SecureStore.DEFAULT_ASSISTANT }
        assistantName = store.assistantName
    }
    fun updateTtsEngine(v: String) { store.ttsEngine = v; ttsEngine = v }
    fun updateTtsVoice(v: String) { store.ttsVoice = v; ttsVoice = v }
    fun updateSarvamVoice(v: String) { store.sarvamVoice = v; sarvamVoice = v }
    fun updatePrimaryAi(v: String) { store.primaryAi = v; primaryAi = v }
    fun setLang(v: String) { store.sttLang = v; sttLang = v }
    fun updateVoiceEngine(v: String) { store.voiceEngine = v; voiceEngine = v }
    fun updateSearchEnabled(v: Boolean) { store.searchEnabled = v; searchEnabled = v }
    fun updateMemoryEnabled(v: Boolean) { store.memoryEnabled = v; memoryEnabled = v }
    fun updateGuideSpeak(v: Boolean) { store.guideSpeak = v; guideSpeak = v }
    fun updateMusicApp(v: String) { store.musicApp = v; musicApp = v }
    fun updateWaAutoSend(v: Boolean) { store.waAutoSend = v; waAutoSend = v }
    fun saveYoutube(key: String) { store.youtubeKey = key.trim() }
    fun resetGuides() { store.resetSeen() }

    fun saveGemini(key: String) { store.geminiKey = key.trim(); hasGeminiKey = store.geminiKey.isNotBlank() }
    fun saveGroq(key: String) { store.groqKey = key.trim(); hasGroqKey = store.groqKey.isNotBlank() }
    fun saveSarvam(key: String) { store.sarvamKey = key.trim(); hasSarvamKey = store.sarvamKey.isNotBlank() }
    fun saveSearch(key: String) { store.searchKey = key.trim() }
    fun saveModel(m: String) { store.model = m }

    // ----- memory -----
    fun reloadMemories() { memories.clear(); memories.addAll(memRepo.all()) }
    fun addMemory(text: String) { memRepo.add(text); reloadMemories() }
    fun deleteMemory(id: Long) { memRepo.delete(id); reloadMemories() }
    fun clearMemories() { memRepo.clear(); reloadMemories() }

    // ----- tests -----
    var testResult by mutableStateOf(""); private set
    var testing by mutableStateOf(false); private set
    var voiceTestMsg by mutableStateOf(""); private set

    fun runTest() {
        val g = store.geminiKey
        val q = store.groqKey
        val sv = store.sarvamKey
        if (g.isBlank() && q.isBlank() && sv.isBlank()) { testResult = "पहले कोई key डालकर Save करें।"; return }
        testing = true
        testResult = ""
        viewModelScope.launch {
            val sb = StringBuilder()
            suspend fun step(label: String, block: suspend () -> String) {
                val t0 = System.currentTimeMillis()
                val r = try { block() } catch (e: Exception) { "✖ ${e.message}" }
                val dt = "%.1f".format((System.currentTimeMillis() - t0) / 1000.0)
                sb.append(label).append(": ").append(r).append(" (").append(dt).append("s)\n")
                testResult = sb.toString().trim()
            }
            val hello = listOf(ChatMessage(1, "user", "नमस्ते बोलो", 0))
            val sample = "नमस्ते, मैं ${store.assistantName} हूँ।"

            if (q.isNotBlank()) {
                step("Groq key") { groq.diagnose(q).replace('\n', ' ') }
                step("Groq जवाब") {
                    groq.generate(q, hello, true, "Reply in one short Hindi sentence.", "", null)
                        .fold({ "✔ " + it.take(40) }, { "✖ " + (it.message ?: "").replace('\n', ' ') })
                }
            }
            if (g.isNotBlank()) {
                step("Gemini key") { gemini.diagnose(g).replace('\n', ' ') }
                step("Gemini जवाब") {
                    gemini.generate(g, store.model, hello, true, "", false, "", null)
                        .fold({ "✔ " + it.take(40) }, { "✖ " + (it.message ?: "").replace('\n', ' ') })
                }
            }
            step("Google News खोज") {
                val n = WebSearch.news("ताज़ा खबर").size
                if (n > 0) "✔ $n नतीजे" else "✖ कोई नतीजा नहीं"
            }
            if (sv.isNotBlank()) {
                step("Sarvam आवाज़") {
                    val a = withContext(Dispatchers.IO) { sarvam.fetchOne(sv, sample, store.sarvamVoice, "hi-IN") }
                    if (a != null) "✔ आवाज़ बनी" else "✖ नहीं बनी (key / credits / voice नाम?)"
                }
            }
            if (g.isNotBlank()) {
                step("Gemini आवाज़") {
                    val a = withContext(Dispatchers.IO) { speaker.fetchOne(g, sample, store.ttsVoice) }
                    if (a != null) "✔ आवाज़ बनी" else "✖ नहीं बनी (limit / model?)"
                }
            }
            testing = false
        }
    }

    fun testVoice() {
        val engine = store.ttsEngine
        if (engine == "android") {
            voiceTestMsg = "फ़ोन की आवाज़ जवाब बोलते समय सुनाई देगी।"
            return
        }
        voiceTestMsg = "आवाज़ बन रही है…"
        viewModelScope.launch {
            val text = "नमस्ते! मैं ${store.assistantName} हूँ। कहिए, मैं आपकी क्या मदद करूँ?"
            val chunks = speaker.chunksOf(text)
            var played = 0
            val order = if (engine == "sarvam") listOf("sarvam", "gemini") else listOf("gemini", "sarvam")
            for (e in order) {
                if (played > 0) break
                if (e == "sarvam" && store.sarvamKey.isNotBlank()) {
                    played = sarvam.speak(store.sarvamKey, chunks, store.sarvamVoice, "hi-IN") {
                        voiceTestMsg = "बोल रही हूँ…"
                    }
                }
                if (e == "gemini" && store.geminiKey.isNotBlank()) {
                    played = speaker.speak(store.geminiKey, chunks, store.ttsVoice) {
                        voiceTestMsg = "बोल रही हूँ…"
                    }
                }
            }
            voiceTestMsg = if (played > 0) "" else "आवाज़ नहीं मिली: key या limit जाँचें (Tools → API Key)।"
        }
    }
}
