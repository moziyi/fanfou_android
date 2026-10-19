package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.common.JsonCodec
import com.kansen.fanfan.core.database.dao.StatusDao
import com.kansen.fanfan.core.database.dao.UserDao
import com.kansen.fanfan.core.database.entity.StatusEntity
import com.kansen.fanfan.core.database.entity.TimelineEntryEntity
import com.kansen.fanfan.core.database.entity.UserEntity
import com.kansen.fanfan.core.model.ApiTime
import com.kansen.fanfan.core.network.dto.StatusDto
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single write path into the status cache: raw JSON is stored verbatim, the
 * index columns are derived from it, and the embedded user is split into the
 * user table. Timeline membership is separate from the status row so one
 * status can live in several timelines.
 */
@Singleton
class StatusStore @Inject constructor(
    private val statusDao: StatusDao,
    private val userDao: UserDao,
) {

    suspend fun saveTimeline(statuses: List<StatusDto>, timelineType: String) {
        if (statuses.isEmpty()) return
        val rows = ArrayList<StatusEntity>(statuses.size)
        val entries = ArrayList<TimelineEntryEntity>(statuses.size)

        statuses.forEach { dto ->
            val entity = dto.toEntity() ?: return@forEach
            rows += entity
            entries += TimelineEntryEntity(timelineType, entity.id, entity.rawid)
        }

        statusDao.upsertStatuses(rows)
        statusDao.upsertEntries(entries)
        saveUsers(statuses)
    }

    /** Saves status/user rows without adding them to any timeline (detail, reply parent). */
    suspend fun saveBare(statuses: List<StatusDto>) {
        val rows = statuses.mapNotNull { it.toEntity() }
        if (rows.isNotEmpty()) statusDao.upsertStatuses(rows)
        saveUsers(statuses)
    }

    suspend fun trim(timelineType: String) {
        statusDao.trim(timelineType)
        statusDao.pruneOrphans()
    }

    private suspend fun saveUsers(statuses: List<StatusDto>) {
        val now = System.currentTimeMillis()
        val users = statuses.mapNotNull { dto ->
            val user = dto.user ?: return@mapNotNull null
            val userId = user.id ?: return@mapNotNull null
            UserEntity(
                id = userId,
                rawJson = JsonCodec.json.encodeToString(user),
                screenName = user.screen_name,
                updatedAt = now,
            )
        }
        if (users.isNotEmpty()) userDao.upsertUsers(users)
    }

    /** The raw payload is the source of truth; index columns are re-derivable from it. */
    private fun StatusDto.toEntity(): StatusEntity? {
        val statusId = id ?: return null
        val createdAt = ApiTime.parseEpochMillis(created_at)
        return StatusEntity(
            id = statusId,
            rawid = rawid ?: createdAt,
            rawJson = JsonCodec.json.encodeToString(this),
            userId = user?.id,
            createdAtEpoch = createdAt,
            inReplyToStatusId = in_reply_to_status_id?.takeIf { it.isNotBlank() },
        )
    }
}
