package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.database.dao.StatusDao
import com.kansen.fanfan.core.database.entity.FavoriteOverrideEntity
import com.kansen.fanfan.core.model.StatusMapper
import com.kansen.fanfan.core.model.StatusMapper.toUi
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.core.network.api.FavoritesApi
import com.kansen.fanfan.core.network.api.StatusesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Write path: publish / reply / repost / favorite. Network first, then update
 * the local cache; failures surface to the UI (no offline queue, design D5).
 */
@Singleton
class StatusRepository @Inject constructor(
    private val statusesApi: StatusesApi,
    private val favoritesApi: FavoritesApi,
    private val statusDao: StatusDao,
    private val store: StatusStore,
) {

    suspend fun post(
        text: String,
        inReplyToStatusId: String? = null,
        repostStatusId: String? = null,
    ): Result<StatusUi> = runCatching {
        val dto = statusesApi.update(
            status = text,
            inReplyToStatusId = inReplyToStatusId,
            repostStatusId = repostStatusId,
        )
        store.saveTimeline(listOf(dto), TimelineType.HOME.key)
        dto.toUi()
    }

    /** Flips the favorite state and records it in the local overlay table. */
    suspend fun toggleFavorite(status: StatusUi): Result<Boolean> = runCatching {
        val target = !status.favorited
        val dto = if (target) favoritesApi.create(status.id) else favoritesApi.destroy(status.id)
        val effective = dto.favorited ?: target
        statusDao.upsertFavoriteOverride(FavoriteOverrideEntity(status.id, effective))
        effective
    }

    fun status(id: String): Flow<StatusUi?> =
        statusDao.statusFlow(id).map { row -> row?.let(StatusMapper::toUi) }

    /** Refreshes one status from the server into the local cache. */
    suspend fun refreshStatus(id: String): Result<Unit> = runCatching {
        val dto = statusesApi.show(id)
        store.saveBare(listOf(dto))
    }

    /** Thread context around a status, newest first, as display models. */
    suspend fun context(id: String): Result<List<StatusUi>> = runCatching {
        val statuses = statusesApi.contextTimeline(id)
        store.saveBare(statuses)
        statuses.map { it.toUi() }.sortedBy { it.rawid }
    }
}
