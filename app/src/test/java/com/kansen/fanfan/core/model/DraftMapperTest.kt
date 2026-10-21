package com.kansen.fanfan.core.model

import com.kansen.fanfan.core.database.entity.DraftEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraftMapperTest {

    private fun draft(
        id: String = "composer",
        text: String = "草稿内容",
        inReplyTo: String? = null,
        repost: String? = null,
    ) = DraftEntity(
        id = id,
        text = text,
        inReplyToStatusId = inReplyTo,
        repostStatusId = repost,
        updatedAt = 1_000L,
    )

    @Test
    fun `a plain draft is a status of its own`() {
        val ui = DraftMapper.toUi(draft())

        assertEquals(DraftKind.NEW, ui.kind)
        assertNull(ui.targetId)
        assertEquals("消息", ui.contextLabel)
    }

    @Test
    fun `a reply draft names the author of the status it answers`() {
        val ui = DraftMapper.toUi(
            draft(id = "reply:abc", inReplyTo = "abc"),
            mapOf("abc" to "徒然草"),
        )

        assertEquals(DraftKind.REPLY, ui.kind)
        assertEquals("abc", ui.targetId)
        assertEquals("回复 @徒然草", ui.contextLabel)
    }

    @Test
    fun `a repost draft is recognised as one`() {
        val ui = DraftMapper.toUi(draft(id = "repost:xyz", repost = "xyz"))

        assertEquals(DraftKind.REPOST, ui.kind)
        assertEquals("xyz", ui.targetId)
    }

    @Test
    fun `a target that is no longer cached is shown without a name`() {
        val ui = DraftMapper.toUi(draft(id = "reply:abc", inReplyTo = "abc"))

        assertEquals("回复", ui.contextLabel)
    }
}
