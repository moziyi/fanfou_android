package com.kansen.fanfan.core.network.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden fixtures generated and LIVE-VERIFIED against
 * https://fanfou.com/oauth/request_token on 2026-10-16 (HTTP 200 with exactly
 * these parameters). See .doc/饭否App设计方案-2026-10-16.md §0.
 */
class OAuth1SignerTest {

    private val consumerKey = "e32388ba8a694273598d477cef6a7851"
    private val consumerSecret = "be9a2533c6a215e9dfae38aa57a87347"

    @Test
    fun `percentEncode follows RFC 3986 unreserved set`() {
        assertEquals("fanfan%3A%2F%2Foauth%2Fcallback", OAuth1Signer.percentEncode("fanfan://oauth/callback"))
        assertEquals("hello%20world", OAuth1Signer.percentEncode("hello world"))
        assertEquals("unch-ained._~1", OAuth1Signer.percentEncode("unch-ained._~1"))
        assertEquals("%2B", OAuth1Signer.percentEncode("+"))
        assertEquals("%2A", OAuth1Signer.percentEncode("*"))
        assertEquals("%E9%A5%AD%E5%90%A6", OAuth1Signer.percentEncode("饭否"))
    }

    @Test
    fun `normalizeBaseUrl rewrites https to http`() {
        assertEquals(
            "http://fanfou.com/oauth/request_token",
            OAuth1Signer.normalizeBaseUrl("https://fanfou.com/oauth/request_token"),
        )
        assertEquals(
            "http://api.fanfou.com/statuses/home_timeline.json",
            OAuth1Signer.normalizeBaseUrl("https://api.fanfou.com/statuses/home_timeline.json"),
        )
        assertEquals("http://a.b/c", OAuth1Signer.normalizeBaseUrl("http://a.b/c"))
    }

