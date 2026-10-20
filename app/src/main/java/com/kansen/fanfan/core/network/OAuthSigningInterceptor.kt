package com.kansen.fanfan.core.network

import com.kansen.fanfan.BuildConfig
import com.kansen.fanfan.core.network.auth.OAuth1Signer
import com.kansen.fanfan.core.network.token.TokenProvider
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signs every api.fanfou.com request with the stored access token (OAuth 1.0a
 * Authorization header). Query and form-body parameters participate in the
 * signature per RFC 5849.
 *
 * Signing is skipped when no token is stored — those requests are expected to
 * fail with 401 and are surfaced by the error layer.
 */
@Singleton
class OAuthSigningInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
) : Interceptor {

    private val random = SecureRandom()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != "api.fanfou.com") return chain.proceed(request)

        val token = tokenProvider.accessToken ?: return chain.proceed(request)

        val params = signingParameters(request)

        val header = OAuth1Signer.authorizationHeader(
            method = request.method,
            url = request.url.toString(),
            requestParameters = params,
            consumerKey = BuildConfig.CONSUMER_KEY,
            consumerSecret = BuildConfig.CONSUMER_SECRET,
            token = token,
            tokenSecret = tokenProvider.accessTokenSecret ?: "",
            timestamp = (System.currentTimeMillis() / 1000).toString(),
            nonce = newNonce(),
        )

        return chain.proceed(request.newBuilder().header("Authorization", header).build())
    }

    private fun newNonce(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * The parameters that participate in the OAuth signature, in their DECODED
 * form: [OAuth1Signer] percent-encodes them again per RFC 3986.
 *
 * Live-verified 2026-10-19: reading `encodedName()`/`encodedValue()` from a
 * form body double-encodes (space → `%2520`) and the server answers 401
 * "Invalid signature" on `statuses/update`. Query values are already decoded
 * by `HttpUrl`.
 */
internal fun signingParameters(request: Request): Map<String, String> {
    val params = LinkedHashMap<String, String>()
    for (name in request.url.queryParameterNames) {
        val value = request.url.queryParameter(name) ?: continue
        params[name] = value
    }
    (request.body as? FormBody)?.let { form ->
        for (i in 0 until form.size) params[form.name(i)] = form.value(i)
    }
    return params
}
