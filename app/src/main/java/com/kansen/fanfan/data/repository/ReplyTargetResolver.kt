package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.database.dao.StatusDao
import com.kansen.fanfan.core.network.api.StatusesApi
import com.kansen.fanfan.core.network.dto.StatusDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import com.kansen.fanfan.core.common.runCatchingCancellable

/**
 * Fetches the statuses a page's replies answer. Fanfou's timeline payload only
 * carries `in_reply_to_status_id`, never the parent body, so the parent has to
 * be pulled separately — a handful of requests per page (measured: 2 replies
 * in 30 home-timeline statuses, 1 of which was outside the page) and none at
 * all once the parent is cached.
 *
 * A failed fetch is not fatal: the reply card simply renders without its quoted
 * parent (deleted, protected, or offline).
 */
@Singleton
class ReplyTargetResolver @Inject constructor(
    private val api: StatusesApi,
    private val statusDao: StatusDao,
    private val store: StatusStore,
) {

    suspend fun warm(statuses: List<StatusDto>) {
        val missing = statuses
            .mapNotNull { it.in_reply_to_status_id?.takeIf(String::isNotBlank) }
            .distinct()
            .filter { statusDao.statusById(it) == null }
        if (missing.isEmpty()) return

        val fetched = missing.chunked(MAX_PARALLEL).flatMap { chunk ->
            coroutineScope {
                chunk.map { id -> async { runCatchingCancellable { api.show(id) }.getOrNull() } }.awaitAll()
            }
        }.filterNotNull()

        store.saveBare(fetched)
    }

    private companion object {
        /** Keeps a page's worth of parents from hitting the API all at once. */
        const val MAX_PARALLEL = 4
    }
}
