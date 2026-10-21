package com.kansen.fanfan.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A repost of a status with a photo carries that photo twice in the payload
 * (top level and inside the quoted original). The card must draw it once.
 */
class StatusUiPhotoTest {

    private val cdn = "https://s3-img.meituan.net/v1/xxx/ff/n0/0p/dj/5f_6630.jpg"

    private fun photo(variant: String) = PhotoUi(
        thumbUrl = "$cdn@120w_120h_1l.jpg",
        imageUrl = "$cdn@$variant",
        largeUrl = "$cdn@596w_1l.jpg",
    )

    private fun status(
        text: String,
        photo: PhotoUi?,
        repost: StatusUi?,
    ) = StatusUi(
        id = "1",
        rawid = 1,
        text = text,
        createdAtEpoch = 0,
        user = null,
        photo = photo,
        repost = repost,
        repostStatusId = repost?.id,
        inReplyToStatusId = null,
        inReplyToScreenName = null,
        favorited = false,
        sourceName = null,
        sourceUrl = null,
    )

    private val original = status(
        text = "上传了新照片",
        photo = photo("200w_200h_1l.jpg"),
        repost = null,
    )

    @Test
    fun `a truncated repost does not repeat the photo of the quote`() {
        val repost = status(
            text = "很长的评论".repeat(20),
            photo = photo("200w_200h_1l.jpg"),
            repost = original,
        )
        assertTrue(repost.showsRepostQuote)

        assertNull(repost.bodyPhoto)
    }

    @Test
    fun `a photo of its own is kept next to the quote`() {
        val other = "https://s3-img.meituan.net/v1/xxx/ff/n0/0p/dj/other.jpg"
        val repost = status(
            text = "很长的评论".repeat(20),
            photo = PhotoUi(
                thumbUrl = "$other@120w_120h_1l.jpg",
                imageUrl = "$other@200w_200h_1l.jpg",
                largeUrl = "$other@596w_1l.jpg",
            ),
            repost = original,
        )

        assertTrue(repost.showsRepostQuote)
        assertEquals(repost.photo, repost.bodyPhoto)
    }

    @Test
    fun `an untruncated repost shows the photo once in the body`() {
        val repost = status(
            text = "转@徒然草 上传了新照片",
            photo = photo("200w_200h_1l.jpg"),
            repost = original,
        )
        assertFalse(repost.showsRepostQuote)

        assertEquals(repost.photo, repost.bodyPhoto)
    }

    @Test
    fun `a repost without its own photo falls back to the original`() {
        val repost = status(
            text = "转@徒然草 上传了新照片",
            photo = null,
            repost = original,
        )

        assertEquals(original.photo, repost.bodyPhoto)
    }

    @Test
    fun `photos of different objects are not the same`() {
        val other = photo("200w_200h_1l.jpg").copy(
            thumbUrl = "https://s3-img.meituan.net/v1/xxx/ff/n0/0p/dj/other.jpg@120w_120h_1l.jpg",
            imageUrl = "https://s3-img.meituan.net/v1/xxx/ff/n0/0p/dj/other.jpg@200w_200h_1l.jpg",
            largeUrl = "https://s3-img.meituan.net/v1/xxx/ff/n0/0p/dj/other.jpg@596w_1l.jpg",
        )

        assertFalse(photo("200w_200h_1l.jpg").isSameObject(other))
        assertTrue(photo("596w_1l.jpg").isSameObject(photo("200w_200h_1l.jpg")))
    }
}
