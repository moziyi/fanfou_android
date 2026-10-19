package com.kansen.fanfan.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Fanfou status object, 1:1 with the API payload. All fields nullable by
 * design: the doc is from 2012, live payloads drift, and unknown keys are
 * ignored. The raw JSON is persisted as-is for replayable storage.
 */
@Serializable
data class StatusDto(
    val id: String? = null,
    val rawid: Long? = null,
    val created_at: String? = null,
    val text: String? = null,
    val source: String? = null,
    val truncated: Boolean? = null,
    val favorited: Boolean? = null,
    val in_reply_to_status_id: String? = null,
    val in_reply_to_user_id: String? = null,
    val in_reply_to_screen_name: String? = null,
    val repost_status_id: String? = null,
    val repost_status: StatusDto? = null,
    val repost_user_id: String? = null,
    val repost_screen_name: String? = null,
    val photo: PhotoDto? = null,
    val location: String? = null,
    val user: UserDto? = null,
)

@Serializable
data class PhotoDto(
    val imageurl: String? = null,
    val thumburl: String? = null,
    val largeurl: String? = null,
)
