package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.database.dao.DraftDao
import com.kansen.fanfan.core.database.entity.DraftEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Persists composer drafts so closing the screen never loses text. */
@Singleton
class DraftRepository @Inject constructor(
    private val draftDao: DraftDao,
) {

    suspend fun load(draftId: String): DraftEntity? = draftDao.get(draftId)

    /** Every draft, the most recently changed first. */
    fun observe(): Flow<List<DraftEntity>> = draftDao.list()

    suspend fun save(draftId: String, text: String, inReplyToStatusId: String?, repostStatusId: String?) {
        if (text.isBlank()) {
            draftDao.delete(draftId)
        } else {
            draftDao.upsert(
                DraftEntity(
                    id = draftId,
                    text = text,
                    inReplyToStatusId = inReplyToStatusId,
                    repostStatusId = repostStatusId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun clear(draftId: String) = draftDao.delete(draftId)
}
