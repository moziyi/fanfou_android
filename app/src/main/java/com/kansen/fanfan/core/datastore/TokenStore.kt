package com.kansen.fanfan.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kansen.fanfan.core.network.token.TokenCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "fanfan_auth")

/** The logged-in user, as returned by verify_credentials. */
data class SelfUser(
    val id: String,
    val screenName: String,
    val avatarUrl: String?,
)

data class AuthSession(
    val token: String,
    val tokenSecret: String,
    val user: SelfUser?,
)

/** Durable auth state: access token pair + self user. Plaintext per design D4. */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenCache: TokenCache,
) {

    private object Keys {
        val token = stringPreferencesKey("oauth_token")
        val tokenSecret = stringPreferencesKey("oauth_token_secret")
        val userId = stringPreferencesKey("self_user_id")
        val screenName = stringPreferencesKey("self_screen_name")
        val avatarUrl = stringPreferencesKey("self_avatar_url")
    }

    val session: Flow<AuthSession?> = context.tokenDataStore.data.map { prefs ->
        val token = prefs[Keys.token]
        val secret = prefs[Keys.tokenSecret]
        if (token == null || secret == null) {
            null
        } else {
            val id = prefs[Keys.userId]
            AuthSession(
                token = token,
                tokenSecret = secret,
                user = if (id != null) {
                    SelfUser(id = id, screenName = prefs[Keys.screenName].orEmpty(), avatarUrl = prefs[Keys.avatarUrl])
                } else {
                    null
                },
            )
        }
    }

    suspend fun saveTokens(token: String, tokenSecret: String) {
        context.tokenDataStore.edit { prefs ->
            prefs[Keys.token] = token
            prefs[Keys.tokenSecret] = tokenSecret
        }
        tokenCache.update(token, tokenSecret)
    }

    suspend fun saveSelfUser(id: String, screenName: String, avatarUrl: String?) {
        context.tokenDataStore.edit { prefs ->
            prefs[Keys.userId] = id
            prefs[Keys.screenName] = screenName
            avatarUrl?.let { prefs[Keys.avatarUrl] = it } ?: prefs.remove(Keys.avatarUrl)
        }
    }

    /** Warm the in-memory cache used by interceptors; call once at app start. */
    suspend fun warmCache() {
        val prefs = context.tokenDataStore.data.first()
        val token = prefs[Keys.token]
        val secret = prefs[Keys.tokenSecret]
        if (token != null && secret != null) tokenCache.update(token, secret)
    }

    suspend fun clear() {
        context.tokenDataStore.edit { it.clear() }
        tokenCache.update(null, null)
    }
}
