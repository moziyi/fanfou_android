package com.kansen.fanfan.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

/**
 * Immutable raw snapshot of a status. `rawJson` is the untouched API payload;
 * the other columns exist only for indexing/sorting and are re-derivable.
 */
@Entity(
    tableName = "status",
    indices = [Index("userId"), Index("rawid")],
)
data class StatusEntity(
    @androidx.room.PrimaryKey val id: String,
    val rawid: Long,
    val rawJson: String,
    val userId: String?,
    val createdAtEpoch: Long,
    /** Index column: queried for a reply's parent, and keeps that parent from being pruned. */
    val inReplyToStatusId: String? = null,
)

/** Many-to-many membership of a status in a timeline. */
@Entity(
    tableName = "timeline_entry",
    primaryKeys = ["timelineType", "statusId"],
    indices = [Index(value = ["timelineType", "rawid"])],
)
data class TimelineEntryEntity(
    val timelineType: String,
    val statusId: String,
    val rawid: Long,
)

/**
 * Local override of data that is relative to the current user (favorited).
 * Raw status JSON is never rewritten (design §5.3).
 */
@Entity(tableName = "favorite_override")
data class FavoriteOverrideEntity(
    @androidx.room.PrimaryKey val statusId: String,
    val favorited: Boolean,
)

/** Query projection: status + effective favorite state after overlay. */
data class StatusRow(
    @Embedded val status: StatusEntity,
    val favoritedOverride: Boolean?,
)

/** A timeline entry with the age of the status behind it. */
data class TimelineEntryRow(
    val statusId: String,
    val rawid: Long,
    val createdAtEpoch: Long,
)
