package com.vidya.ai.core

object Keys {
    /** Several keys can be pasted separated by comma / space / new line; the next one is used on a limit. */
    fun list(raw: String): List<String> =
        raw.split(',', '\n', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
}
