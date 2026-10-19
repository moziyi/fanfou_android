package com.kansen.fanfan.core.network.token

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronous view over the in-memory token cache, for OkHttp interceptors.
 * The cache is warmed at app start (di/NetworkModule warm-up) and updated
 * synchronously on every write (TokenStore.saveTokens/clear), so interceptor
 * reads never touch disk and never race a fresh login.
 */
@Singleton
class TokenProvider @Inject constructor(
    private val cache: TokenCache,
) {
    val accessToken: String? get() = cache.accessToken
    val accessTokenSecret: String? get() = cache.accessSecret
}
