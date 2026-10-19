package com.kansen.fanfan.core.network

import android.util.Log
import com.kansen.fanfan.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Debug-only diagnostics for api.fanfou.com failures.
 *
 * Fanfou answers every API-layer rejection with a small envelope
 * ({"request": .., "error": ..}); that message is the authoritative reason and
 * is otherwise swallowed by Retrofit's non-2xx path. We log ONLY path, status
 * and body — never the Authorization header, which carries the xAuth password
 * during login.
 */
class ApiErrorLogInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (BuildConfig.DEBUG && response.code >= 400 &&
            chain.request().url.host == "api.fanfou.com"
        ) {
            val body = runCatching { response.peekBody(MAX_BODY_BYTES).string() }.getOrNull()
            Log.w(TAG, "${chain.request().url.encodedPath} -> ${response.code} $body")
        }
        return response
    }

    private companion object {
        const val TAG = "FanfanApi"
        const val MAX_BODY_BYTES = 2048L
    }
}
