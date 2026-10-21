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
import com.kansen.fanfan.core.common.runCatchingCancellable
import com.kansen.fanfan.core.network.api.PhotosApi
import com.kansen.fanfan.data.media.PreparedPhoto
import com.kansen.fanfan.data.media.ProgressRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Write path: publish / reply / repost / favorite / delete. Network first, then
 * update the local cache; failures surface to the UI (no offline queue, design D5).
 */
@Singleton
class StatusRepository @Inject constructor(
    private val statusesApi: StatusesApi,
    private val favoritesApi: FavoritesApi,
    private val photosApi: PhotosApi,
    private val statusDao: StatusDao,
    private val db: FanfanDatabase,
    private val store: StatusStore,
) {

    suspend fun post(
        text: String,
        inReplyToStatusId: String? = null,
        repostStatusId: String? = null,
        repostAuthor: String? = null,
    ): Result<StatusUi> = runCatchingCancellable {
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

    /**
     * Posts a status with a picture.
     *
     * Live-verified 2026-10-21: `photos/upload` creates the status itself —
     * the picture and the text go in one multipart request and the answer is
     * the new status. The endpoint rejects an empty text with
     * 400 「上传照片失败」, so the default text the server uses itself is sent
     * when the composer has none.
     */
    suspend fun postPhoto(
        text: String,
        photo: PreparedPhoto,
        onProgress: (Float) -> Unit = {},
    ): Result<StatusUi> = runCatchingCancellable {
        val body = text.trim().ifEmpty { DEFAULT_PHOTO_TEXT }
        val file = ProgressRequestBody(
            bytes = photo.bytes,
            mediaType = photo.mimeType.toMediaType(),
            onProgress = onProgress,
        )
        val part = MultipartBody.Part.createFormData("photo", photo.fileName, file)
        val status = body.toRequestBody("text/plain; charset=utf-8".toMediaType())
        val dto = photosApi.upload(part, status)
        store.saveTimeline(listOf(dto), TimelineType.HOME.key)
        dto.toUi()
    }

    /** Flips the favorite state and records it in the local overlay table. */
    suspend fun toggleFavorite(status: StatusUi): Result<Boolean> = runCatchingCancellable {
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
    suspend fun delete(statusId: String): Result<Unit> = runCatchingCancellable {
        statusesApi.destroy(statusId)
        withContext(NonCancellable) {
            db.withTransaction {
                statusDao.deleteTimelineEntries(statusId)
                statusDao.deleteFavoriteOverride(statusId)
                statusDao.deleteStatus(statusId)
            }
        }
    }

    private companion object {
        /** The text the server uses itself when a photo is posted without one. */
        const val DEFAULT_PHOTO_TEXT = "上传了新照片"
    }

    fun status(id: String): Flow<StatusUi?> =
        statusDao.statusFlow(id).map { row -> row?.let(StatusMapper::toUi) }

    /** Refreshes one status from the server into the local cache. */
    suspend fun refreshStatus(id: String): Result<Unit> = runCatchingCancellable {
        val dto = statusesApi.show(id)
        store.saveBare(listOf(dto))
    }

    /** Thread context around a status, newest first, as display models. */
    suspend fun context(id: String): Result<List<StatusUi>> = runCatchingCancellable {
        val statuses = statusesApi.contextTimeline(id)
        store.saveBare(statuses)
        statuses.map { it.toUi() }.sortedBy { it.rawid }
    }
}
