package com.kansen.fanfan.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kansen.fanfan.core.database.entity.FavoriteOverrideEntity
import com.kansen.fanfan.core.database.entity.StatusEntity
import com.kansen.fanfan.core.database.entity.StatusRow
import com.kansen.fanfan.core.database.entity.TimelineEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusDao {

    @Upsert
    suspend fun upsertStatuses(statuses: List<StatusEntity>)

    @Upsert
    suspend fun upsertEntries(entries: List<TimelineEntryEntity>)

    @Upsert
    suspend fun upsertFavoriteOverride(override: FavoriteOverrideEntity)

    @Query(
        """
        SELECT s.*, f.favorited AS favoritedOverride
        FROM status s
        JOIN timeline_entry t ON t.statusId = s.id
        LEFT JOIN favorite_override f ON f.statusId = s.id
        WHERE t.timelineType = :timelineType
        ORDER BY t.rawid DESC
        LIMIT :limit
        """,
    )
    fun timeline(timelineType: String, limit: Int = TIMELINE_LIMIT): Flow<List<StatusRow>>

    @Query("SELECT statusId FROM timeline_entry WHERE timelineType = :timelineType ORDER BY rawid ASC LIMIT 1")
    suspend fun oldestId(timelineType: String): String?

    @Query("DELETE FROM timeline_entry WHERE statusId = :id")
    suspend fun deleteTimelineEntries(id: String)

    @Query("DELETE FROM status WHERE id = :id")
    suspend fun deleteStatus(id: String)

    @Query("DELETE FROM favorite_override WHERE statusId = :id")
    suspend fun deleteFavoriteOverride(id: String)

    @Query(
        """
        SELECT s.*, f.favorited AS favoritedOverride
        FROM status s
        LEFT JOIN favorite_override f ON f.statusId = s.id
        WHERE s.id = :id
        """,
    )
    suspend fun statusById(id: String): StatusRow?

    @Query(
        """
        SELECT s.*, f.favorited AS favoritedOverride
        FROM status s
        LEFT JOIN favorite_override f ON f.statusId = s.id
        WHERE s.id IN (:ids)
        """,
    )
    suspend fun statusByIds(ids: List<String>): List<StatusRow>

    @Query(
        """
        SELECT s.*, f.favorited AS favoritedOverride
        FROM status s
        LEFT JOIN favorite_override f ON f.statusId = s.id
        WHERE s.id = :id
        """,
    )
    fun statusFlow(id: String): Flow<StatusRow?>


    /** Keeps the newest [keep] entries of a timeline (memory bound, design D2). */
    @Query(
        """
        DELETE FROM timeline_entry WHERE timelineType = :timelineType AND statusId NOT IN (
            SELECT statusId FROM timeline_entry WHERE timelineType = :timelineType ORDER BY rawid DESC LIMIT :keep
        )
        """,
    )
    suspend fun trim(timelineType: String, keep: Int = TIMELINE_LIMIT)

    /**
     * Drops status rows no longer referenced by any timeline, favorite overlay,
     * or reply — reply parents are cached without a timeline of their own, so
     * the `NOT IN` subquery must never see a NULL (it would delete nothing).
     */
    @Query(
        """
        DELETE FROM status WHERE id NOT IN (SELECT statusId FROM timeline_entry)
        AND id NOT IN (SELECT statusId FROM favorite_override)
        AND id NOT IN (SELECT inReplyToStatusId FROM status WHERE inReplyToStatusId IS NOT NULL)
        """,
    )
    suspend fun pruneOrphans()

    companion object {
        const val TIMELINE_LIMIT = 2000
    }
}
