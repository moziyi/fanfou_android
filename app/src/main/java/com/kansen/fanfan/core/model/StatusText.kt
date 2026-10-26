package com.kansen.fanfan.core.model

/** Fanfou limits a status to 140 characters counted as Unicode code points. */
object StatusText {

    const val MAX_LENGTH = 140

    fun length(text: String): Int = text.codePointCount(0, text.length)

    fun remaining(text: String): Int = MAX_LENGTH - length(text)

    fun fits(text: String): Boolean = remaining(text) >= 0

    /**
     * The text a repost starts from: `转@<author> <original>`, cut by Unicode
     * code points so it fits exactly like the official client's own repost
     * chains do (no ellipsis — a chain that breaks mid-sentence in the wild
     * always stops at the hard 140 boundary). Consumers type their own comment
     * in front of the `转`; an author without a screen name gives nothing.
     */
    fun repostPrefill(screenName: String?, originalText: String): String? {
        val name = screenName?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val prefix = "转@$name"
        val original = originalText.trim()
        if (original.isEmpty()) return prefix
        val full = "$prefix $original"
        if (fits(full)) return full
        return "$prefix " + truncate(original, MAX_LENGTH - length("$prefix "))
    }

    /** Cuts by code points, never in the middle of a surrogate pair. */
    private fun truncate(text: String, maxCodePoints: Int): String {
        if (maxCodePoints <= 0) return ""
        var end = 0
        var count = 0
        while (end < text.length && count < maxCodePoints) {
            end += Character.charCount(text.codePointAt(end))
            count++
        }
        return if (end >= text.length) text else text.substring(0, end)
    }
}
