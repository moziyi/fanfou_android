package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.common.runCatchingCancellable
import com.kansen.fanfan.core.database.dao.StatusDao
import com.kansen.fanfan.core.model.StatusMapper
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.core.model.TimelineReconciler
import com.kansen.fanfan.core.network.api.StatusesApi
import com.kansen.fanfan.core.network.dto.StatusDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class TimelineType(val key: String, val label: String) {
    HOME("home", "首页"),
    PUBLIC("public", "随便看看"),
    MENTIONS("mentions", "@我"),
    ;

    companion object {
        /** Timelines offered as top tabs on the home screen. */
        val feedTabs = listOf(HOME, PUBLIC)
    }
}

/**
 * Read path for timelines. Room is the single source of truth: a manual
 * refresh pulls the newest full page (no `since_id` — moderated posts become
 * visible minutes after their creation time, so an incremental fetch would
 * miss them permanently; see [refresh]) and loadMore fetches older ones
 * (`max_id`); the UI only observes the local table and is therefore
 * offline-capable.
 */
@Singleton
class TimelineRepository @Inject constructor(
    private val api: StatusesApi,
    private val statusDao: StatusDao,
    private val store: StatusStore,
    private val replyTargets: ReplyTargetResolver,
) {

    fun timeline(type: TimelineType): Flow<List<StatusUi>> =
        statusDao.timeline(type.key).map { rows -> attachReplyTargets(rows.map(StatusMapper::toUi)) }

    /**
     * Pull-to-refresh always fetches the full newest page instead of only
     * `since_id` supplements. Content can become visible later than its
     * creation time — fanfou releases moderated posts (`【审核中】`) minutes
     * after posting, and those are invisible to every `since_id` refresh that
     * runs in between, so they would never reach the cache (observed live
     * 2026-10-20). A full page costs 30 items and repairs such gaps.
     */
    suspend fun refresh(type: TimelineType): Result<Unit> = runCatchingCancellable {
        val page = fetch(type, sinceId = null, maxId = null)
        cachePage(type, page)
        reconcileDeleted(type, page)
    }

    /** Returns true when older statuses were appended (false = end reached). */
    suspend fun loadMore(type: TimelineType): Result<Boolean> = runCatchingCancellable {
        val oldestId = statusDao.oldestId(type.key) ?: return@runCatchingCancellable false
        val page = fetch(type, sinceId = null, maxId = oldestId)
        if (page.isEmpty()) return@runCatchingCancellable false
        cachePage(type, page)
        true
    }

    /**
     * The single write path into the timeline cache: store the page, trim the
     * timeline and warm the reply parents.
     *
     * The poll worker caches pages too, and it must not take a shortcut: the
     * reply parents are bare rows, so they are only fetched *after* the trim
     * (an earlier prune would collect them before they were ever read).
     */
    suspend fun cachePage(type: TimelineType, page: List<StatusDto>) {
        if (page.isEmpty()) return
        store.saveTimeline(page, type.key)
        store.trim(type.key)
        replyTargets.warm(page)
    }

    /**
     * Drops the entries of a timeline that the page proves are gone: a status
     * that was deleted on the server (here or in the web client) would
     * otherwise stay in the cache until it is pushed out of it.
     *
     * Only the entries of this timeline are touched, and only those that are
     * newer than the oldest item of the page — the page says nothing about
     * the older ones. A status that is missing from the page but was created
     * recently is kept: fanfou holds new posts for a few minutes, and those
     * are not in the timeline until they are released.
     */
    private suspend fun reconcileDeleted(type: TimelineType, page: List<StatusDto>) {
        if (page.isEmpty()) return
        // A page without ids or rawids says nothing about the window it
        // covers; guessing it would delete far more than it should.
        val window = TimelineReconciler.windowOf(
            ids = page.map { it.id },
            rawids = page.map { it.rawid },
        ) ?: return
        val pageIds = window.ids
        val minRawid = window.minRawid
        val cached = statusDao.timelineEntries(type.key).map {
            TimelineReconciler.CachedEntry(
                id = it.statusId,
                rawid = it.rawid,
                createdAt = it.createdAtEpoch,
            )
        }
        val stale = TimelineReconciler.staleIds(
            cached = cached,
            pageIds = pageIds,
            minRawid = minRawid,
            now = System.currentTimeMillis(),
        )
        if (stale.isEmpty()) return
        statusDao.deleteTimelineEntriesOf(type.key, stale)
        statusDao.pruneOrphans()
    }

    /**
     * A reply's parent is not in the payload, so it is looked up per card; the
     * parent may be absent (never fetched, deleted, or protected) and then the
     * card simply renders without the quoted block.
     */
    private suspend fun attachReplyTargets(statuses: List<StatusUi>): List<StatusUi> {
        val ids = statuses.mapNotNull { it.inReplyToStatusId }.distinct()
        if (ids.isEmpty()) return statuses
        val targets = statusDao.statusByIds(ids).associate { it.status.id to StatusMapper.toUi(it) }
        return statuses.map { status ->
            val parent = status.inReplyToStatusId?.let(targets::get)
            if (parent == null) status else status.copy(replied = parent)
        }
    }

    private suspend fun fetch(type: TimelineType, sinceId: String?, maxId: String?) = when (type) {
        TimelineType.HOME -> api.homeTimeline(sinceId = sinceId, maxId = maxId, count = PAGE_SIZE)
        TimelineType.PUBLIC -> api.publicTimeline(sinceId = sinceId, maxId = maxId, count = PAGE_SIZE)
        TimelineType.MENTIONS -> api.mentions(sinceId = sinceId, maxId = maxId, count = PAGE_SIZE)
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
