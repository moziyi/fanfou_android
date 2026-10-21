package com.kansen.fanfan.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kansen.fanfan.core.datastore.DmStateStore
import com.kansen.fanfan.core.datastore.MentionStateStore
import com.kansen.fanfan.core.datastore.TokenStore
import com.kansen.fanfan.core.model.DmMapper
import com.kansen.fanfan.core.model.DmUnread
import com.kansen.fanfan.core.network.api.DmApi
import com.kansen.fanfan.core.network.api.StatusesApi
import com.kansen.fanfan.data.repository.StatusStore
import com.kansen.fanfan.data.repository.TimelineType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Polls the account every 15 minutes (WorkManager's minimum):
 *
 * - mentions: posts system notifications for statuses newer than the local
 *   watermark and keeps the @我 cache fresh;
 * - direct messages: refreshes the unread-conversation count for the 私信 tab
 *   badge.
 *
 * The two are independent: a failing mention request must not stop the
 * unread count from being refreshed.
 *
 * Live-verified 2026-10-20: fetching `statuses/mentions` also clears the unread
 * counter on the server, which is unavoidable if the notification is to carry
 * the author and the text — see design D1.
 */
@HiltWorker
class FanfouPollWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: StatusesApi,
    private val tokenStore: TokenStore,
    private val state: MentionStateStore,
    private val store: StatusStore,
    private val dmApi: DmApi,
    private val dmState: DmStateStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val session = tokenStore.session.first() ?: return Result.success()
        if (session.token.isNullOrBlank()) return Result.success()

        val mentions = runCatching { api.mentions(count = PAGE_SIZE) }
        mentions.onSuccess { page ->
            val newest = MentionRules.newestRawid(page)
            val notifiedUpTo = state.notifiedUpTo.first()

            // Keep the local cache in step with the server, so the badge on
            // the @我 tab is meaningful even when the tab was never opened.
            // This does not count as reading: the read watermark only moves
            // when the user opens the tab.
            if (page.isNotEmpty()) {
                store.saveTimeline(page, TimelineType.MENTIONS.key)
                store.trim(TimelineType.MENTIONS.key)
            }

            // First run: treat everything already on the server as seen,
            // otherwise installing the app would fire a notification for
            // every old mention.
            if (notifiedUpTo == 0L) {
                state.markNotified(newest)
                return@onSuccess
            }

            val fresh = MentionRules.freshMentions(page, notifiedUpTo)
            if (fresh.isNotEmpty()) {
                MentionNotifications.show(applicationContext, fresh)
                state.markNotified(maxOf(newest, fresh.maxOf { it.rawid ?: 0L }))
            }
        }

        refreshUnreadConversations()

        return if (mentions.isSuccess) Result.success() else Result.retry()
    }

    /**
     * The 私信 tab badge counts conversations the user has not read yet. The
     * same rule as the conversation list ([DmUnread]) is used here, otherwise
     * the two writers would disagree about the same number.
     */
    private suspend fun refreshUnreadConversations() {
        val selfId = tokenStore.session.first()?.user?.id.orEmpty()
        runCatching {
            dmApi.conversations(count = DM_PAGE_SIZE)
                .mapNotNull { DmMapper.toConversationUi(it, selfId) }
        }.onSuccess { conversations ->
            val markers = dmState.readMarkers.first()
            dmState.setUnreadConversations(DmUnread.evaluate(conversations, markers).size)
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val DM_PAGE_SIZE = 30
    }
}
