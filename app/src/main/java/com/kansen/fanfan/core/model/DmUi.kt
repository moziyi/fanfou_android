package com.kansen.fanfan.core.model

/**
 * A direct message as the UI needs it. `isMine` is resolved against the
 * logged-in account, so the bubble side never has to guess.
 */
data class DmUi(
    val id: String,
    val text: String,
    val senderId: String,
    val recipientId: String,
    val senderName: String,
    val senderAvatarUrl: String?,
    val createdAt: Long,
    val isMine: Boolean,
) {
    val time: String get() = ApiTime.relative(createdAt)
}

/** One row of the conversation list. */
data class DmConversationUi(
    val otherId: String,
    val otherName: String,
    val otherAvatarUrl: String?,
    val lastText: String,
    val lastCreatedAt: Long,
    val messageCount: Int,
    /** Id of the last message in the conversation. */
    val lastMessageId: String,
    /** True when the last message was written by the other side. */
    val lastFromOther: Boolean,
) {
    val time: String get() = ApiTime.relative(lastCreatedAt)
}
