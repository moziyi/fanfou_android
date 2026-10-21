package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.datastore.TokenStore
import com.kansen.fanfan.core.model.DmConversationUi
import com.kansen.fanfan.core.model.DmMapper
import com.kansen.fanfan.core.model.DmUi
import com.kansen.fanfan.core.network.api.DmApi
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun conversation(otherId: String, page: Int = 1, maxId: String? = null): List<DmUi> {
        val selfId = selfId()
        return api.conversation(id = otherId, page = page, maxId = maxId)
            .mapNotNull { DmMapper.toUi(it, selfId) }
    }

    /** Name and avatar of the other side, taken from the conversation itself. */
    suspend fun peerOf(otherId: String): DmMapper.DmPeer? {
        val selfId = selfId()
        return api.conversation(id = otherId, count = 1, mode = "lite")
            .firstOrNull()
            ?.let { DmMapper.peer(it, selfId) }
    }

    suspend fun send(otherId: String, text: String, inReplyToId: String? = null): DmUi {
        val selfId = selfId()
        val sent = api.send(user = otherId, text = text, inReplyToId = inReplyToId)
        return DmMapper.toUi(sent, selfId)
            ?: error("发送成功但返回内容无法解析")
    }

    private suspend fun selfId(): String = tokenStore.session.first()?.user?.id.orEmpty()
}
