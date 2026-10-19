package com.kansen.fanfan.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Fanfou user object, 1:1 with the API payload. All fields nullable: the API
 * doc is from 2012 and the live payload may drift (unknown keys are ignored).
 * Raw JSON is persisted by the caller for replayable storage.
 */
@Serializable
data class UserDto(
    val id: String? = null,
    val name: String? = null,
    val screen_name: String? = null,
    val location: String? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val description: String? = null,
    val profile_image_url: String? = null,
    val profile_image_url_large: String? = null,
    val url: String? = null,
    val protected: Boolean? = null,
    val followers_count: Int? = null,
    val friends_count: Int? = null,
    val favourites_count: Int? = null,
    val statuses_count: Int? = null,
    val following: Boolean? = null,
    val notifications: Boolean? = null,
    val created_at: String? = null,
    val utc_offset: Long? = null,
)

/** Standard fanfou error envelope: {"request": .., "error": ..} */
@Serializable
data class ApiErrorDto(
    val request: String? = null,
    val error: String? = null,
)
