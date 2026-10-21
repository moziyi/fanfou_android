package com.kansen.fanfan.core.model

/**
 * The text of a search result carries the search words as markup: the server
 * wraps them in `<strong>` and escapes the whole string (`&lt;strong&gt;`),
 * live-verified 2026-10-21.
 *
 * The raw text must not be shown as it is; it is decoded and split here, so
 * the screen can draw the words in bold.
 */
object SearchHighlight {

    /** A piece of the text: the words that matched are [bold]. */
    data class Span(val text: String, val bold: Boolean)

    private val entities = mapOf(
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'",
        "&nbsp;" to " ",
        "&amp;" to "&",
    )

    /** The tags the server uses for the words that matched. */
    private val tags = listOf("strong", "b")

    /**
     * Decodes the escaped markup and splits the text into its parts.
     *
     * The entities are decoded first: the tags of the payload are escaped as
     * well, so decoding them later would find nothing.
     */
    fun parse(raw: String): List<Span> {
        if (raw.isEmpty()) return emptyList()
        val decoded = decodeEntities(raw)
        return splitTags(decoded)
    }

    /** The text without any markup — for a summary or a notification. */
    fun plain(raw: String): String = parse(raw).joinToString("") { it.text }

    private fun decodeEntities(raw: String): String {
        var text = raw
        for ((entity, replacement) in entities) {
            if (text.contains(entity)) text = text.replace(entity, replacement)
        }
        return text
    }

    private fun splitTags(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        val current = StringBuilder()
        var bold = false
        var index = 0
        while (index < text.length) {
            val tag = tags.firstOrNull { text.startsWith("<$it>", index) }
            if (tag != null) {
                if (current.isNotEmpty()) {
                    spans += Span(current.toString(), bold)
                    current.clear()
                }
                bold = true
                index += tag.length + 2
                continue
            }
            val closing = tags.firstOrNull { text.startsWith("</$it>", index) }
            if (closing != null) {
                if (current.isNotEmpty()) {
                    spans += Span(current.toString(), bold)
                    current.clear()
                }
                bold = false
                index += closing.length + 3
                continue
            }
            current.append(text[index])
            index++
        }
        if (current.isNotEmpty()) spans += Span(current.toString(), bold)
        return spans
    }
}
