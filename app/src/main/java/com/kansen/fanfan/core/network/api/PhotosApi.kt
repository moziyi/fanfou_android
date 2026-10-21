package com.kansen.fanfan.core.network.api

import com.kansen.fanfan.core.network.dto.StatusDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Photo upload.
 *
 * Live-verified 2026-10-21: the endpoint creates the status itself. One
 * multipart request carries the picture and the text, and the answer is the
 * new status (including its `photo` object) — there is no "attach the photo
 * to a status you post later" step.
 *
 * The server signs only the `oauth_*` parameters for multipart requests: its
 * 401 answer echoes an expected base string that contains no form field, so
 * the fields must stay out of the signature (see `signingParameters`).
 */
interface PhotosApi {

    @Multipart
    @POST("photos/upload.json")
    suspend fun upload(
        @Part photo: MultipartBody.Part,
        @Part("status") status: RequestBody,
    ): StatusDto
}
