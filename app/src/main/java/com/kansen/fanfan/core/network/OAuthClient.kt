package com.kansen.fanfan.core.network

import com.kansen.fanfan.BuildConfig
import com.kansen.fanfan.core.network.auth.OAuth1Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Tokens returned by the fanfou oauth endpoints. */
data class OAuthTokens(val token: String, val tokenSecret: String)

/**
 * xAuth login: one signed request exchanges username+password directly for an
 * access token (https://fanfou.com/oauth/access_token). The three x_auth_*
 * parameters participate in the signature and travel in the Authorization
 * header; response body is form-encoded: oauth_token=..&oauth_token_secret=..
 */
@Singleton
class OAuthClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    private val random = SecureRandom()

    suspend fun xAuthAccessToken(username: String, password: String): Result<OAuthTokens> =
        withContext(Dispatchers.IO) {
            runCatching {
                val header = OAuth1Signer.authorizationHeader(
                    method = "GET",
                    url = ACCESS_TOKEN_URL,
                    consumerKey = BuildConfig.CONSUMER_KEY,
                    consumerSecret = BuildConfig.CONSUMER_SECRET,
                    xAuthParameters = mapOf(
                        "x_auth_username" to username,
                        "x_auth_password" to password,
                        "x_auth_mode" to "client_auth",
                    ),
                    timestamp = epochSeconds(),
                    nonce = newNonce(),
                )
                val request = Request.Builder()
                    .url(ACCESS_TOKEN_URL)
                    .header("Authorization", header)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    check(response.isSuccessful) { "xAuth 登录失败 (${response.code}): $body" }
                    parseTokens(body)
                }
            }
        }

    private fun parseTokens(formEncoded: String): OAuthTokens {
        val map = formEncoded.split('&')
            .mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 } }
            .associate { (k, v) -> k to v }
        val token = map["oauth_token"]
        val secret = map["oauth_token_secret"]
        check(!token.isNullOrBlank() && !secret.isNullOrBlank()) {
            "响应缺少 token 字段: $formEncoded"
        }
        return OAuthTokens(token, secret)
    }

    private fun epochSeconds() = (System.currentTimeMillis() / 1000).toString()

    private fun newNonce(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val ACCESS_TOKEN_URL = "https://fanfou.com/oauth/access_token"
    }
}
