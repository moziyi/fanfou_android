package com.kansen.fanfan.core.model

/**
 * The rule that decides which cached timeline entries are gone from the
 * server.
 *
 * A refresh fetches the newest page (30 items). Everything in the cache that
 * is **newer than the oldest item of that page** but not in the page has been
 * deleted on the server — with one exception: fanfou holds new posts for a
 * few minutes ("【审核中】") and they are missing from the timeline until
 * they are released. A status that was created recently is therefore kept
 * even when the page does not have it.
 */
object TimelineReconciler {

    /** How long a status may be missing from the page before it counts as deleted. */
    const val HOLD_GRACE_MILLIS = 60 * 60 * 1000L

    /** The window a page covers: its ids and its oldest item. */
    data class PageWindow(
        val ids: Set<String>,
        val minRawid: Long,
    )

    /**
     * The window the page covers, or null when the page does not say: an
     * item without an id or a rawid leaves the window unknown, and guessing
     * it would make the reconciliation delete the whole cache.
     */
    fun windowOf(ids: List<String?>, rawids: List<Long?>): PageWindow? {
        if (ids.isEmpty() || ids.size != rawids.size) return null
        if (ids.any { it == null } || rawids.any { it == null }) return null
        return PageWindow(
            ids = ids.filterNotNull().toSet(),
            minRawid = rawids.filterNotNull().min(),
        )
    }

    /**
     * The ids of the cached entries that the page proves are gone.
     *
     * @param cached the entries of the timeline, with the age of their status
     * @param pageIds the ids the server just returned
     * @param minRawid the smallest `rawid` of that page
     * @param now the current time
     */
    fun staleIds(
        cached: List<CachedEntry>,
        pageIds: Set<String>,
        minRawid: Long,
        now: Long,
    ): List<String> = cached
        .filter { it.rawid >= minRawid }
        .filter { it.id !in pageIds }
        .filter { now - it.createdAt >= HOLD_GRACE_MILLIS }
        .map { it.id }

    /** A cached timeline entry with the age of the status behind it. */
    data class CachedEntry(
        val id: String,
        val rawid: Long,
        val createdAt: Long,
    )
}
