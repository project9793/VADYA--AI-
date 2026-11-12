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
        get() = sanitizeModel(prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL)
        set(v) = prefs.edit().putString("model", sanitizeModel(v)).apply()

    /** BCP-47 tag used for speech recognition, e.g. hi-IN */
    var sttLang: String
        get() = prefs.getString("stt_lang", "hi-IN") ?: "hi-IN"
        set(v) = prefs.edit().putString("stt_lang", v).apply()

    /** "gemini" (record + Gemini transcribes), "google" (in-app recognizer), "window" (Google voice window) */
    var voiceEngine: String
        get() = prefs.getString("voice_engine", "gemini") ?: "gemini"
        set(v) = prefs.edit().putString("voice_engine", v).apply()

    /** "gemini" = natural Gemini voice, "android" = phone's own text-to-speech */
    var ttsEngine: String
        get() = prefs.getString("tts_engine", "gemini") ?: "gemini"
        set(v) = prefs.edit().putString("tts_engine", v).apply()

    var ttsVoice: String
        get() = prefs.getString("tts_voice", "Kore") ?: "Kore"
        set(v) = prefs.edit().putString("tts_voice", v).apply()

    var useDialog: Boolean
        get() = voiceEngine == "window"
        set(v) { voiceEngine = if (v) "window" else "google" }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"

        fun sanitizeModel(s: String): String {
            val c = s.trim().removePrefix("models/").lowercase()
                .replace(Regex("\\s+"), "-")
                .filter { it.isLetterOrDigit() || it == '-' || it == '.' || it == '_' }
            return c.ifBlank { DEFAULT_MODEL }
        }
    }
}
