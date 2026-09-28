package com.mersadai.app.data.local

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ItemEntity::class,
        SourceEntity::class,
        ItemSourceEntity::class,
        CategoryEntity::class,
        ItemCategoryEntity::class,
        FavoriteEntity::class,
        TranslationEntity::class,
        SyncStateEntity::class,
        StarSnapshotEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MersadDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                listOf(
                    "tags TEXT", "license TEXT", "starsCount INTEGER", "forksCount INTEGER",
                    "openIssuesCount INTEGER", "pipelineTag TEXT", "pipelineCategory TEXT",
                    "downloads INTEGER", "likes INTEGER", "trendingScore REAL", "sdk TEXT",
                    "emoji TEXT", "publishedAt INTEGER", "author TEXT", "promptForDevelopers INTEGER",
                    "promptType TEXT", "contributor TEXT", "archived INTEGER", "isFork INTEGER",
                    "sourceUpdatedAt INTEGER",
                    "pushedAt INTEGER", "gated INTEGER", "isPrivate INTEGER",
                ).forEach { column -> database.execSQL("ALTER TABLE items ADD COLUMN $column") }
                listOf(
                    "lastAttemptAt INTEGER", "lastSuccessAt INTEGER", "lastHttpStatus INTEGER",
                    "lastError TEXT", "etag TEXT", "lastModifiedHeader TEXT", "rateLimitRemaining INTEGER",
                    "rateLimitResetAt INTEGER", "nextAllowedSyncAt INTEGER",
                    "isSyncing INTEGER NOT NULL DEFAULT 0",
                ).forEach { column -> database.execSQL("ALTER TABLE sync_state ADD COLUMN $column") }
            }
        }

        fun create(context: Context): MersadDatabase =
            Room.databaseBuilder(context, MersadDatabase::class.java, "mersad.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
