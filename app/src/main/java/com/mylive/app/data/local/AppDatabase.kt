package com.mylive.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mylive.app.data.local.dao.FollowUserDao
import com.mylive.app.data.local.dao.FollowUserTagDao
import com.mylive.app.data.local.dao.HistoryDao
import com.mylive.app.data.local.dao.ShieldDao
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.local.entity.ShieldPresetEntity

@Database(
    entities = [
        FollowUserEntity::class,
        FollowUserTagEntity::class,
        HistoryEntity::class,
        ShieldEntity::class,
        ShieldPresetEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun followUserDao(): FollowUserDao
    abstract fun followUserTagDao(): FollowUserTagDao
    abstract fun historyDao(): HistoryDao
    abstract fun shieldDao(): ShieldDao

    companion object {
        /**
         * Migration 1 → 2: [FollowUserTagEntity.userIds] changed Kotlin type from `String`
         * (raw JSON array) to `List<String>` (via [Converters]). The SQLite column affinity
         * stays `TEXT` and the on-disk format is unchanged, so this is a no-op that only bumps
         * the schema version. Exposed here so it can be exercised by MigrationTestHelper.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No SQL change required. Future schema changes must add explicit migrations
                // rather than falling back to destructive migration.
            }
        }

        /**
         * Migration 2 → 3: Added [FollowUserEntity.showTime] column to store
         * the platform-reported live start timestamp (seconds).
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE follow_users ADD COLUMN showTime TEXT DEFAULT NULL")
            }
        }
    }
}
