package com.kansen.fanfan.core.model

import com.kansen.fanfan.core.network.dto.DmConversationDto
import com.kansen.fanfan.core.network.dto.DmDto

/**
 * DTO → UI mapping for direct messages. Kept out of the repository so the
 * rules (which side is "mine", whose name to show) can be unit-tested.
 */
object DmMapper {

    /** The counterparty of a message: sender when received, recipient when sent. */
    data class DmPeer(
        val id: String,
        val name: String,
        val avatarUrl: String?,
    )

    fun toUi(dto: DmDto, selfId: String): DmUi? {
        val dmId = dto.id ?: return null
        val from = dto.sender_id.orEmpty()
        val fromUser = dto.sender
        return DmUi(
            id = dmId,
            text = dto.text.orEmpty(),
            senderId = from,
            recipientId = dto.recipient_id.orEmpty(),
            senderName = dto.sender_screen_name?.takeIf { it.isNotBlank() }
                ?: fromUser?.screen_name
                ?: fromUser?.name
                ?: "未知用户",
            senderAvatarUrl = fromUser?.profile_image_url_large?.takeIf { it.isNotBlank() }
                ?: fromUser?.profile_image_url?.takeIf { it.isNotBlank() },
            createdAt = ApiTime.parseEpochMillis(dto.created_at),
            isMine = from.isNotBlank() && from == selfId,
        )
    }

    /**
     * The other side of a message. A conversation row shows the counterparty,
     * so the name and avatar must come from the party that is *not* the
     * logged-in account — a message we sent shows the recipient.
     */
    fun peer(dto: DmDto, selfId: String): DmPeer? {
        val senderId = dto.sender_id.orEmpty()
        val recipientId = dto.recipient_id.orEmpty()
        val senderUser = dto.sender
        val recipientUser = dto.recipient
        return when {
            senderId.isNotBlank() && senderId != selfId -> DmPeer(
                id = senderId,
                name = dto.sender_screen_name?.takeIf { it.isNotBlank() }
                    ?: senderUser?.screen_name
                    ?: senderUser?.name
                    ?: senderId,
                avatarUrl = senderUser?.profile_image_url_large?.takeIf { it.isNotBlank() }
                    ?: senderUser?.profile_image_url?.takeIf { it.isNotBlank() },
            )

            recipientId.isNotBlank() && recipientId != selfId -> DmPeer(
                id = recipientId,
                name = dto.recipient_screen_name?.takeIf { it.isNotBlank() }
                    ?: recipientUser?.screen_name
                    ?: recipientUser?.name
                    ?: recipientId,
                avatarUrl = recipientUser?.profile_image_url_large?.takeIf { it.isNotBlank() }
                    ?: recipientUser?.profile_image_url?.takeIf { it.isNotBlank() },
            )

            else -> null
        }
    }

    fun toConversationUi(dto: DmConversationDto, selfId: String): DmConversationUi? {
        val other = dto.otherid?.takeIf { it.isNotBlank() } ?: return null
        val dm = dto.dm ?: return null
        val peer = peer(dm, selfId)
        // The server flag `new_conv` tracks the *other* side's read state
        // (it drops to false once they read the thread), so it cannot say
        // what the local user has not seen yet. The UI derives unread from
        // the last message plus a local read marker instead.
        val lastFromOther = dm.sender_id?.takeIf { it.isNotBlank() } != selfId
        return DmConversationUi(
            otherId = other,
            otherName = peer?.name ?: other,
            otherAvatarUrl = peer?.avatarUrl,
            lastText = dm.text.orEmpty(),
            lastCreatedAt = ApiTime.parseEpochMillis(dm.created_at),
            messageCount = dto.msg_num ?: 0,
            lastMessageId = dm.id.orEmpty(),
            lastFromOther = lastFromOther,
        )
    }
}
