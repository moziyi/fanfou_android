package com.kansen.fanfan.core.model

/**
 * `source` is documented both as plain text ("网页") and as an HTML anchor
 * `<a href="http://fanfou.com">网页</a>`. Display the label, keep the link.
 */
object SourceText {

    private val tags = Regex("<[^>]+>")
    private val href = Regex("""href\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)

    fun name(source: String?): String? {
        val raw = source?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (!raw.contains('<')) return raw
        return tags.replace(raw, "").trim().ifEmpty { null }
    }

    fun url(source: String?): String? =
        source?.let { href.find(it)?.groupValues?.getOrNull(1)?.trim()?.ifEmpty { null } }
}
