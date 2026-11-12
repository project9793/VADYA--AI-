package com.vidya.ai.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidya.ai.core.GeminiClient
import com.vidya.ai.core.GeminiSpeaker
import com.vidya.ai.core.SecureStore
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    private val gemini = GeminiClient()
    private val speaker = GeminiSpeaker()

    var ttsEngine: String
        get() = store.ttsEngine
        set(v) { store.ttsEngine = v }
    var ttsVoice: String
        get() = store.ttsVoice
        set(v) { store.ttsVoice = v }

    var voiceTestMsg by mutableStateOf("")
        private set

    fun testVoice() {
        val key = store.geminiKey
        if (key.isBlank()) { voiceTestMsg = "पहले Gemini API key डालकर Save करें।"; return }
        voiceTestMsg = "आवाज़ बन रही है…"
        viewModelScope.launch {
            val ok = speaker.speak(key, "नमस्ते! मैं विद्या हूँ। कहिए, मैं आपकी क्या मदद करूँ?", store.ttsVoice) {
                voiceTestMsg = "बोल रही हूँ…"
            }
            voiceTestMsg = if (ok) "" else "Gemini आवाज़ नहीं मिली (limit या model की दिक्कत)। थोड़ी देर बाद कोशिश करें।"
        }
    }

    val savedGemini: String get() = store.geminiKey
    val savedSearch: String get() = store.searchKey
    val savedModel: String get() = store.model
    var sttLang: String
        get() = store.sttLang
        set(v) { store.sttLang = v }

    var voiceEngine: String
        get() = store.voiceEngine
        set(v) { store.voiceEngine = v }

    var useDialog: Boolean
        get() = store.useDialog
        set(v) { store.useDialog = v }

    fun saveGemini(key: String) { store.geminiKey = key.trim() }
    fun saveSearch(key: String) { store.searchKey = key.trim() }
    fun saveModel(m: String) { store.model = m }

    var testResult by mutableStateOf("")
        private set
    var testing by mutableStateOf(false)
        private set

    fun runTest() {
        if (store.geminiKey.isBlank()) {
            testResult = "पहले Gemini API key डालकर Save करें।"
            return
        }
        testing = true
        testResult = ""
        viewModelScope.launch {
            testResult = gemini.diagnose(store.geminiKey)
            testing = false
        }
    }
}
