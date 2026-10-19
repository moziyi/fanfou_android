package com.kansen.fanfan.core.model

import com.kansen.fanfan.core.common.JsonCodec
import com.kansen.fanfan.core.database.entity.StatusRow
import com.kansen.fanfan.core.network.dto.PhotoDto
import com.kansen.fanfan.core.network.dto.StatusDto
import com.kansen.fanfan.core.network.dto.UserDto

/**
 * Entity(rawJson) → UI model. Decoding happens on every read; the raw payload
 * is the source of truth (schema drift can always be re-parsed).
 *
 * Fanfou sends absent optional fields as empty strings, not nulls — measured on
 * live payloads (`"in_reply_to_status_id":""` on every non-reply). Every
 * optional string therefore goes through [blankToNull] before it reaches the UI,
 * otherwise `isReply` / `isRepost` would be true for every status.
 */
object StatusMapper {

    fun toUi(row: StatusRow): StatusUi {
        val dto = runCatching {
            JsonCodec.json.decodeFromString<StatusDto>(row.status.rawJson)
        }.getOrNull() ?: StatusDto()
        return dto.toUi(favorited = row.favoritedOverride ?: dto.favorited ?: false).copy(
            id = row.status.id,
            rawid = row.status.rawid,
            createdAtEpoch = row.status.createdAtEpoch,
        )
    }

    fun StatusDto.toUi(favorited: Boolean = this.favorited ?: false): StatusUi = StatusUi(
        id = id.orEmpty(),
        rawid = rawid ?: 0L,
        text = text.orEmpty(),
        createdAtEpoch = ApiTime.parseEpochMillis(created_at),
        user = user?.toUi(),
        photo = photo?.toUi()?.takeIf { it.thumbUrl != null || it.imageUrl != null },
        repost = repost_status?.toUi(),
        repostStatusId = repost_status_id.blankToNull(),
        inReplyToStatusId = in_reply_to_status_id.blankToNull(),
        inReplyToScreenName = in_reply_to_screen_name.blankToNull(),
        favorited = favorited,
        sourceName = SourceText.name(source),
        sourceUrl = SourceText.url(source),
        repostScreenName = repost_screen_name.blankToNull(),
        location = location.blankToNull(),
    )

    fun UserDto.toUi(): UserUi = UserUi(
        id = id.orEmpty(),
        name = name.blankToNull() ?: screen_name.blankToNull().orEmpty(),
        screenName = screen_name.orEmpty(),
        avatarUrl = (profile_image_url_large.blankToNull() ?: profile_image_url.blankToNull()),
    )

    fun PhotoDto.toUi(): PhotoUi = PhotoUi(
        thumbUrl = thumburl.blankToNull() ?: imageurl.blankToNull(),
        imageUrl = imageurl.blankToNull() ?: thumburl.blankToNull(),
        largeUrl = largeurl.blankToNull() ?: imageurl.blankToNull(),
    )

    private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }
}
