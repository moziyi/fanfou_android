package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.database.dao.StatusDao
import com.kansen.fanfan.core.datastore.MentionStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Badge and read state for the @我 tab. The mentions themselves live in Room
 * under [TimelineType.MENTIONS], so the count is a local query and works
 * offline.
 */
@Singleton
class MentionRepository @Inject constructor(
    private val statusDao: StatusDao,
    private val state: MentionStateStore,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadCount: Flow<Int> = state.readUpTo.flatMapLatest { rawid ->
        statusDao.unreadCount(TimelineType.MENTIONS.key, rawid)
    }

    /** Call after a successful refresh so the newest rawid seen is counted as read. */
    suspend fun markRead() {
        val newest = statusDao.maxRawid(TimelineType.MENTIONS.key) ?: return
        state.markRead(newest)
    }
}
