package com.vidya.ai.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** API keys and settings, stored encrypted on-device (AES-256). */
class SecureStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "vidya_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var geminiKey: String
        get() = prefs.getString("gemini_key", "") ?: ""
        set(v) = prefs.edit().putString("gemini_key", v).apply()

    var searchKey: String
        get() = prefs.getString("search_key", "") ?: ""
        set(v) = prefs.edit().putString("search_key", v).apply()

    var model: String
        get() = (prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL).ifBlank { DEFAULT_MODEL }
        set(v) = prefs.edit().putString("model", v.trim()).apply()

    /** BCP-47 tag used for speech recognition, e.g. hi-IN */
    var sttLang: String
        get() = prefs.getString("stt_lang", "hi-IN") ?: "hi-IN"
        set(v) = prefs.edit().putString("stt_lang", v).apply()

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
