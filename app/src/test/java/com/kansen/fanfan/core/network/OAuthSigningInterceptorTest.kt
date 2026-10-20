package com.kansen.fanfan.core.network

import okhttp3.FormBody
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression, live-verified 2026-10-19: publishing "test 20261019" failed with
 * 401 "Invalid signature". The bug lived here, not in the signer — form-body
 * values must be handed over DECODED, because the signer percent-encodes them
 * once more per RFC 3986.
 */
class OAuthSigningInterceptorTest {

    @Test
    fun `form body values are decoded before signing`() {
        val request = Request.Builder()
            .url("https://api.fanfou.com/statuses/update.json")
            .post(
                FormBody.Builder()
                    .add("status", "test 20261019 & x=1+2")
                    .build(),
            )
            .build()

        assertEquals("test 20261019 & x=1+2", signingParameters(request)["status"])
    }

    @Test
    fun `query values are decoded before signing`() {
        val request = Request.Builder()
            .url("https://api.fanfou.com/statuses/home_timeline.json?mode=lite&max_id=a%2Fb%20c")
            .build()

        val params = signingParameters(request)
        assertEquals("lite", params["mode"])
        assertEquals("a/b c", params["max_id"])
    }

    @Test
    fun `query and form parameters are merged`() {
        val request = Request.Builder()
            .url("https://api.fanfou.com/statuses/update.json?mode=lite")
            .post(FormBody.Builder().add("status", "hi there").build())
            .build()

        assertEquals(
            mapOf("mode" to "lite", "status" to "hi there"),
            signingParameters(request),
        )
    }
}
