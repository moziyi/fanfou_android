package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.database.dao.StatusDao
import com.kansen.fanfan.core.model.StatusMapper
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.core.network.api.StatusesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class TimelineType(val key: String, val label: String) {
    HOME("home", "首页"),
    PUBLIC("public", "随便看看"),
}

/**
 * Read path for timelines. Room is the single source of truth: refresh fetches
 * newer statuses (`since_id`) and loadMore fetches older ones (`max_id`); the
 * UI only observes the local table and is therefore offline-capable.
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

    /** Pull-to-refresh: fetch only statuses newer than the local newest id. */
    suspend fun refresh(type: TimelineType): Result<Unit> = runCatching {
        val sinceId = statusDao.newestId(type.key)
        val page = fetch(type, sinceId = sinceId, maxId = null)
        store.saveTimeline(page, type.key)
        store.trim(type.key)
        // After the trim: reply parents are bare rows, and an earlier prune
        // would collect them before they are ever read.
        replyTargets.warm(page)
    }

    /** Returns true when older statuses were appended (false = end reached). */
    suspend fun loadMore(type: TimelineType): Result<Boolean> = runCatching {
        val oldestId = statusDao.oldestId(type.key) ?: return@runCatching false
        val page = fetch(type, sinceId = null, maxId = oldestId)
        if (page.isEmpty()) return@runCatching false
        store.saveTimeline(page, type.key)
        store.trim(type.key)
        replyTargets.warm(page)
        true
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
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
