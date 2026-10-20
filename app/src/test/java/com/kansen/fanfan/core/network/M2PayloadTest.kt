package com.kansen.fanfan.core.network

import com.kansen.fanfan.core.common.JsonCodec
import com.kansen.fanfan.core.model.toProfileUi
import com.kansen.fanfan.core.network.dto.StatusDto
import com.kansen.fanfan.core.network.dto.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing of the payloads the M2 endpoints really return (captured live on
 * 2026-10-20 against api.fanfou.com).
 */
class M2PayloadTest {

    /** One element of `statuses/mentions.json?count=5&mode=lite`. */
    private val mentionsPayload = """
        [{
          "created_at": "Tue Oct 20 03:11:54 +0000 2026",
          "id": "ZCf_EyEVgF8",
          "rawid": 276441992,
          "text": "@徒然草 不过也有点晒",
          "source": "<a href=\"http://imach.me/gohanapp\" target=\"_blank\">御飯 iOS</a>",
          "truncated": false,
          "in_reply_to_status_id": "AGNK4Un86Wo",
          "in_reply_to_user_id": "turancao",
          "favorited": false,
          "in_reply_to_screen_name": "徒然草",
          "is_self": false,
          "location": "",
          "user": {
            "id": "deadsun",
            "name": "梓曜_外卖员",
            "screen_name": "梓曜_外卖员",
            "unique_id": "~Ik5v7SKeEtI",
            "description": "向往一无所有的人生",
            "profile_image_url": "https://s3.meituan.net/v1/mss_avatar/s0/00/zz/n1.jpg?1354852049",
            "profile_image_url_large": "https://s3.meituan.net/v1/mss_avatar/l0/00/zz/n1.jpg?1354852049",
            "protected": true,
            "followers_count": 251,
            "friends_count": 351,
            "favourites_count": 0,
            "statuses_count": 15064,
            "following": false
          }
        }]
    """.trimIndent()

    @Test
    fun `mention payload keeps rawid, reply target and author`() {
        val page = JsonCodec.json.decodeFromString<List<StatusDto>>(mentionsPayload)

        assertEquals(1, page.size)
        val mention = page.first()
        assertEquals("ZCf_EyEVgF8", mention.id)
        // rawid is the monotonic id the unread watermark compares on.
        assertEquals(276441992L, mention.rawid)
        assertEquals("AGNK4Un86Wo", mention.in_reply_to_status_id)
        assertEquals("徒然草", mention.in_reply_to_screen_name)
        assertEquals("deadsun", mention.user?.id)
    }

    @Test
    fun `user profile payload exposes the relationship state and counters`() {
        val payload = """
            {
              "id": "starwalker",
              "name": "向告别飞驰吧",
              "screen_name": "向告别飞驰吧",
              "description": "在所有人事已非的景色里",
              "profile_image_url": "https://s3.meituan.net/v1/mss_avatar/s0/00/zz/n1.jpg?1354852049",
              "profile_image_url_large": "https://s3.meituan.net/v1/mss_avatar/l0/00/zz/n1.jpg?1354852049",
              "protected": true,
              "followers_count": 251,
              "friends_count": 351,
              "favourites_count": 0,
              "statuses_count": 15064,
              "following": true
            }
        """.trimIndent()

        val profile = JsonCodec.json.decodeFromString<UserDto>(payload).toProfileUi()

        assertEquals("starwalker", profile.id)
        assertTrue(profile.following)
        assertTrue(profile.protected)
        assertEquals(251, profile.followersCount)
        assertEquals(351, profile.friendsCount)
        assertEquals(15064, profile.statusesCount)
        // The large avatar is preferred: it is the one that stays sharp in the
        // 72dp profile header.
        assertTrue(profile.avatarUrl!!.contains("/l0/"))
    }

    @Test
    fun `profile of a user without a name falls back to the screen name`() {
        val payload = """{"id":"someone","screen_name":"某人"}"""

        val profile = JsonCodec.json.decodeFromString<UserDto>(payload).toProfileUi()

        assertEquals("某人", profile.name)
        assertEquals("某人", profile.screenName)
        assertFalse(profile.following)
        assertEquals(null, profile.avatarUrl)
    }
}
