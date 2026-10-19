package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.database.FanfanDatabase
import com.kansen.fanfan.core.datastore.SelfUser
import com.kansen.fanfan.core.datastore.TokenStore
import com.kansen.fanfan.core.network.OAuthClient
import com.kansen.fanfan.core.network.api.FanfouApi
import com.kansen.fanfan.core.network.token.TokenCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns authentication. xAuth path: username+password → access token in one
 * signed request, then verify_credentials to persist the self user.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val oauthClient: OAuthClient,
    private val tokenStore: TokenStore,
    private val tokenCache: TokenCache,
    private val api: FanfouApi,
    private val database: FanfanDatabase,
) {

    val sessionFlow: Flow<com.kansen.fanfan.core.datastore.AuthSession?> = tokenStore.session

    suspend fun login(username: String, password: String): Result<SelfUser> {
        val tokens = oauthClient.xAuthAccessToken(username.trim(), password)
            .getOrElse { return Result.failure(it) }

        // Verify BEFORE persisting. Persisting first flips sessionFlow, which
        // navigates to Home even when verify_credentials fails — the user sees
        // a "successful" login with a token that cannot call the API.
        tokenCache.update(tokens.token, tokens.tokenSecret)
        return runCatching {
            val user = api.verifyCredentials()
            val id = user.id ?: error("verify_credentials 未返回用户 id")
            val screenName = user.screen_name ?: user.name ?: id
            val avatar = user.profile_image_url_large ?: user.profile_image_url
            tokenStore.saveTokens(tokens.token, tokens.tokenSecret)
            tokenStore.saveSelfUser(id, screenName, avatar)
            SelfUser(id = id, screenName = screenName, avatarUrl = avatar)
        }.onFailure {
            tokenCache.update(null, null)
        }
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        tokenStore.clear()
    }
}
