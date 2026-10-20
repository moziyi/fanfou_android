package com.kansen.fanfan.core.network.api

import com.kansen.fanfan.core.network.dto.StatusDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Timeline / status endpoints. `mode=lite` trims per-user custom profile
 * fields; avatars and display fields are kept, payload is materially smaller.
 */
interface StatusesApi {

    @GET("statuses/home_timeline.json")
    suspend fun homeTimeline(
        @Query("since_id") sinceId: String? = null,
        @Query("max_id") maxId: String? = null,
        @Query("count") count: Int = 30,
        @Query("page") page: Int? = null,
        @Query("mode") mode: String = "lite",
    ): List<StatusDto>

    @GET("statuses/public_timeline.json")
    suspend fun publicTimeline(
        @Query("since_id") sinceId: String? = null,
        @Query("max_id") maxId: String? = null,
        @Query("count") count: Int = 30,
        @Query("page") page: Int? = null,
        @Query("mode") mode: String = "lite",
    ): List<StatusDto>

    @GET("statuses/show.json")
    suspend fun show(
        @Query("id") id: String,
        @Query("mode") mode: String = "lite",
    ): StatusDto

    /** The thread around a status (in_reply_to chain + replies). */
    @GET("statuses/context_timeline.json")
    suspend fun contextTimeline(
        @Query("id") id: String,
        @Query("mode") mode: String = "lite",
    ): List<StatusDto>

    /**
     * Publishes a status. Reply: set [inReplyToStatusId]. Repost: set
     * [repostStatusId].
     *
     * Live-verified 2026-10-19: unlike the wiki (`status` optional for
     * reposts), the server rejects a blank `status` with 400; callers go
     * through `StatusRepository.post`, which synthesizes the minimal text.
     */
    @FormUrlEncoded
    @POST("statuses/update.json")
    suspend fun update(
        @Field("status") status: String,
        @Field("in_reply_to_status_id") inReplyToStatusId: String? = null,
        @Field("repost_status_id") repostStatusId: String? = null,
        @Field("location") location: String? = null,
    ): StatusDto

    /** Deletes a status. Live-verified 2026-10-19: returns the destroyed status. */
    @FormUrlEncoded
    @POST("statuses/destroy.json")
    suspend fun destroy(@Field("id") id: String): StatusDto
}
