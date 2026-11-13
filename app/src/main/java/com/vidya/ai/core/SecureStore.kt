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

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(v) = prefs.edit().putString("user_name", v).apply()

    var assistantName: String
        get() = (prefs.getString("assistant_name", DEFAULT_ASSISTANT) ?: DEFAULT_ASSISTANT).ifBlank { DEFAULT_ASSISTANT }
        set(v) = prefs.edit().putString("assistant_name", v).apply()

    var memoryEnabled: Boolean
        get() = prefs.getBoolean("memory_enabled", true)
        set(v) = prefs.edit().putBoolean("memory_enabled", v).apply()

    var searchEnabled: Boolean
        get() = prefs.getBoolean("search_enabled", true)
        set(v) = prefs.edit().putBoolean("search_enabled", v).apply()

    var memoryJson: String
        get() = prefs.getString("memory", "[]") ?: "[]"
        set(v) = prefs.edit().putString("memory", v).apply()

    var groqKey: String
        get() = prefs.getString("groq_key", "") ?: ""
        set(v) = prefs.edit().putString("groq_key", v).apply()

    var sarvamKey: String
        get() = prefs.getString("sarvam_key", "") ?: ""
        set(v) = prefs.edit().putString("sarvam_key", v).apply()

    /** Which AI answers first: "groq" or "gemini" (the other one is the automatic backup). */
    var primaryAi: String
        get() = prefs.getString("primary_ai", "groq") ?: "groq"
        set(v) = prefs.edit().putString("primary_ai", v).apply()

    var sarvamVoice: String
        get() = prefs.getString("sarvam_voice", "anushka") ?: "anushka"
        set(v) = prefs.edit().putString("sarvam_voice", v).apply()

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
        get() = prefs.getString("tts_engine", "sarvam") ?: "sarvam"
        set(v) = prefs.edit().putString("tts_engine", v).apply()

    var ttsVoice: String
        get() = prefs.getString("tts_voice", "Kore") ?: "Kore"
        set(v) = prefs.edit().putString("tts_voice", v).apply()

    var useDialog: Boolean
        get() = voiceEngine == "window"
        set(v) { voiceEngine = if (v) "window" else "google" }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val DEFAULT_ASSISTANT = "विद्या"

        fun sanitizeModel(s: String): String {
            val c = s.trim().removePrefix("models/").lowercase()
                .replace(Regex("\\s+"), "-")
                .filter { it.isLetterOrDigit() || it == '-' || it == '.' || it == '_' }
            return c.ifBlank { DEFAULT_MODEL }
        }
    }
}
