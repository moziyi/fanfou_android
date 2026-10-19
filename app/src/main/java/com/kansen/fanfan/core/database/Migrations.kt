package com.kansen.fanfan.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 adds the reply-parent id as a column. It is needed both to resolve the
 * quoted parent of a reply and to keep those rows alive when orphan statuses
 * are pruned. Migrated rather than rebuilt: drafts are user data and a
 * destructive migration would drop them along with the cache.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE status ADD COLUMN inReplyToStatusId TEXT")
        // The new column is derived from rawJson, which pure SQL cannot read
        // (json1 is not enabled on every API level), so the cached rows are
        // dropped rather than left with a column that lies about their content.
        // Only the status cache goes: drafts, favorite overrides and tokens are
        // user data and stay.
        db.execSQL("DELETE FROM status")
        db.execSQL("DELETE FROM timeline_entry")
    }
}
