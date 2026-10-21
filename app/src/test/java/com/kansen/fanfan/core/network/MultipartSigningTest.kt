package com.kansen.fanfan.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Live-verified 2026-10-21: `photos/upload` is a multipart request, and the
 * server signs **only** the oauth_* parameters for it — its 401 answer echoed
 * an expected base string that contained no form field. A form field in the
 * signature makes the server answer "Invalid signature", so the fields must
 * stay out.
 */
class MultipartSigningTest {

    private fun uploadRequest(): Request {
        val photo = MultipartBody.Part.createFormData(
            "photo",
            "fanfan_test.jpg",
            ByteArray(16).toRequestBody("image/jpeg".toMediaType()),
        )
        val status = "M4 图片发布测试".toRequestBody("text/plain; charset=utf-8".toMediaType())
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(photo)
            .addFormDataPart("status", "M4 图片发布测试")
            .build()
        return Request.Builder()
            .url("https://api.fanfou.com/photos/upload.json")
            .post(body)
            .build()
    }

    @Test
    fun `multipart form fields do not take part in the signature`() {
        val params = signingParameters(uploadRequest())

        assertFalse("the status field must not be signed", params.containsKey("status"))
        assertEquals(emptyMap<String, String>(), params)
    }
}
