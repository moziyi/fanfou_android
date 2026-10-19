package com.kansen.fanfan.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kansen.fanfan.core.database.entity.UserEntity

@Dao
interface UserDao {

    @Upsert
    suspend fun upsertUsers(users: List<UserEntity>)

    @Query("SELECT * FROM user WHERE id = :id")
    suspend fun byId(id: String): UserEntity?
}
