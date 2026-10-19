package com.kansen.fanfan.core.network.token

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mirror of the persisted access token pair (token, secret).
 * TokenStore updates it on every write; interceptors read it synchronously.
 */
@Singleton
class TokenCache @Inject constructor() {

    @Volatile
    private var pair: Pair<String, String>? = null

    val accessToken: String? get() = pair?.first
    val accessSecret: String? get() = pair?.second

    fun update(token: String?, secret: String?) {
        pair = if (token != null && secret != null) token to secret else null
    }
}
