package com.kansen.fanfan.core.network.api

import com.kansen.fanfan.core.network.dto.StatusDto
import retrofit2.http.POST
import retrofit2.http.Path

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
}
