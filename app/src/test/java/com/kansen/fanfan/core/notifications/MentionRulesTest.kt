package com.kansen.fanfan.core.notifications

import com.kansen.fanfan.core.network.dto.StatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watermark rules behind the 15-minute mention poller.
 */
class MentionRulesTest {

    private fun status(id: String, rawid: Long) = StatusDto(id = id, rawid = rawid)

    @Test
    fun `newest rawid of a page is the highest one`() {
        val page = listOf(status("a", 10), status("b", 42), status("c", 7))

        assertEquals(42L, MentionRules.newestRawid(page))
        assertEquals(0L, MentionRules.newestRawid(emptyList()))
    }

    @Test
    fun `first run reports nothing so old mentions do not notify`() {
        val page = listOf(status("a", 10), status("b", 42))

        assertTrue(MentionRules.freshMentions(page, notifiedUpTo = 0L).isEmpty())
    }

    @Test
    fun `only mentions newer than the watermark are reported`() {
        val page = listOf(status("a", 10), status("b", 42), status("c", 7))

        val fresh = MentionRules.freshMentions(page, notifiedUpTo = 10L)

        assertEquals(listOf("b"), fresh.map { it.id })
    }

    @Test
    fun `a page without a newer mention yields nothing`() {
        val page = listOf(status("a", 10), status("b", 9))

        assertTrue(MentionRules.freshMentions(page, notifiedUpTo = 10L).isEmpty())
    }
}
