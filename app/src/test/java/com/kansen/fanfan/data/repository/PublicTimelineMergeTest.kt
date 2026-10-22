package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.model.StatusUi
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The public timeline lives in memory only. Its list is bounded, and a page
 * that arrives twice must not grow it.
 */
class PublicTimelineMergeTest {

    private fun status(id: String) = StatusUi(
        id = id,
        rawid = 0,
        text = "",
        createdAtEpoch = 0,
        user = null,
        photo = null,
        repost = null,
        repostStatusId = null,
        inReplyToStatusId = null,
        inReplyToScreenName = null,
        favorited = false,
        sourceName = null,
        sourceUrl = null,
    )

    @Test
    fun `an older page is appended behind the current one`() {
        val merged = merge(listOf(status("a"), status("b")), listOf(status("c")), 10)

        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun `an entry that is on screen already is dropped`() {
        val merged = merge(listOf(status("a"), status("b")), listOf(status("b"), status("c")), 10)

        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun `the newest entries are the ones that are kept`() {
        val current = (1..5).map { status("s$it") }
        val fresh = (6..8).map { status("s$it") }

        val merged = merge(current, fresh, maxItems = 6)

        assertEquals(listOf("s1", "s2", "s3", "s4", "s5", "s6"), merged.map { it.id })
    }

    @Test
    fun `an empty page changes nothing`() {
        val current = listOf(status("a"))

        val merged = merge(current, emptyList(), 10)

        assertEquals(listOf("a"), merged.map { it.id })
    }
}
