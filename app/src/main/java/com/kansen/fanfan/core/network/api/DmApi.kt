package com.kansen.fanfan.core.network.api

import com.kansen.fanfan.core.network.dto.DmConversationDto
import com.kansen.fanfan.core.network.dto.DmDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Direct message endpoints.
 *
 * Live-verified 2026-10-21: the current consumer key *does* have direct
 * message permission (the doc says "API 需要有读用户私信的权限" — it does).
 * Sending uses the documented form style (`user` + `text`), unlike the
 * favorites endpoints which need the path style.
 */
interface DmApi {

    /** One row per conversation, newest message first. */
    @GET("direct_messages/conversation_list.json")
    suspend fun conversations(
        @Query("count") count: Int = 30,
        @Query("page") page: Int = 1,
        @Query("mode") mode: String = "lite",
    ): List<DmConversationDto>

    /** All messages exchanged with one user. */
    @GET("direct_messages/conversation.json")
    suspend fun conversation(
        @Query("id") id: String,
        @Query("count") count: Int = 30,
        @Query("page") page: Int = 1,
        @Query("max_id") maxId: String? = null,
        @Query("mode") mode: String = "lite",
    ): List<DmDto>

    @FormUrlEncoded
    @POST("direct_messages/new.json")
    suspend fun send(
        @Field("user") user: String,
        @Field("text") text: String,
        @Field("in_reply_to_id") inReplyToId: String? = null,
    ): DmDto
}
