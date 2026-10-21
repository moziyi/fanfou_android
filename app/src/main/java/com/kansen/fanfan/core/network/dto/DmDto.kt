package com.kansen.fanfan.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Fanfou direct message, 1:1 with the live payload (verified 2026-10-21).
 *
 * Live shape:
 * ```json
 * {"id":"13990207","text":"…","sender_id":"fanfou","recipient_id":"turancao",
 *  "created_at":"Mon Dec 04 15:12:55 +0000 2023","sender_screen_name":"饭否",
 *  "recipient_screen_name":"徒然草","sender":{…},"recipient":{…}}
 * ```
 */
@Serializable
data class DmDto(
    val id: String? = null,
    val text: String? = null,
    val sender_id: String? = null,
    val recipient_id: String? = null,
    val created_at: String? = null,
    val sender_screen_name: String? = null,
    val recipient_screen_name: String? = null,
    val sender: UserDto? = null,
    val recipient: UserDto? = null,
    val in_reply_to_id: String? = null,
    val is_new: Boolean? = null,
)

/**
 * One entry of `/direct_messages/conversation_list.json`:
 * `{"dm":{…},"otherid":"fanfou","msg_num":18,"new_conv":false}`.
 *
 * `new_conv` marks a conversation with unread messages; `msg_num` is the
 * total number of messages in the conversation.
 */
@Serializable
data class DmConversationDto(
    val dm: DmDto? = null,
    val otherid: String? = null,
    val msg_num: Int? = null,
    val new_conv: Boolean? = null,
)
