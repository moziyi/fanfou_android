package com.kansen.fanfan.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Raw user snapshot; `screenName` is a redundant column for cheap lookups. */
@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey val id: String,
    val rawJson: String,
    val screenName: String?,
    val updatedAt: Long,
)
