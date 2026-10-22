package com.kansen.fanfan.core.network.api

import com.kansen.fanfan.core.network.dto.StatusDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Live-verified 2026-10-19: the wiki documents `favorites/create` with an `id`
 * form/query parameter, but the live API silently no-ops and returns `[]` for
 * that shape. The path-style `favorites/create/{id}.json` works and returns the
 * status object with `favorited` set.
 */
interface FavoritesApi {

    @POST("favorites/create/{id}.json")
    suspend fun create(@Path("id") id: String): StatusDto

    @POST("favorites/destroy/{id}.json")
    suspend fun destroy(@Path("id") id: String): StatusDto

    /**
     * The favorites of the logged-in user, newest first. The list is private:
     * the endpoint always answers with the favorites of the token owner, so it
     * only makes sense on one's own profile.
     */
    @GET("favorites/list.json")
    suspend fun list(
        @Query("count") count: Int = 30,
        @Query("page") page: Int = 1,
    ): List<StatusDto>
}
