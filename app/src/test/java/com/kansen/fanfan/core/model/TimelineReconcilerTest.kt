package com.kansen.fanfan.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A status that was deleted on the server stays in the local cache until it
 * is pushed out of it. The refresh page says which ones are gone — with the
 * exception of the posts fanfou still holds ("【审核中】") and the ones whose
 * time is unknown.
 */
class TimelineReconcilerTest {

    private val now = 1_700_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun entry(id: String, rawid: Long, ageMillis: Long) =
        TimelineReconciler.CachedEntry(id = id, rawid = rawid, createdAt = now - ageMillis)

    @Test
    fun `a status of the page is kept`() {
        val cached = listOf(entry("a", 100, 2 * hour))

        val stale = TimelineReconciler.staleIds(
            cached = cached,
            pageIds = setOf("a", "b"),
            minRawid = 90,
            now = now,
        )

        assertTrue(stale.isEmpty())
    }

    @Test
    fun `a status that is older than the page is not judged by it`() {
        // The page says nothing about the entries below its oldest item.
        val cached = listOf(entry("old", 10, 5 * hour))

        val stale = TimelineReconciler.staleIds(
            cached = cached,
            pageIds = setOf("a"),
            minRawid = 90,
            now = now,
        )

        assertTrue(stale.isEmpty())
    }

    @Test
    fun `a status that the page does not have is dropped`() {
        val cached = listOf(
            entry("a", 100, 2 * hour),
            entry("gone", 95, 2 * hour),
        )

        val stale = TimelineReconciler.staleIds(
            cached = cached,
            pageIds = setOf("a"),
            minRawid = 90,
            now = now,
        )

        assertEquals(listOf("gone"), stale)
    }

    @Test
    fun `a status that fanfou still holds is kept`() {
        // New posts are missing from the timeline until they are released.
        val cached = listOf(entry("fresh", 100, 5 * 60 * 1000L))

        val stale = TimelineReconciler.staleIds(
            cached = cached,
            pageIds = emptySet(),
            minRawid = 90,
            now = now,
        )

        assertTrue(stale.isEmpty())
    }

    @Test
    fun `a status whose time is unknown is kept`() {
        // A timestamp that could not be parsed is stored as 0; nothing is
        // known about such an entry, so it is not deleted.
        val cached = listOf(TimelineReconciler.CachedEntry(id = "unknown", rawid = 100, createdAt = 0L))

        val stale = TimelineReconciler.staleIds(
            cached = cached,
            pageIds = emptySet(),
            minRawid = 90,
            now = now,
        )

        assertTrue(stale.isEmpty())
    }

    @Test
    fun `a page without ids or rawids covers no window`() {
        // Guessing the window would delete the whole cache.
        assertTrue(TimelineReconciler.windowOf(listOf("a", null), listOf(1L, 2L)) == null)
        assertTrue(TimelineReconciler.windowOf(listOf("a", "b"), listOf(1L, null)) == null)
        assertTrue(TimelineReconciler.windowOf(emptyList(), emptyList()) == null)
    }

    @Test
    fun `a complete page covers the window of its oldest item`() {
        val window = TimelineReconciler.windowOf(listOf("a", "b"), listOf(120L, 100L))

        assertEquals(setOf("a", "b"), window?.ids)
        assertEquals(100L, window?.minRawid)
    }

    @Test
    fun `the grace period is one hour`() {
        val cached = listOf(entry("edge", 100, TimelineReconciler.HOLD_GRACE_MILLIS))

        val stale = TimelineReconciler.staleIds(
            cached = cached,
            pageIds = emptySet(),
            minRawid = 90,
            now = now,
        )

        assertEquals(listOf("edge"), stale)
    }
}
