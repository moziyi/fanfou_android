package com.kansen.fanfan.core.network

import com.kansen.fanfan.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The fanfou gateway (Tencent tgw/nginx) answers 404 HTML to requests without
 * a custom User-Agent (verified 2026-10-16). Every request must carry one.
 */
@Singleton
class UserAgentInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", BuildConfig.USER_AGENT)
            .build()
        return chain.proceed(request)
    }
}
