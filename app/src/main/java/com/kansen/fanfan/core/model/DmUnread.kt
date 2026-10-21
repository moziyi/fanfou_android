package com.kansen.fanfan.core.model

/**
 * The rule that decides which conversations the user has not read yet.
 *
 * The server flag `new_conv` cannot be used: it reports the *other* side's
 * read state (it drops to false once they read the thread), not ours. So the
 * app keeps its own marker — the id of the last message seen in a
 * conversation — and a conversation is unread when
 *
 * 1. the last message was written by the other side, and
 * 2. that message is not the one we have already seen.
 *
 * Kept as a pure function so both the conversation list and the poll worker
 * use exactly the same definition.
 */
object DmUnread {

    fun evaluate(
        conversations: List<DmConversationUi>,
        readMarkers: Map<String, String>,
    ): Set<String> = conversations
        .filter { it.lastFromOther && it.lastMessageId.isNotBlank() }
        .filter { readMarkers[it.otherId] != it.lastMessageId }
        .map { it.otherId }
        .toSet()
}
