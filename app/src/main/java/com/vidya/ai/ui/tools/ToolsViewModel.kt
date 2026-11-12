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
import com.vidya.ai.core.SecureStore
import com.vidya.ai.data.MemoryItem
import com.vidya.ai.data.MemoryRepository
import kotlinx.coroutines.launch

/** Holds everything the Tools screens show and change (activity-scoped, shared by all screens). */
class ToolsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    private val gemini = GeminiClient()
    private val speaker = GeminiSpeaker()
    private val memRepo = MemoryRepository(store)

    var userName by mutableStateOf(store.userName); private set
    var assistantName by mutableStateOf(store.assistantName); private set
    var ttsEngine by mutableStateOf(store.ttsEngine); private set
    var ttsVoice by mutableStateOf(store.ttsVoice); private set
    var sttLang by mutableStateOf(store.sttLang); private set
    var voiceEngine by mutableStateOf(store.voiceEngine); private set
    var searchEnabled by mutableStateOf(store.searchEnabled); private set
    var memoryEnabled by mutableStateOf(store.memoryEnabled); private set
    var hasGeminiKey by mutableStateOf(store.geminiKey.isNotBlank()); private set
    val memories = mutableStateListOf<MemoryItem>().also { it.addAll(memRepo.all()) }

    val savedGemini: String get() = store.geminiKey
    val savedSearch: String get() = store.searchKey
    val savedModel: String get() = store.model

    fun saveUserName(v: String) { store.userName = v.trim(); userName = store.userName }
    fun saveAssistantName(v: String) {
        store.assistantName = v.trim().ifBlank { SecureStore.DEFAULT_ASSISTANT }
        assistantName = store.assistantName
    }
    fun setTtsEngine(v: String) { store.ttsEngine = v; ttsEngine = v }
    fun setTtsVoice(v: String) { store.ttsVoice = v; ttsVoice = v }
    fun setLang(v: String) { store.sttLang = v; sttLang = v }
    fun setVoiceEngine(v: String) { store.voiceEngine = v; voiceEngine = v }
    fun setSearchEnabled(v: Boolean) { store.searchEnabled = v; searchEnabled = v }
    fun setMemoryEnabled(v: Boolean) { store.memoryEnabled = v; memoryEnabled = v }

    fun saveGemini(key: String) { store.geminiKey = key.trim(); hasGeminiKey = store.geminiKey.isNotBlank() }
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
        if (store.geminiKey.isBlank()) { testResult = "पहले Gemini API key डालकर Save करें।"; return }
        testing = true
        testResult = ""
        viewModelScope.launch {
            testResult = gemini.diagnose(store.geminiKey)
            testing = false
        }
    }

    fun testVoice() {
        val key = store.geminiKey
        if (key.isBlank()) { voiceTestMsg = "पहले Tools → API Key में Gemini key डालें।"; return }
        voiceTestMsg = "आवाज़ बन रही है…"
        viewModelScope.launch {
            val ok = speaker.speak(
                key,
                "नमस्ते! मैं ${store.assistantName} हूँ। कहिए, मैं आपकी क्या मदद करूँ?",
                store.ttsVoice
            ) { voiceTestMsg = "बोल रही हूँ…" }
            voiceTestMsg = if (ok) "" else "Gemini आवाज़ नहीं मिली (limit या model की दिक्कत)। थोड़ी देर बाद कोशिश करें।"
        }
    }
}
