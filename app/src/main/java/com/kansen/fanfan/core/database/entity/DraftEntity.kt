package com.kansen.fanfan.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Composer draft. `id` scopes drafts: "composer" for a fresh status,
 * "reply:{statusId}" and "repost:{statusId}" for contextual drafts.
 */
@Entity(tableName = "draft")
data class DraftEntity(
    @PrimaryKey val id: String,
    val text: String,
    val inReplyToStatusId: String?,
    val repostStatusId: String?,
    val updatedAt: Long,
)
