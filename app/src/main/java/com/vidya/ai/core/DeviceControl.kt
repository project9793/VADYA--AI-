package com.vidya.ai.core

import org.json.JSONObject

object DeviceControl {
    private val hint = Regex(
        "call|कॉल|फोन\\s*(करो|कर दो|लगा|मिला)|फ़ोन\\s*(करो|कर दो|लगा|मिला)|dial|whatsapp|व्हाट्स|वाट्स|वॉट्स|" +
            "youtube|यूट्यूब|खोलो|खोल दो|ओपन|\\bopen\\b|चलाओ|चला दो|बजाओ|बजा दो|\\bplay\\b|सुनाओ|" +
            "(काट|कट)\\s*(दो|करो)|hang ?up|disconnect|मैसेज\\s*(भेज|कर)|message\\s*(to|भेज)|\\bmsg\\b",
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

    /** Commands are short orders, not questions. Normal chat skips the extra request completely. */
    fun looksLikeCommand(msg: String): Boolean =
        msg.length <= 120 && !msg.contains('?') && !msg.contains('？') && hint.containsMatchIn(msg)
}
