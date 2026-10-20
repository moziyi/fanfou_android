package com.kansen.fanfan.core.model

/** Immutable display model derived from a status payload. Never persisted. */
data class StatusUi(
    val id: String,
    val rawid: Long,
    val text: String,
    val createdAtEpoch: Long,
    val user: UserUi?,
    val photo: PhotoUi?,
    val repost: StatusUi?,
    val repostStatusId: String?,
    val inReplyToStatusId: String?,
    val inReplyToScreenName: String?,
    val favorited: Boolean,
    val sourceName: String?,
    val sourceUrl: String?,
    /** Present when the API supplies the repost target even if [repost] was stripped (mode=lite). */
    val repostScreenName: String? = null,
    val location: String? = null,
    /**
     * The status this one answers. The timeline payload never embeds it, so it
     * is fetched and cached separately (see ReplyTargetResolver).
     */
    val replied: StatusUi? = null,
) {
    val isReply: Boolean get() = inReplyToStatusId != null

    val isRepost: Boolean get() = repost != null || repostStatusId != null

    /**
     * Fanfou composes a repost's text as `comment 转@author <original text>`, so
     * quoting the target again prints the same words twice. The quote is only
     * kept when that text was cut at the 140-character limit (measured on live
     * reposts: 2 of 13 were cut, both at exactly 140), because then the quoted
     * block is the only copy of the tail.
     */
    val showsRepostQuote: Boolean
        get() {
            val original = repost?.text?.trim().orEmpty()
            return original.isNotEmpty() && !text.trim().endsWith(original)
        }

    /**
     * A repost carries the original's photo, and the outer status usually has
     * none of its own. Only used when the quote block is hidden — otherwise the
     * photo would render twice, since the block shows it too.
     */
    val bodyPhoto: PhotoUi?
        get() = photo ?: if (showsRepostQuote) null else repost?.photo
}

data class UserUi(
    val id: String,
    val name: String,
    val screenName: String,
    val avatarUrl: String?,
)

/**
 * Display model for a user page. Distinct from [UserUi] (author chip): the
 * profile carries the relationship state and counters, which are only
 * trustworthy for the fetch that returned them.
 */
data class UserProfileUi(
    val id: String,
    val name: String,
    val screenName: String,
    val avatarUrl: String?,
    val description: String?,
    val location: String?,
    val following: Boolean,
    /** A follow request is waiting for the other side to accept it. */
    val followRequested: Boolean,
    val protected: Boolean,
    val followersCount: Int?,
    val friendsCount: Int?,
    val statusesCount: Int?,
    val favouritesCount: Int?,
) {
    val hasProfile: Boolean get() = id.isNotBlank()
}

/**
 * Fanfou serves every photo off one Meituan S3 object, and the three fields only
 * differ by the processing directive appended to it (measured 2026-10-19 on a
 * 4000x3000 original):
 *
 * ```
 * thumburl  .../8g_119611.jpg@120w_120h_1l.jpg    120x90     4 KB
 * imageurl  .../8g_119611.jpg@200w_200h_1l.jpg    200x150   10 KB
 * largeurl  .../8g_119611.jpg@596w_1l.jpg         596x447   78 KB
 * (strip @) .../8g_119611.jpg                   4000x3000 3.1 MB
 * ```
 *
 * The directive is client-controlled: any `@<w>w[_<h>h]_1l.jpg` is accepted
 * (`_1l` fits inside the box and keeps the aspect; it does not crop), so the
 * list can ask for exactly the pixels it draws instead of upscaling imageurl.
 */
data class PhotoUi(
    val thumbUrl: String?,
    val imageUrl: String?,
    val largeUrl: String?,
) {
    /**
     * URL at roughly [widthPx] pixels wide, or the largest static variant when
     * the URL is not one this directive can be applied to (older CDNs, or a
     * payload that grew a different shape).
     */
    fun atWidth(widthPx: Int): String? {
        val source = largeUrl ?: imageUrl ?: thumbUrl ?: return null
        if (widthPx <= 0) return source
        val base = cdnBase(source) ?: return source
        return "$base@${widthPx}w_1l.jpg"
    }

    /** The bare object URL, i.e. the full-size original; not used by lists. */
    val originalUrl: String? get() = (largeUrl ?: imageUrl ?: thumbUrl)?.let(::cdnBase)
}

/** `.../x.jpg@596w_1l.jpg` → `.../x.jpg`; null when there is no such directive. */
private fun cdnBase(url: String): String? {
    val at = url.indexOf('@')
    if (at <= 0) return null
    val head = url.substring(0, at)
    return head.takeIf {
        it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".png", true)
    }
}
