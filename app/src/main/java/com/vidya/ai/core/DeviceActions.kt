package com.vidya.ai.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.vidya.ai.service.VidyaAccessibilityService

/** Phone actions done only through Android's official, permission-based routes. */
class DeviceActions(private val ctx: Context) {

    class Contact(val name: String, val number: String)

    private val packages = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "youtube_music" to "com.google.android.apps.youtube.music",
        "chrome" to "com.android.chrome",
        "maps" to "com.google.android.apps.maps",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "telegram" to "org.telegram.messenger",
        "spotify" to "com.spotify.music",
        "gmail" to "com.google.android.gm",
        "photos" to "com.google.android.apps.photos"
    )

    private fun has(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun start(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    // ---------------------------------------------------------------- contacts

    fun findContact(names: List<String>): List<Contact> {
        if (!has(Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = mutableListOf<Contact>()
        for (n in names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$n%"), null
            )?.use { c ->
                while (c.moveToNext() && out.size < 8) {
                    out += Contact(c.getString(0).orEmpty(), c.getString(1).orEmpty())
                }
            }
        }
        return out.distinctBy { it.number.filter { ch -> ch.isDigit() }.takeLast(10) }
    }

    // ---------------------------------------------------------------- calls

    fun call(number: String): String {
        val canCall = has(Manifest.permission.CALL_PHONE)
        val i = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
        return try {
            start(i)
            if (canCall) "📞 $number पर call लगा दिया।"
            else "📞 Dialer खुल गया। सीधे call लगाने के लिए Tools → Permissions में Phone calls allow करें।"
        } catch (e: Exception) {
            "Call नहीं लग सकी: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun endCall(): String {
        if (Build.VERSION.SDK_INT < 28) return "इस Android version पर call काटना संभव नहीं है।"
        if (!has(Manifest.permission.ANSWER_PHONE_CALLS)) {
            return "Call काटने के लिए Tools → Permissions में Phone calls allow करें।"
        }
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (tm.endCall()) "✔ Call काट दी।" else "कोई चालू call नहीं मिली।"
        } catch (e: Exception) {
            "Call नहीं काट सकी: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun answerCall(): String {
        if (!has(Manifest.permission.ANSWER_PHONE_CALLS)) {
            return "Call उठाने के लिए Tools → Permissions में Phone calls allow करें।"
        }
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.acceptRingingCall()
            "✔ Call उठा ली।"
        } catch (e: Exception) {
            "Call नहीं उठा सकी: ${e.message}"
        }
    }

    // ---------------------------------------------------------------- WhatsApp

    private fun waNumber(raw: String): String {
        val d = raw.filter { it.isDigit() }
        return when {
            d.length == 10 -> "91$d"
            d.length == 11 && d.startsWith("0") -> "91" + d.drop(1)
            else -> d
        }
    }

    /**
     * Opens the chat with the text already typed. With [autoSend] (Accessibility service on) the
     * Send button is pressed for the user; otherwise the user presses Send.
     */
    fun whatsapp(number: String?, message: String, autoSend: Boolean = false): String {
        val text = Uri.encode(message)
        val url = if (!number.isNullOrBlank()) "https://wa.me/${waNumber(number)}?text=$text"
        else "https://wa.me/?text=$text"
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val ok = if (autoSend) "💬 WhatsApp खुल गया, संदेश भेज रही हूँ।" else "💬 WhatsApp खुल गया। संदेश तैयार है, Send आप दबाइए।"
        if (autoSend) VidyaAccessibilityService.requestSend()
        return try {
            i.setPackage("com.whatsapp")
            start(i)
            ok
        } catch (e: ActivityNotFoundException) {
            try {
                i.setPackage(null)
                start(i)
                ok
            } catch (e2: Exception) {
                VidyaAccessibilityService.cancelSend()
                "WhatsApp नहीं खुल सका: ${e2.message}"
            }
        }
    }

    // ---------------------------------------------------------------- apps, YouTube, music

    fun openApp(key: String): String {
        val k = key.lowercase().trim()
        val intent: Intent? = when (k) {
            "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            "settings" -> Intent(Settings.ACTION_SETTINGS)
            "dialer" -> Intent(Intent.ACTION_DIAL)
            "whatsapp" -> ctx.packageManager.getLaunchIntentForPackage("com.whatsapp")
                ?: ctx.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
            else -> packages[k]?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
        }
        if (intent == null) return "यह app इस फ़ोन में नहीं मिला: $key"
        return try {
            start(intent)
            "✔ $k खोल दिया।"
        } catch (e: Exception) {
            "App खुल नहीं सका: ${e.message}"
        }
    }

    fun youtubeSearch(query: String): String {
        val i = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", query)
        return try {
            start(i)
            "▶ YouTube में \"$query\" के नतीजे खुल गए।"
        } catch (e: Exception) {
            try {
                start(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")))
                "▶ \"$query\" के नतीजे खुल गए।"
            } catch (e2: Exception) {
                "YouTube नहीं खुल सका: ${e2.message}"
            }
        }
    }

    /** Finds the top YouTube video (Data API) and opens it so it starts playing. Blocking: call off the main thread. */
    fun youtubePlay(query: String, apiKeys: String): String {
        if (apiKeys.isBlank()) {
            return youtubeSearch(query) + "\\nवीडियो अपने आप चलाने के लिए Tools → 8. Phone control में YouTube API key डालिए।"
        }
        val hit = YouTubeApi.firstVideo(apiKeys, query)
            ?: return youtubeSearch(query) + "\\nYouTube key / limit में दिक्कत है, इसलिए खोज के नतीजे खोले।"
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${hit.first}"))
        return try {
            i.setPackage("com.google.android.youtube")
            start(i)
            "▶ चला रही हूँ: ${hit.second}"
        } catch (e: Exception) {
            try {
                i.setPackage(null)
                start(i)
                "▶ चला रही हूँ: ${hit.second}"
            } catch (e2: Exception) {
                "वीडियो नहीं खुल सका: ${e2.message}"
            }
        }
    }

    private fun playFromSearch(query: String, pkg: String?): Boolean {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, query)
        if (pkg != null) i.setPackage(pkg)
        return try { start(i); true } catch (e: Exception) { false }
    }

    /** [app]: spotify | youtube_music | music_player | youtube | "" (then [defaultApp] is used). */
    fun playMusic(query: String, app: String, defaultApp: String, youtubeKey: String): String {
        val wanted = app.lowercase().ifBlank { defaultApp.lowercase() }
        if (wanted == "youtube") return youtubePlay(query, youtubeKey)
        val order = when (wanted) {
            "spotify" -> listOf("spotify")
            "youtube_music" -> listOf("youtube_music")
            "music_player", "player" -> listOf("player")
            else -> listOf("youtube_music", "spotify", "player")
        }
        for (target in order) {
            when (target) {
                "spotify" -> {
                    if (playFromSearch(query, "com.spotify.music")) return "🎵 Spotify में \"$query\" चला रही हूँ।"
                    try {
                        start(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(query))))
                        return "🎵 Spotify में \"$query\" की खोज खुल गई। गाना आप tap कीजिए।"
                    } catch (e: Exception) { /* not installed */ }
                }
                "youtube_music" -> {
                    if (playFromSearch(query, "com.google.android.apps.youtube.music")) {
                        return "🎵 YouTube Music में \"$query\" चला रही हूँ।"
                    }
                }
                else -> {
                    if (playFromSearch(query, null)) return "🎵 Music player में \"$query\" चला रही हूँ।"
                    try {
                        start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
                        return "🎵 Music player खुल गया। \"$query\" आप चुनिए।"
                    } catch (e: Exception) { /* none */ }
                }
            }
        }
        return youtubePlay(query, youtubeKey)
    }

    /** pause | resume | next | previous: works with any music / video app that is playing. */
    fun mediaControl(cmd: String): String {
        val (code, label) = when (cmd.lowercase().trim()) {
            "pause", "stop" -> KeyEvent.KEYCODE_MEDIA_PAUSE to "रोक दिया"
            "resume", "play" -> KeyEvent.KEYCODE_MEDIA_PLAY to "फिर चालू कर दिया"
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT to "अगला चला दिया"
            "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS to "पिछला चला दिया"
            else -> return "यह आदेश समझ नहीं आया: $cmd"
        }
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            "✔ $label।"
        } catch (e: Exception) {
            "Media control नहीं चला: ${e.message}"
        }
    }
}
