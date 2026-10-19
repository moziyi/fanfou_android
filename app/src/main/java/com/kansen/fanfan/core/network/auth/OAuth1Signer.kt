package com.kansen.fanfan.core.network.auth

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * OAuth 1.0a (RFC 5849) HMAC-SHA1 signer for the fanfou API.
 *
 * Pure Kotlin, no Android dependencies. Two fanfou-specific rules are baked in,
 * both verified against the live API on 2026-10-16 (see .doc design doc §0):
 *
 * 1. The signature base string always uses the http scheme (http URL), even though
 *    requests are sent over https. The server computes the expected signature
 *    against `http://fanfou.com/...` / `http://api.fanfou.com/...`.
 * 2. The parameter set must include `oauth_version=1.0`, and at the
 *    request_token stage `oauth_callback`.
 *
 * A golden fixture (fixed timestamp/nonce → expected signature, live-verified
 * HTTP 200) pins this behaviour in OAuth1SignerTest.
 */
object OAuth1Signer {

    const val SIGNATURE_METHOD = "HMAC-SHA1"
    const val OAUTH_VERSION = "1.0"

    /**
     * Builds the `Authorization` header value for a signed request.
     *
     * @param method HTTP method, e.g. "GET"/"POST".
     * @param url request URL exactly as sent on the wire (may be https).
     * @param requestParameters all non-oauth request parameters (query + form
     *   body), which participate in the signature per RFC 5849 §3.4.1.3.
     * @param xAuthParameters x_auth_* credentials (xAuth login). Signed AND
     *   emitted into the header, per the fanfou XAuth sample.
     * @param token access/request token, absent at the plain consumer stage.
     * @param callback oauth_callback, request_token stage only.
     * @param verifier oauth_verifier, access_token stage only.
     * @param timestamp unix seconds as string.
     * @param nonce unique-per-request string.
     */
    fun authorizationHeader(
        method: String,
        url: String,
        requestParameters: Map<String, String> = emptyMap(),
        consumerKey: String,
        consumerSecret: String,
        xAuthParameters: Map<String, String> = emptyMap(),
        token: String? = null,
        tokenSecret: String? = null,
        callback: String? = null,
        verifier: String? = null,
        timestamp: String,
        nonce: String,
    ): String {
        val oauthParams = buildMap {
            put("oauth_consumer_key", consumerKey)
            put("oauth_nonce", nonce)
            put("oauth_signature_method", SIGNATURE_METHOD)
            put("oauth_timestamp", timestamp)
            put("oauth_version", OAUTH_VERSION)
            token?.let { put("oauth_token", it) }
            callback?.let { put("oauth_callback", it) }
            verifier?.let { put("oauth_verifier", it) }
        }

        val signature = signature(
            method = method,
            url = url,
            allParameters = oauthParams + requestParameters + xAuthParameters,
            consumerSecret = consumerSecret,
            tokenSecret = tokenSecret,
        )

        val headerParams = oauthParams + xAuthParameters + ("oauth_signature" to signature)
        val pairs = headerParams.entries
            .sortedBy { it.key }
            .joinToString(", ") { (k, v) -> """${percentEncode(k)}="${percentEncode(v)}"""" }
        return "OAuth $pairs"
    }

    /** RFC 5849 §3.4.1 signature base string — exposed for tests. */
    fun baseString(
        method: String,
        url: String,
        allParameters: Map<String, String>,
    ): String {
        val normalizedUrl = normalizeBaseUrl(url)
        val paramString = allParameters.entries
            .sortedWith(compareBy({ it.key }, { it.value }))
            .joinToString("&") { (k, v) -> "${percentEncode(k)}=${percentEncode(v)}" }
        return listOf(method.uppercase(), normalizedUrl, paramString)
            .joinToString("&") { percentEncode(it) }
    }

    fun signature(
        method: String,
        url: String,
        allParameters: Map<String, String>,
        consumerSecret: String,
        tokenSecret: String?,
    ): String {
        val base = baseString(method, url, allParameters)
        val signingKey = "${percentEncode(consumerSecret)}&${percentEncode(tokenSecret ?: "")}"
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(signingKey.encodeToByteArray(), "HmacSHA1"))
        return base64(mac.doFinal(base.encodeToByteArray()))
    }

    /**
     * Fanfou quirk: the base string URL uses the http scheme. Per RFC 5849
     * §3.4.1.2 the base string URI excludes the query string and fragment —
     * request parameters are signed separately. Everything else (host, port,
     * path) is preserved.
     */
    fun normalizeBaseUrl(url: String): String {
        val withoutFragment = url.substringBefore('#')
        val withoutQuery = withoutFragment.substringBefore('?')
        return if (withoutQuery.startsWith("https://")) {
            "http://" + withoutQuery.removePrefix("https://")
        } else {
            withoutQuery
        }
    }

    /** RFC 3986 unreserved characters: A-Z a-z 0-9 - . _ ~ */
    fun percentEncode(value: String): String {
        val out = StringBuilder(value.length)
        for (byte in value.encodeToByteArray()) {
            val c = byte.toInt().toChar()
            when {
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~' ->
                    out.append(c)
                else -> out.append('%').append(((byte.toInt() and 0xFF)).toString(16).uppercase().padStart(2, '0'))
            }
        }
        return out.toString()
    }

    private fun base64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)
}
