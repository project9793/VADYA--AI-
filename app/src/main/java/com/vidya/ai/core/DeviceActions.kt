package com.vidya.ai.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

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

    // ---------------------------------------------------------------- WhatsApp

    private fun waNumber(raw: String): String {
        val d = raw.filter { it.isDigit() }
        return when {
            d.length == 10 -> "91$d"
            d.length == 11 && d.startsWith("0") -> "91" + d.drop(1)
            else -> d
        }
    }

    /** Opens the chat with the text already typed. The user presses Send inside WhatsApp. */
    fun whatsapp(number: String?, message: String): String {
        val text = Uri.encode(message)
        val url = if (!number.isNullOrBlank()) "https://wa.me/${waNumber(number)}?text=$text"
        else "https://wa.me/?text=$text"
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        return try {
            i.setPackage("com.whatsapp")
            start(i)
            "💬 WhatsApp खुल गया। संदेश तैयार है, Send आप दबाइए।"
        } catch (e: ActivityNotFoundException) {
            try {
                i.setPackage(null)
                start(i)
                "💬 संदेश तैयार है, Send आप दबाइए।"
            } catch (e2: Exception) {
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
            "▶ YouTube में \"$query\" के नतीजे खुल गए। वीडियो आप tap कीजिए।"
        } catch (e: Exception) {
            try {
                start(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")))
                "▶ \"$query\" के नतीजे खुल गए। वीडियो आप tap कीजिए।"
            } catch (e2: Exception) {
                "YouTube नहीं खुल सका: ${e2.message}"
            }
        }
    }

    fun playMusic(query: String): String {
        fun intent(pkg: String?) = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, query)
            .apply { if (pkg != null) setPackage(pkg) }
        for (pkg in listOf("com.google.android.apps.youtube.music", "com.spotify.music", null)) {
            try {
                start(intent(pkg))
                return "🎵 \"$query\" चला रही हूँ।"
            } catch (e: Exception) {
                // try the next music app
            }
        }
        return youtubeSearch(query)
    }
}
