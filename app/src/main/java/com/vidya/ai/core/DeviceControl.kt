package com.vidya.ai.core

import org.json.JSONObject

object DeviceControl {
    private val hint = Regex(
        "call|कॉल|फोन|फ़ोन|dial|whatsapp|व्हाट्स|वाट्स|वॉट्स|youtube|यूट्यूब|खोल|open|चलाओ|चला दो|" +
            "बजाओ|बजा दो|play|गाना|गाने|song|music|म्यूजिक|संगीत|video|वीडियो|सुनाओ|काट|कट|disconnect|" +
            "hang ?up|मैसेज|message|msg|भेज",
        RegexOption.IGNORE_CASE
    )

    fun prompt(text: String): String =
        "You convert a spoken phone-control request (Hindi, Hinglish, English or any Indian language) into JSON. " +
            "Return ONLY JSON with the keys: action, name, name_en, number, message, app, query. " +
            "action must be one of: call, end_call, whatsapp, open_app, youtube_search, play_music, none. " +
            "call = place a phone call (name = contact name as spoken, name_en = the same name in Roman letters, " +
            "number = digits if the user said a number). end_call = hang up / cut the current call. " +
            "whatsapp = send a WhatsApp message (name, name_en, number as above; message = the text to send, or empty). " +
            "open_app = open an app (app is one of: whatsapp, youtube, youtube_music, chrome, maps, instagram, " +
            "facebook, telegram, spotify, gmail, camera, settings, photos, dialer, other). " +
            "youtube_search = search or show videos on YouTube (query = search text). " +
            "play_music = play a song, music or video by name or artist (query = what to play). " +
            "none = anything else such as questions, chat or information requests. Unused fields are empty strings. " +
            "User request: " + JSONObject.quote(text)

    /** Cheap check so normal chat does not pay for an extra request. */
    fun looksLikeCommand(msg: String): Boolean = msg.length <= 160 && hint.containsMatchIn(msg)
}
