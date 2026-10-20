package com.kansen.fanfan.core.network.api

import com.kansen.fanfan.core.network.dto.UserDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * User / relationship endpoints.
 *
 * Live-verified 2026-10-20: unlike favorites, the documented form-parameter
 * style is correct here — `friendships/create.json` and `destroy.json` with an
 * `id` form field both answer 200 and return the target user with the new
 * `following` value (create → true, destroy → false).
 */
interface UsersApi {

    @GET("users/show.json")
    suspend fun show(
        @Query("id") id: String,
        @Query("mode") mode: String = "lite",
    ): UserDto

    /** Users the target follows; [id] omitted means the logged-in user. */
    @GET("users/friends.json")
    suspend fun friends(
        @Query("id") id: String? = null,
        @Query("count") count: Int = 30,
        @Query("page") page: Int = 1,
        @Query("mode") mode: String = "lite",
    ): List<UserDto>

    /** Users that follow the target; [id] omitted means the logged-in user. */
    @GET("users/followers.json")
    suspend fun followers(
        @Query("id") id: String? = null,
        @Query("count") count: Int = 30,
        @Query("page") page: Int = 1,
        @Query("mode") mode: String = "lite",
    ): List<UserDto>

    @FormUrlEncoded
    @POST("friendships/create.json")
    suspend fun follow(@Field("id") id: String): UserDto

    @FormUrlEncoded
    @POST("friendships/destroy.json")
    suspend fun unfollow(@Field("id") id: String): UserDto
}
