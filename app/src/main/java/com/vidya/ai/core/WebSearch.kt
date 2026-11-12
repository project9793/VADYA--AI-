package com.vidya.ai.core

import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Fetches current headlines so answers about live events come from real, recent sources. */
object WebSearch {

    class Hit(val title: String, val source: String, val date: String)

    private val keywords = listOf(
        "news", "खबर", "ख़बर", "समाचार", "samachar", "khabar", "latest", "ताज़ा", "ताजा", "taaza",
        "चुनाव", "election", "जीत गए", "हार गए", "jeet", "won ", "lost ", "result", "नतीजा", "नतीजे",
        "score", "स्कोर", "match", "मैच", "ipl", "price", "भाव", "कीमत", "rate", "stock", "शेयर",
        "sensex", "nifty", "weather", "मौसम", "president", "राष्ट्रपति", "प्रधानमंत्री", "prime minister",
        "मुख्यमंत्री", "chief minister", "ceo", "released", "launch", "लॉन्च", "trending", "viral",
        "budget", "बजट", "war ", "युद्ध", "2025", "2026", "आज की", "today's", "current", "वर्तमान",
        "कौन है", "who is", "kaun hai", "internet", "इंटरनेट", "search", "खोज", "google", "गूगल"
    )
    private val rx = Regex(keywords.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    private val filler = Regex(
        "इंटरनेट से|खोजकर|खोजो|बताओ|बताइए|search|internet|please|कृपया|जरा|ज़रा",
        RegexOption.IGNORE_CASE
    )

    fun needsFresh(msg: String): Boolean = rx.containsMatchIn(msg)

    suspend fun news(query: String): List<Hit> = withContext(Dispatchers.IO) {
        try {
            val cleaned = query.replace(filler, " ").replace(Regex("[:?।!]"), " ")
                .replace(Regex("\\s+"), " ").trim().take(100)
            if (cleaned.isBlank()) return@withContext emptyList<Hit>()
            val hindi = query.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.DEVANAGARI }
            val loc = if (hindi) "hl=hi&gl=IN&ceid=IN:hi" else "hl=en-IN&gl=IN&ceid=IN:en"
            val url = URL("https://news.google.com/rss/search?q=${URLEncoder.encode(cleaned, "UTF-8")}&$loc")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 4_000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext emptyList<Hit>()
            }
            val hits = mutableListOf<Hit>()
            conn.inputStream.use { stream ->
                val p = Xml.newPullParser()
                p.setInput(stream, "UTF-8")
                var ev = p.eventType
                var inItem = false
                var title = ""
                var source = ""
                var date = ""
                while (ev != XmlPullParser.END_DOCUMENT && hits.size < 8) {
                    if (ev == XmlPullParser.START_TAG) {
                        when (p.name) {
                            "item" -> { inItem = true; title = ""; source = ""; date = "" }
                            "title" -> if (inItem) title = p.nextText()
                            "source" -> if (inItem) source = p.nextText()
                            "pubDate" -> if (inItem) date = p.nextText()
                        }
                    } else if (ev == XmlPullParser.END_TAG && p.name == "item") {
                        inItem = false
                        val clean = if (source.isNotBlank() && title.endsWith(" - $source"))
                            title.removeSuffix(" - $source") else title
                        if (clean.isNotBlank()) hits += Hit(clean, source.ifBlank { "Google News" }, date.take(16))
                    }
                    ev = p.next()
                }
            }
            conn.disconnect()
            hits
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList<Hit>()
        }
    }

    fun asContext(hits: List<Hit>): String =
        if (hits.isEmpty()) "" else buildString {
            append(" FRESH WEB RESULTS just fetched from Google News (newer than your training data; ")
            append("use them as the primary source for current facts and mention the source names): ")
            hits.forEachIndexed { i, h -> append("[${i + 1}] ${h.title} (${h.source}, ${h.date}) ") }
        }

    fun sourcesFooter(hits: List<Hit>): String {
        val names = hits.map { it.source }.filter { it.isNotBlank() }.distinct().take(4)
        return if (names.isEmpty()) "" else "\n\nस्रोत:\n" + names.joinToString("\n") { "• $it" }
    }
}
