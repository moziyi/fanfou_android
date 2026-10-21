package com.kansen.fanfan.core.network

import com.kansen.fanfan.core.common.JsonCodec
import com.kansen.fanfan.core.model.DmConversationUi
import com.kansen.fanfan.core.model.DmMapper
import com.kansen.fanfan.core.model.DmUi
import com.kansen.fanfan.core.network.dto.DmConversationDto
import com.kansen.fanfan.core.network.dto.DmDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing of the payloads the direct-message endpoints really return
 * (captured live on 2026-10-21 against api.fanfou.com).
 */
class M3PayloadTest {

    /** One element of `direct_messages/inbox.json?count=3&mode=lite`. */
    private val inboxPayload = """
        [{
          "id": "13990207",
          "text": "您的账户疑似有非法登录尝试, 时间:2023-12-04 23:12:55; IP:188.131.128.151.",
          "sender_id": "fanfou",
          "recipient_id": "turancao",
          "created_at": "Mon Dec 04 15:12:55 +0000 2023",
          "sender_screen_name": "饭否",
          "recipient_screen_name": "徒然草",
          "sender": {
            "id": "fanfou",
            "name": "饭否",
            "screen_name": "饭否",
            "unique_id": "~ZyxpEIc0iWk",
            "description": "饭否官方帐号",
            "profile_image_url": "https://s3.meituan.net/v1/mss_3d027b52ec5a4d589e68050845611e68/avatar/s0/00/37/9g.jpg?1181650871",
            "profile_image_url_large": "https://s3.meituan.net/v1/mss_3d027b52ec5a4d589e68050845611e68/avatar/l0/00/37/9g.jpg?1181650871",
            "protected": false,
            "followers_count": 309853,
            "friends_count": 16,
            "favourites_count": 100,
            "statuses_count": 0,
            "following": false,
            "notifications": false,
            "created_at": "Thu May 17 04:48:30 +0000 2007",
            "utc_offset": 28800
          },
          "recipient": {
            "id": "turancao",
            "name": "徒然草",
            "screen_name": "徒然草",
            "unique_id": "~K8mQx2pLpYc",
            "profile_image_url": "https://s3.meituan.net/v1/mss_avatar/s0/00/zz/8m.jpg?1354852049",
            "profile_image_url_large": "https://s3.meituan.net/v1/mss_avatar/l0/00/zz/8m.jpg?1354852049"
          }
        }]
    """.trimIndent()

    /** One element of `direct_messages/conversation_list.json?count=3&mode=lite`. */
    private val conversationListPayload = """
        [{
          "dm": {
            "id": "13990207",
            "text": "您的账户疑似有非法登录尝试, 时间:2023-12-04 23:12:55; IP:188.131.128.151.",
            "sender_id": "fanfou",
            "recipient_id": "turancao",
            "created_at": "Mon Dec 04 15:12:55 +0000 2023",
            "sender_screen_name": "饭否",
            "recipient_screen_name": "徒然草",
            "sender": {
              "id": "fanfou",
              "screen_name": "饭否",
              "profile_image_url": "https://s3.meituan.net/v1/mss_3d027b52ec5a4d589e68050845611e68/avatar/s0/00/37/9g.jpg?1181650871",
              "profile_image_url_large": "https://s3.meituan.net/v1/mss_3d027b52ec5a4d589e68050845611e68/avatar/l0/00/37/9g.jpg?1181650871"
            },
            "recipient": {
              "id": "turancao",
              "screen_name": "徒然草",
              "profile_image_url": "https://s3.meituan.net/v1/mss_avatar/s0/00/zz/8m.jpg?1354852049",
              "profile_image_url_large": "https://s3.meituan.net/v1/mss_avatar/l0/00/zz/8m.jpg?1354852049"
            }
          },
          "otherid": "fanfou",
          "msg_num": 18,
          "new_conv": false
        }]
    """.trimIndent()

    @Test
    fun `inbox message parses with sender and time`() {
        val dms = JsonCodec.json.decodeFromString<List<DmDto>>(inboxPayload)
        assertEquals(1, dms.size)
        val dm = dms.first()
        assertEquals("13990207", dm.id)
        assertEquals("fanfou", dm.sender_id)
        assertEquals("turancao", dm.recipient_id)
        assertEquals("饭否", dm.sender_screen_name)
        assertTrue(dm.text!!.startsWith("您的账户疑似有非法登录尝试"))
        assertEquals("饭否", dm.sender?.screen_name)
    }

    @Test
    fun `message is mapped with sender avatar and time`() {
        val dm = JsonCodec.json.decodeFromString<List<DmDto>>(inboxPayload).first()
        val ui: DmUi = DmMapper.toUi(dm, selfId = "turancao")!!
        assertEquals("13990207", ui.id)
        assertEquals("饭否", ui.senderName)
        assertTrue(ui.senderAvatarUrl!!.contains("/avatar/l0/"))
        assertFalse(ui.isMine)
        // "Mon Dec 04 15:12:55 +0000 2023"
        assertEquals(1701702775000L, ui.createdAt)
    }

    @Test
    fun `own message is flagged as mine`() {
        val dm = JsonCodec.json.decodeFromString<List<DmDto>>(inboxPayload).first()
        val ui = DmMapper.toUi(dm, selfId = "fanfou")!!
        assertTrue(ui.isMine)
    }

    @Test
    fun `conversation entry carries other id and counters`() {
        val entries = JsonCodec.json.decodeFromString<List<DmConversationDto>>(conversationListPayload)
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("fanfou", entry.otherid)
        assertEquals(18, entry.msg_num)
        assertFalse(entry.new_conv ?: true)
    }

    @Test
    fun `conversation row shows the other side and the last message`() {
        val entry = JsonCodec.json.decodeFromString<List<DmConversationDto>>(conversationListPayload).first()
        val row: DmConversationUi = DmMapper.toConversationUi(entry, selfId = "turancao")!!
        assertEquals("fanfou", row.otherId)
        assertEquals("饭否", row.otherName)
        assertEquals(18, row.messageCount)
        // The server flag tracks the other side; the local unread state is
        // derived from the last message and a read marker.
        assertTrue(row.lastFromOther)
        assertEquals("13990207", row.lastMessageId)
        assertTrue(row.lastText.startsWith("您的账户疑似有非法登录尝试"))
        assertTrue(row.otherAvatarUrl!!.contains("/avatar/l0/"))
    }
}
