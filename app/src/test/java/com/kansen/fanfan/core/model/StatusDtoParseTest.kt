package com.kansen.fanfan.core.model

import com.kansen.fanfan.core.common.JsonCodec
import com.kansen.fanfan.core.model.StatusMapper.toUi
import com.kansen.fanfan.core.network.dto.StatusDto
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture mirrors the wiki payload shape (status object with embedded user,
 * photo, nested repost), plus additive/unknown keys the live API may send.
 */
private const val STATUS_JSON = """
{
  "id": "UcIlC04F2pQ",
  "rawid": 912345,
  "created_at": "Wed Nov 09 07:15:21 +0000 2011",
  "text": "hello 世界",
  "source": "<a href=\"http://fanfou.com\" rel=\"nofollow\">网页</a>",
  "truncated": false,
  "favorited": false,
  "in_reply_to_status_id": null,
  "in_reply_to_user_id": null,
  "in_reply_to_screen_name": null,
  "repost_status_id": "ZzYyXx",
  "repost_status": {
    "id": "ZzYyXx",
    "rawid": 912300,
    "created_at": "Wed Nov 09 06:00:00 +0000 2011",
    "text": "original",
    "user": { "id": "u1", "name": "原主", "screen_name": "orig" }
  },
  "repost_user_id": "u1",
  "repost_screen_name": "orig",
  "photo": {
    "imageurl": "http://static.fanfou.com/img/m.jpg",
    "thumburl": "http://static.fanfou.com/img/t.jpg",
    "largeurl": "http://static.fanfou.com/img/l.jpg"
  },
  "user": {
    "id": "u2",
    "name": "测试",
    "screen_name": "tester",
    "profile_image_url": "http://static.fanfou.com/avatar/s.jpg",
    "profile_image_url_large": "http://static.fanfou.com/avatar/l.jpg",
    "followers_count": 3,
    "some_future_field": { "nested": true }
  }
}
"""

class StatusDtoParseTest {

    private fun dto(json: String = STATUS_JSON): StatusDto =
        JsonCodec.json.decodeFromString<StatusDto>(json)

    @Test
    fun `parses documented fields and ignores unknown keys`() {
        val status = dto()
        assertEquals("UcIlC04F2pQ", status.id)
        assertEquals(912345L, status.rawid)
        assertEquals("hello 世界", status.text)
        assertEquals("u2", status.user?.id)
        assertEquals("tester", status.user?.screen_name)
        assertEquals(3, status.user?.followers_count)
        assertEquals("ZzYyXx", status.repost_status?.id)
        assertEquals("original", status.repost_status?.text)
        assertEquals("http://static.fanfou.com/img/t.jpg", status.photo?.thumburl)
    }

    @Test
    fun `maps to UI model with overlay favorite state`() {
        val ui = dto().toUi(favorited = true)
        assertEquals("UcIlC04F2pQ", ui.id)
        assertEquals(1320822921000L, ui.createdAtEpoch)
        assertEquals("tester", ui.user?.screenName)
        assertEquals("http://static.fanfou.com/avatar/l.jpg", ui.user?.avatarUrl)
        assertEquals("网页", ui.sourceName)
        assertEquals("http://fanfou.com", ui.sourceUrl)
        assertEquals("original", ui.repost?.text)
        assertTrue(ui.favorited)
        assertTrue(ui.isRepost)
        assertFalse(ui.isReply)
    }

    @Test
    fun `survives missing and null fields`() {
        val ui = dto("""{"id":"only-id"}""").toUi()
        assertEquals("only-id", ui.id)
        assertEquals("", ui.text)
        assertEquals(0L, ui.createdAtEpoch)
        assertNull(ui.user)
        assertNull(ui.photo)
        assertNull(ui.repost)
        assertFalse(ui.favorited)
    }

    @Test
    fun `keeps repost id when nested body is stripped by mode=lite`() {
        val ui = dto(
            """{"id":"a","rawid":1,"repost_status_id":"orig-id","text":"转发"}""",
        ).toUi()
        assertTrue(ui.isRepost)
        assertEquals("orig-id", ui.repostStatusId)
        assertNull(ui.repost)
        // No nested body to compare against, so no quote block to show either.
        assertFalse(ui.showsRepostQuote)
    }
}
