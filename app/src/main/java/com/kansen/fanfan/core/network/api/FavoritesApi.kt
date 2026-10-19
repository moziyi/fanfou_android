package com.kansen.fanfan.core.network.api

import com.kansen.fanfan.core.network.dto.StatusDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface FavoritesApi {

    @FormUrlEncoded
    @POST("favorites/create.json")
    suspend fun create(@Field("id") id: String): StatusDto

    @FormUrlEncoded
    @POST("favorites/destroy.json")
    suspend fun destroy(@Field("id") id: String): StatusDto
}
