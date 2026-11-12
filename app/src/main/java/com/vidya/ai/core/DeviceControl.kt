package com.vidya.ai.core

object DeviceControl {
    private val hint = Regex(
        "call|कॉल|फोन|फ़ोन|dial|whatsapp|व्हाट्स|वाट्स|वॉट्स|youtube|यूट्यूब|खोल|open|चलाओ|चला दो|" +
            "बजाओ|बजा दो|play|गाना|गाने|song|music|म्यूजिक|संगीत|video|वीडियो|सुनाओ|काट|कट|disconnect|" +
            "hang ?up|मैसेज|message|msg|भेज",
        RegexOption.IGNORE_CASE
    )

    /** Cheap check so normal chat does not pay for an extra request. */
    fun looksLikeCommand(msg: String): Boolean = msg.length <= 160 && hint.containsMatchIn(msg)
}
