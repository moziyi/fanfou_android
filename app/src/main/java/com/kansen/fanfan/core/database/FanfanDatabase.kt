package com.kansen.fanfan.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kansen.fanfan.core.database.dao.DraftDao
import com.kansen.fanfan.core.database.dao.StatusDao
import com.kansen.fanfan.core.database.dao.UserDao
import com.kansen.fanfan.core.database.entity.DraftEntity
import com.kansen.fanfan.core.database.entity.FavoriteOverrideEntity
import com.kansen.fanfan.core.database.entity.StatusEntity
import com.kansen.fanfan.core.database.entity.TimelineEntryEntity
import com.kansen.fanfan.core.database.entity.UserEntity

@Database(
    entities = [
        StatusEntity::class,
        TimelineEntryEntity::class,
        FavoriteOverrideEntity::class,
        UserEntity::class,
        DraftEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class FanfanDatabase : RoomDatabase() {
    abstract fun statusDao(): StatusDao
    abstract fun userDao(): UserDao
    abstract fun draftDao(): DraftDao
}
