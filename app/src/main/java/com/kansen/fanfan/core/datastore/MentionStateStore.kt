package com.kansen.fanfan.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mentionDataStore: DataStore<Preferences> by preferencesDataStore(name = "fanfan_mentions")

/**
 * Two watermarks, both `rawid` (monotonic, unlike the base62 `id`):
 *
 * - [readUpTo] drives the badge on the @我 tab; advanced when the user opens it.
 * - [notifiedUpTo] drives system notifications; advanced right after posting
 *   one so the 15-minute worker never reports the same mention twice.
 *
 * Kept separate on purpose: reading inside the app must not swallow a system
 * notification the user has not seen yet, and vice versa.
 */
@Singleton
class MentionStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val readUpTo = longPreferencesKey("read_up_to_rawid")
        val notifiedUpTo = longPreferencesKey("notified_up_to_rawid")
    }

    val readUpTo: Flow<Long> = context.mentionDataStore.data.map { it[Keys.readUpTo] ?: 0L }

    val notifiedUpTo: Flow<Long> = context.mentionDataStore.data.map { it[Keys.notifiedUpTo] ?: 0L }

    // Monotonic on purpose: a stale page must never rewind a watermark.
    suspend fun markRead(rawid: Long) = advance(Keys.readUpTo, rawid)

    suspend fun markNotified(rawid: Long) = advance(Keys.notifiedUpTo, rawid)

    private suspend fun advance(key: Preferences.Key<Long>, rawid: Long) {
        if (rawid <= 0) return
        context.mentionDataStore.edit { prefs ->
            if (rawid > (prefs[key] ?: 0L)) prefs[key] = rawid
        }
    }
}
