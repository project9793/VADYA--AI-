package com.vidya.ai.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Date / time are answered from the phone's clock: instant and always correct. */
object LocalAnswers {
    private val days = listOf("रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार")
    private val months = listOf(
        "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून",
        "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"
    )

    private val dateRx = Regex(
        "(आज.*(तारीख|तारिख|दिनांक|डेट|तिथि|date|tareekh|tarikh))|" +
            "((तारीख|तारिख|दिनांक|डेट|date).*(आज|today|क्या|कौन|kya|kaun|what))|" +
            "(what('?s| is)? (the )?(today'?s )?date)|(today'?s date)|(aaj.*(tareekh|tarikh|date))|" +
            "(कौन\\s*सी\\s*(तारीख|तारिख|डेट))|(आज\\s*क्या\\s*(वार|दिन))|(which day)|(what day)|(कौन\\s*सा\\s*दिन)",
        RegexOption.IGNORE_CASE
    )
    private val timeRx = Regex(
        "(कितने\\s*बजे)|(क्या\\s*(समय|टाइम))|(अभी.*(समय|टाइम|बजे))|(what('?s| is)? (the )?(current )?time)|" +
            "(time\\s*kya)|(kitne\\s*baje)|(abhi.*(time|baje))|(टाइम\\s*क्या)|(समय\\s*क्या)",
        RegexOption.IGNORE_CASE
    )
    private val hindiHint = Regex("kya|kaun|aaj|tareekh|tarikh|kitne|baje|abhi|batao|bata", RegexOption.IGNORE_CASE)

    fun tryAnswer(msg: String): String? {
        val m = msg.trim()
        if (m.length > 60) return null
        val wantDate = dateRx.containsMatchIn(m)
        val wantTime = timeRx.containsMatchIn(m)
        if (!wantDate && !wantTime) return null

        val hindi = m.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.DEVANAGARI } ||
            hindiHint.containsMatchIn(m)
        val cal = Calendar.getInstance()
        val parts = mutableListOf<String>()

        if (wantDate) {
            parts += if (hindi) {
                "आज ${days[cal.get(Calendar.DAY_OF_WEEK) - 1]}, ${cal.get(Calendar.DAY_OF_MONTH)} " +
                    "${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)} है।"
            } else {
                "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(Date()) + "."
            }
        }
        if (wantTime) {
            parts += if (hindi) {
                val h24 = cal.get(Calendar.HOUR_OF_DAY)
                val h12 = if (h24 % 12 == 0) 12 else h24 % 12
                val period = when (h24) {
                    in 4..11 -> "सुबह"
                    in 12..15 -> "दोपहर"
                    in 16..19 -> "शाम"
                    else -> "रात"
                }
                "अभी $period के $h12:${"%02d".format(cal.get(Calendar.MINUTE))} बजे हैं।"
            } else {
                "It's " + SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date()) + " right now."
            }
        }
        return parts.joinToString(" ")
    }
}
