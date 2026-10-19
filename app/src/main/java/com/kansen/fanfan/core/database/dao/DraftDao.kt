package com.kansen.fanfan.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kansen.fanfan.core.database.entity.DraftEntity

@Dao
interface DraftDao {

    @Query("SELECT * FROM draft WHERE id = :id")
    suspend fun get(id: String): DraftEntity?

    @Upsert
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM draft WHERE id = :id")
    suspend fun delete(id: String)
}