    @Test
    fun `baseString matches live-verified golden fixture`() {
        val params = mapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to "fixturenonce0001",
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to "1792160597",
            "oauth_version" to "1.0",
            "oauth_callback" to "fanfan://oauth/callback",
        )
        val expected = "GET&http%3A%2F%2Ffanfou.com%2Foauth%2Frequest_token&" +
            "oauth_callback%3Dfanfan%253A%252F%252Foauth%252Fcallback%26" +
            "oauth_consumer_key%3De32388ba8a694273598d477cef6a7851%26" +
            "oauth_nonce%3Dfixturenonce0001%26" +
            "oauth_signature_method%3DHMAC-SHA1%26" +
            "oauth_timestamp%3D1792160597%26" +
            "oauth_version%3D1.0"
        assertEquals(expected, OAuth1Signer.baseString("GET", "https://fanfou.com/oauth/request_token", params))
    }

    @Test
    fun `signature matches live-verified golden fixture`() {
        val params = mapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to "fixturenonce0001",
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to "1792160597",
            "oauth_version" to "1.0",
            "oauth_callback" to "fanfan://oauth/callback",
        )
        val sig = OAuth1Signer.signature(
            method = "GET",
            url = "https://fanfou.com/oauth/request_token",
            allParameters = params,
            consumerSecret = consumerSecret,
            tokenSecret = null,
        )
        assertEquals("z2bjuEfmG9QZRjwAQxV03IfWWYU=", sig)
    }

    @Test
    fun `authorizationHeader for request_token matches live-verified golden fixture`() {
        val header = OAuth1Signer.authorizationHeader(
            method = "GET",
            url = "https://fanfou.com/oauth/request_token",
            consumerKey = consumerKey,
            consumerSecret = consumerSecret,
            callback = "fanfan://oauth/callback",
            timestamp = "1792160597",
            nonce = "fixturenonce0001",
        )
        val expected = "OAuth oauth_callback=\"fanfan%3A%2F%2Foauth%2Fcallback\", " +
            "oauth_consumer_key=\"e32388ba8a694273598d477cef6a7851\", " +
            "oauth_nonce=\"fixturenonce0001\", " +
            "oauth_signature=\"z2bjuEfmG9QZRjwAQxV03IfWWYU%3D\", " +
            "oauth_signature_method=\"HMAC-SHA1\", " +
            "oauth_timestamp=\"1792160597\", " +
            "oauth_version=\"1.0\""
        assertEquals(expected, header)
    }

    @Test
    fun `authorizationHeader with token and verifier includes them`() {
        val header = OAuth1Signer.authorizationHeader(
            method = "GET",
            url = "https://fanfou.com/oauth/access_token",
            consumerKey = consumerKey,
            consumerSecret = consumerSecret,
            token = "req-token",
            tokenSecret = "req-secret",
            verifier = "123456",
            timestamp = "1792160597",
            nonce = "n2",
        )
        assert(header.contains("oauth_token=\"req-token\""))
        assert(header.contains("oauth_verifier=\"123456\""))
        assert(!header.contains("oauth_callback"))
    }

    @Test
    fun `xAuth parameters are signed and emitted into the header`() {
        val header = OAuth1Signer.authorizationHeader(
            method = "GET",
            url = "https://fanfou.com/oauth/access_token",
            consumerKey = consumerKey,
            consumerSecret = consumerSecret,
            xAuthParameters = mapOf(
                "x_auth_username" to "user@example.com",
                "x_auth_password" to "secret!",
                "x_auth_mode" to "client_auth",
            ),
            timestamp = "1792160597",
            nonce = "n3",
        )
        assert(header.contains("x_auth_mode=\"client_auth\""))
        assert(header.contains("x_auth_username=\"user%40example.com\""))
        assert(header.contains("x_auth_password=\"secret%21\""))
        assert(header.startsWith("OAuth oauth_"))
        // x_auth params must also participate in the signature; a different
        // password must produce a different signature.
        val other = OAuth1Signer.authorizationHeader(
            method = "GET",
            url = "https://fanfou.com/oauth/access_token",
            consumerKey = consumerKey,
            consumerSecret = consumerSecret,
            xAuthParameters = mapOf(
                "x_auth_username" to "user@example.com",
                "x_auth_password" to "OTHER",
                "x_auth_mode" to "client_auth",
            ),
            timestamp = "1792160597",
            nonce = "n3",
        )
        val sig = Regex("oauth_signature=\"([^\"]+)\"").find(header)!!.groupValues[1]
        val sig2 = Regex("oauth_signature=\"([^\"]+)\"").find(other)!!.groupValues[1]
        assert(sig != sig2)
    }

    @Test
    fun `normalizeBaseUrl strips query string and fragment`() {
        assertEquals(
            "http://api.fanfou.com/statuses/home_timeline.json",
            OAuth1Signer.normalizeBaseUrl(
                "https://api.fanfou.com/statuses/home_timeline.json?count=30&mode=lite",
            ),
        )
        assertEquals(
            "http://api.fanfou.com/x.json",
            OAuth1Signer.normalizeBaseUrl("https://api.fanfou.com/x.json?a=1#frag"),
        )
    }

    /**
     * Regression, live-verified 2026-10-19. The device request failed with a
     * 401 whose body echoed the expected base string: the query string must NOT
     * appear in the URL component (it broke GET requests with parameters while
     * xAuth's query-less POST passed).
     */
    @Test
    fun `baseString for home_timeline matches live server expectation`() {
        val params = mapOf(
            "count" to "30",
            "mode" to "lite",
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to "68dde2488db857df688481f46407dba7",
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to "1792390222",
            "oauth_token" to "token-for-fixture",
            "oauth_version" to "1.0",
        )
        val expected = "GET&http%3A%2F%2Fapi.fanfou.com%2Fstatuses%2Fhome_timeline.json&" +
            "count%3D30%26mode%3Dlite%26" +
            "oauth_consumer_key%3De32388ba8a694273598d477cef6a7851%26" +
            "oauth_nonce%3D68dde2488db857df688481f46407dba7%26" +
            "oauth_signature_method%3DHMAC-SHA1%26" +
            "oauth_timestamp%3D1792390222%26" +
            "oauth_token%3Dtoken-for-fixture%26" +
            "oauth_version%3D1.0"
        assertEquals(
            expected,
            OAuth1Signer.baseString(
                method = "GET",
                url = "https://api.fanfou.com/statuses/home_timeline.json?count=30&mode=lite",
                allParameters = params,
            ),
        )
    }

    @Test
    fun `baseString escapes ampersand in parameter values`() {
        val base = OAuth1Signer.baseString(
            "GET",
            "https://api.fanfou.com/x.json",
            mapOf("a" to "1&2", "b" to "3=4"),
        )
        // a=1%262 must sort before b=3%3D4 and be escaped itself
        assert(base.endsWith("a%3D1%25262%26b%3D3%253D4"))
    }

    /**
     * Regression, live-verified 2026-10-19. A publish of "test 20261019" failed
     * with 401 "Invalid signature" whose body echoed the expected base string;
     * the form-body parameter value must be the DECODED text (space → %20
     * exactly once, so the joined param string shows %2520). Feeding the
     * already-encoded body value produced %252520 and the mismatch.
     */
    @Test
    fun `baseString for update with space in status matches live server expectation`() {
        val base = OAuth1Signer.baseString(
            method = "POST",
            url = "https://api.fanfou.com/statuses/update.json",
            allParameters = mapOf(
                "status" to "test 20261019",
                "oauth_consumer_key" to consumerKey,
                "oauth_nonce" to "5596c7a613c0bd1156cefeb80c16ac13",
                "oauth_signature_method" to "HMAC-SHA1",
                "oauth_timestamp" to "1792412466",
                "oauth_token" to "token-for-fixture",
                "oauth_version" to "1.0",
            ),
        )
        assert(base.endsWith("oauth_version%3D1.0%26status%3Dtest%252020261019"))
        assert(!base.contains("%252520"))
    }
}
