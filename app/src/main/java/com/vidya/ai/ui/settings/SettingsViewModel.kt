package com.vidya.ai.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.vidya.ai.core.SecureStore

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    val savedGemini: String get() = store.geminiKey
    val savedSearch: String get() = store.searchKey
    val savedModel: String get() = store.model
    var sttLang: String
        get() = store.sttLang
        set(v) { store.sttLang = v }
    fun saveGemini(key: String) { store.geminiKey = key.trim() }
    fun saveSearch(key: String) { store.searchKey = key.trim() }
    fun saveModel(m: String) { store.model = m }
}
