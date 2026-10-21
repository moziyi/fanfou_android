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

    companion object {
        /**
         * Items per page. Paging is offset based, so the page size has to be
         * the same everywhere: the repository, the view models and the
         * "end of list" check all derive from it.
         */
        const val PAGE_SIZE = 30
    }

    /** One row per conversation, newest message first. */
    @GET("direct_messages/conversation_list.json")
    suspend fun conversations(
        @Query("count") count: Int = PAGE_SIZE,
        @Query("page") page: Int = 1,
        @Query("mode") mode: String = "lite",
    ): List<DmConversationDto>

    /**
     * Messages exchanged with one user, newest first. Paging with `page`
     * yields strictly older messages (live-verified 2026-10-21); `max_id`
     * would repeat the boundary message, so it is not used.
     */
    @GET("direct_messages/conversation.json")
    suspend fun conversation(
        @Query("id") id: String,
        @Query("count") count: Int = PAGE_SIZE,
        @Query("page") page: Int = 1,
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
