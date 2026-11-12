package com.vidya.ai.core

import java.util.Locale

object LangUtil {
    /** Picks a TTS locale from the script of [text]; Latin text uses the speech language if sensible. */
    fun detect(text: String, sttLang: String): Locale {
        for (ch in text) {
            if (!ch.isLetter()) continue
            val tag = when (Character.UnicodeBlock.of(ch)) {
                Character.UnicodeBlock.DEVANAGARI -> if (sttLang.startsWith("ne") || sttLang.startsWith("mr")) sttLang else "hi-IN"
                Character.UnicodeBlock.BENGALI -> "bn-IN"
                Character.UnicodeBlock.TAMIL -> "ta-IN"
                Character.UnicodeBlock.TELUGU -> "te-IN"
                Character.UnicodeBlock.GUJARATI -> "gu-IN"
                Character.UnicodeBlock.MALAYALAM -> "ml-IN"
                Character.UnicodeBlock.KANNADA -> "kn-IN"
                Character.UnicodeBlock.ORIYA -> "or-IN"
                Character.UnicodeBlock.GURMUKHI -> "pa-IN"
                Character.UnicodeBlock.ARABIC -> if (sttLang.startsWith("ur")) "ur-IN" else "ar-SA"
                Character.UnicodeBlock.HIRAGANA, Character.UnicodeBlock.KATAKANA -> "ja-JP"
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS -> "zh-CN"
                else -> null
            }
            if (tag != null) return Locale.forLanguageTag(tag)
        }
        val l = sttLang.substringBefore('-')
        return if (l in setOf("en", "es", "fr", "de")) Locale.forLanguageTag(sttLang)
        else Locale.forLanguageTag("en-IN")
    }

    fun stripMarkdown(s: String): String = s
        .substringBefore("\n\nस्रोत:")
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("(?m)^\\s*[-•]\\s+"), "")
        .replace(Regex("[*#`]"), "")
        .trim()
}
