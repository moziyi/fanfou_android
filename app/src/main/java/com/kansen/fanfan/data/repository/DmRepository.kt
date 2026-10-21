package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.datastore.TokenStore
import com.kansen.fanfan.core.model.DmConversationUi
import com.kansen.fanfan.core.model.DmMapper
import com.kansen.fanfan.core.model.DmUi
import com.kansen.fanfan.core.network.api.DmApi
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** One page of a conversation: the messages plus the other side. */
data class DmThread(
    val messages: List<DmUi>,
    val peer: DmMapper.DmPeer?,
)

/**
 * Direct messages.
 *
 * Deliberately without a Room cache: direct messages are low volume and
 * strongly time-bound, so the pages keep them in memory and refetch on entry
 * (see .doc/M3私信设计方案-2026-10-21.md §3.1).
 */
@Singleton
class DmRepository @Inject constructor(
    private val api: DmApi,
    private val tokenStore: TokenStore,
) {

    suspend fun conversations(page: Int = 1): List<DmConversationUi> {
        val selfId = selfId()
        return api.conversations(page = page).mapNotNull { DmMapper.toConversationUi(it, selfId) }
    }

    /**
     * One page of a conversation. The API returns the messages newest first
     * and paging with `page` yields strictly older ones (live-verified
     * 2026-10-21); `max_id` would include the boundary message again.
     */
    suspend fun conversation(otherId: String, page: Int = 1): DmThread {
        val selfId = selfId()
        val dtos = api.conversation(id = otherId, page = page)
        return DmThread(
            messages = dtos.mapNotNull { DmMapper.toUi(it, selfId) },
            peer = dtos.firstOrNull()?.let { DmMapper.peer(it, selfId) },
        )
    }

    suspend fun send(otherId: String, text: String, inReplyToId: String? = null): DmUi {
        val selfId = selfId()
        val sent = api.send(user = otherId, text = text, inReplyToId = inReplyToId)
        return DmMapper.toUi(sent, selfId)
            ?: error("发送成功但返回内容无法解析")
    }

    private suspend fun selfId(): String = tokenStore.session.first()?.user?.id.orEmpty()
}
