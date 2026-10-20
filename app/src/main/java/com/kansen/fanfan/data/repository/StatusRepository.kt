package com.kansen.fanfan.data.repository

import androidx.room.withTransaction
import com.kansen.fanfan.core.database.FanfanDatabase
import com.kansen.fanfan.core.database.dao.StatusDao
import com.kansen.fanfan.core.database.entity.FavoriteOverrideEntity
import com.kansen.fanfan.core.model.StatusMapper
import com.kansen.fanfan.core.model.StatusMapper.toUi
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.core.network.api.FavoritesApi
import com.kansen.fanfan.core.network.api.StatusesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Write path: publish / reply / repost / favorite / delete. Network first, then
 * update the local cache; failures surface to the UI (no offline queue, design D5).
 */
@Singleton
class StatusRepository @Inject constructor(
    private val statusesApi: StatusesApi,
    private val favoritesApi: FavoritesApi,
    private val statusDao: StatusDao,
    private val db: FanfanDatabase,
    private val store: StatusStore,
) {

    suspend fun post(
        text: String,
        inReplyToStatusId: String? = null,
        repostStatusId: String? = null,
        repostAuthor: String? = null,
    ): Result<StatusUi> = runCatching {
        // Live-verified 2026-10-19: statuses/update rejects a repost whose
        // status text is empty with 400 信息发送失败, although the wiki says the
        // comment is optional. Synthesize the minimal valid text here so every
        // caller (composer, detail dialog) shares one rule.
        val body = text.trim().ifEmpty {
            when {
                repostStatusId == null -> text.trim()
                repostAuthor.isNullOrBlank() -> "转发"
                else -> "转@$repostAuthor"
            }
        }
        val dto = statusesApi.update(
            status = body,
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

    /**
     * Deletes one of the current user's statuses. Remote first: a failure keeps
     * the local cache intact, so the UI never shows a status as gone when the
     * server still has it.
     *
     * Once the server has deleted it the outcome is irreversible, so the three
     * local rows are removed in one transaction and under [NonCancellable]: if
     * the user leaves the screen mid-flight, cancelling the coroutine must not
     * leave the cache pointing at a status the server no longer has.
     */
    suspend fun delete(statusId: String): Result<Unit> = runCatching {
        statusesApi.destroy(statusId)
        withContext(NonCancellable) {
            db.withTransaction {
                statusDao.deleteTimelineEntries(statusId)
                statusDao.deleteFavoriteOverride(statusId)
                statusDao.deleteStatus(statusId)
            }
        }
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
