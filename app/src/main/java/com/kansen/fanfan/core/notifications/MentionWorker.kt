package com.kansen.fanfan.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kansen.fanfan.core.datastore.MentionStateStore
import com.kansen.fanfan.core.datastore.TokenStore
import com.kansen.fanfan.core.network.api.StatusesApi
import com.kansen.fanfan.data.repository.StatusStore
import com.kansen.fanfan.data.repository.TimelineType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Polls mentions every 15 minutes (WorkManager's minimum) and posts system
 * notifications for statuses newer than the local watermark.
 *
 * Live-verified 2026-10-20: fetching `statuses/mentions` also clears the unread
 * counter on the server, which is unavoidable if the notification is to carry
 * the author and the text — see design D1.
 */
@HiltWorker
class MentionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: StatusesApi,
    private val tokenStore: TokenStore,
    private val state: MentionStateStore,
    private val store: StatusStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (tokenStore.session.first()?.token == null) return Result.success()

        val page = runCatching { api.mentions(count = PAGE_SIZE) }.getOrElse { return Result.retry() }
        val newest = MentionRules.newestRawid(page)
        val notifiedUpTo = state.notifiedUpTo.first()

        // Keep the local cache in step with the server, so the badge on the @我
        // tab is meaningful even when the tab was never opened. This does not
        // count as reading: the read watermark only moves when the user opens
        // the tab.
        if (page.isNotEmpty()) {
            store.saveTimeline(page, TimelineType.MENTIONS.key)
            store.trim(TimelineType.MENTIONS.key)
        }

        // First run: treat everything already on the server as seen, otherwise
        // installing the app would fire a notification for every old mention.
        if (notifiedUpTo == 0L) {
            state.markNotified(newest)
            return Result.success()
        }

        val fresh = MentionRules.freshMentions(page, notifiedUpTo)
        if (fresh.isNotEmpty()) {
            MentionNotifications.show(applicationContext, fresh)
            state.markNotified(maxOf(newest, fresh.maxOf { it.rawid ?: 0L }))
        }
        return Result.success()
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
