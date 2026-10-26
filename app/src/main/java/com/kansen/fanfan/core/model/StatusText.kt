package com.kansen.fanfan.core.model

/** Fanfou limits a status to 140 characters counted as Unicode code points. */
object StatusText {

    const val MAX_LENGTH = 140

    fun length(text: String): Int = text.codePointCount(0, text.length)

    fun remaining(text: String): Int = MAX_LENGTH - length(text)

    fun fits(text: String): Boolean = remaining(text) >= 0

    /**
     * The text a repost starts from: `转@<author> <original>` — the exact
     * thing the official web client drops into the same composer (a plain
     * link to /home?status=…&repost_status_id=…, no dialog of its own). The
     * prefill is NOT truncated; the official web lets the count go negative.
     * The cut happens once, at send time (truncate). Consumers type their own
     * comment in front of the 转; an author without a screen name gives
     * nothing.
     */
    fun repostPrefill(screenName: String?, originalText: String): String? {
        val name = screenName?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val original = originalText.trim()
        return if (original.isEmpty()) "转@$name" else "转@$name $original"
    }

    /**
     * Cuts by code points, never in the middle of a surrogate pair
     * (positive max expected). The web client clamps each keystroke the same
     * way, so an overflowing repost loses the tail of the original, not the
     * comment typed in front of it.
     */
    fun truncate(text: String, maxCodePoints: Int): String {
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
