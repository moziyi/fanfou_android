package com.kansen.fanfan.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dmDataStore: DataStore<Preferences> by preferencesDataStore(name = "fanfan_dm")

/**
 * Unread direct-message state.
 *
 * The server marks a *conversation* as unread (`new_conv`), not individual
 * messages, so the badge counts conversations with unread messages. The value
 * is refreshed by the poll worker and whenever the conversation list is
 * opened, so the badge is right even if the tab was never opened.
 */
@Singleton
class DmStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val unreadConversations = intPreferencesKey("unread_conversations")
        val readMarkers = stringPreferencesKey("read_markers")
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Conversation id -> id of the last message the user has seen. The
     * server flag `new_conv` cannot be used: it reports the *other* side's
     * read state, not ours.
     */
    val readMarkers: Flow<Map<String, String>> =
        context.dmDataStore.data.map { prefs ->
            val raw = prefs[Keys.readMarkers].orEmpty()
            if (raw.isBlank()) emptyMap()
            else runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
        }

    val unreadConversations: Flow<Int> =
        context.dmDataStore.data.map { it[Keys.unreadConversations] ?: 0 }

    suspend fun setUnreadConversations(count: Int) {
        context.dmDataStore.edit { prefs ->
            prefs[Keys.unreadConversations] = count.coerceAtLeast(0)
        }
    }

    /** Records that the user has seen [messageId] in [conversationId]. */
    suspend fun markRead(conversationId: String, messageId: String) {
        if (conversationId.isBlank() || messageId.isBlank()) return
        context.dmDataStore.edit { prefs ->
            val current = prefs[Keys.readMarkers].orEmpty()
                .let { raw ->
                    if (raw.isBlank()) emptyMap()
                    else runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
                }
            prefs[Keys.readMarkers] = json.encodeToString(current + (conversationId to messageId))
        }
    }

    /** The message id the user has already seen in [conversationId]. */
    suspend fun readMarker(conversationId: String): String? =
        readMarkers.first()[conversationId]
}
