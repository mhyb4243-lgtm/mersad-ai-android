package com.mersadai.app.data.local

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mersadai.app.data.mapper.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        NotificationHistoryEntity::class,
    ],
    version = 4,
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
                    "pushedAt INTEGER", "gated INTEGER", "isPrivate INTEGER", "libraryName TEXT",
                ).forEach { column -> database.execSQL("ALTER TABLE items ADD COLUMN $column") }
                listOf(
                    "lastAttemptAt INTEGER", "lastSuccessAt INTEGER", "lastHttpStatus INTEGER",
                    "lastError TEXT", "etag TEXT", "lastModifiedHeader TEXT", "rateLimitRemaining INTEGER",
                    "rateLimitResetAt INTEGER", "nextAllowedSyncAt INTEGER",
                    "isSyncing INTEGER NOT NULL DEFAULT 0",
                    "remoteTotalCount INTEGER",
                ).forEach { column -> database.execSQL("ALTER TABLE sync_state ADD COLUMN $column") }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE translations ADD COLUMN sourceText TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE translations ADD COLUMN sourceLanguage TEXT NOT NULL DEFAULT 'en'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS notification_history (notificationKey TEXT NOT NULL, itemId TEXT NOT NULL, notificationType TEXT NOT NULL, discoveredAt INTEGER NOT NULL, notifiedAt INTEGER, PRIMARY KEY(notificationKey))",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_history_notifiedAt ON notification_history(notifiedAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_history_discoveredAt ON notification_history(discoveredAt)")
            }
        }

        fun create(context: Context): MersadDatabase {
            val database = Room.databaseBuilder(context, MersadDatabase::class.java, "mersad.db")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .build()

            CoroutineScope(Dispatchers.IO).launch {
                if (database.contentDao().countItems() == 0) {
                    SeedContentProvider.items().forEach { item ->
                        val source = item.source ?: return@forEach
                        val category = item.category
                        database.contentDao().upsertRemoteContent(item.toEntity(), source.toEntity(), category?.toEntity())
                    }
                }
            }

            return database
        }
    }
}
