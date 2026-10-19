package com.kansen.fanfan.core.network.api

import com.kansen.fanfan.core.network.dto.UserDto
import retrofit2.http.GET
import retrofit2.http.Query

/** Fanfou API resources under https://api.fanfou.com/ */
interface FanfouApi {

    /** Returns the logged-in user. Used right after login to persist self. */
    @GET("account/verify_credentials.json")
    suspend fun verifyCredentials(@Query("mode") mode: String = "default"): UserDto
}
