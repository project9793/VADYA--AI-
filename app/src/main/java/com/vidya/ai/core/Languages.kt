package com.vidya.ai.core

object Languages {
    val all = listOf(
        "हिन्दी" to "hi-IN",
        "English" to "en-IN",
        "മലയാളം (Malayalam)" to "ml-IN",
        "ಕನ್ನಡ (Kannada)" to "kn-IN",
        "தமிழ் (Tamil)" to "ta-IN",
        "తెలుగు (Telugu)" to "te-IN",
        "मराठी (Marathi)" to "mr-IN",
        "বাংলা (Bengali)" to "bn-IN",
        "ગુજરાતી (Gujarati)" to "gu-IN",
        "ਪੰਜਾਬੀ (Punjabi)" to "pa-IN",
        "ଓଡ଼ିଆ (Odia)" to "or-IN",
        "অসমীয়া (Assamese)" to "as-IN",
        "اردو (Urdu)" to "ur-IN",
        "नेपाली (Nepali)" to "ne-NP",
        "भोजपुरी (Bhojpuri)" to "bho-IN",
        "संस्कृतम् (Sanskrit)" to "sa-IN",
        "Español" to "es-ES",
        "Français" to "fr-FR",
        "Deutsch" to "de-DE",
        "العربية (Arabic)" to "ar-SA",
        "日本語 (Japanese)" to "ja-JP",
        "中文 (Chinese)" to "zh-CN"
    )

    fun label(tag: String): String = all.firstOrNull { it.second == tag }?.first ?: tag
}
