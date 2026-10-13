package com.mersadai.app.data.local

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mersadai.app.data.mapper.toEntity
import com.mersadai.app.domain.model.ContentType
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
    version = 8,
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE items ADD COLUMN dealType TEXT")
                database.execSQL("ALTER TABLE items ADD COLUMN promoCode TEXT")
                database.execSQL("ALTER TABLE items ADD COLUMN isActive INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE items ADD COLUMN fetchedAt INTEGER")
                database.execSQL("ALTER TABLE items ADD COLUMN sourceUrl TEXT")
                database.execSQL(
                    """UPDATE items SET createdAt = 0, updatedAt = 0, publishedAt = NULL,
                        sourceUpdatedAt = NULL, pushedAt = NULL, lastVerifiedAt = NULL
                        WHERE id LIKE 'seed-%' OR id LIKE 'creator-deal-%' OR id LIKE 'free-perk-%'
                            OR id LIKE 'offer-%'""",
                )
                database.execSQL(
                    """UPDATE items SET createdAt = 0, updatedAt = 0, publishedAt = NULL,
                        sourceUpdatedAt = NULL, pushedAt = NULL
                        WHERE id LIKE 'prompts-chat:%' OR id LIKE 'image-prompts:%'
                            OR id LIKE 'video-prompts:%' OR id LIKE 'lexica:%'
                            OR id LIKE 'remote-prompts:image-prompts-%'
                            OR id LIKE 'remote-prompts:video-prompts-%'
                            OR id LIKE 'remote-prompts:prompts-chat-%'
                            OR id LIKE 'remote-prompts:photo-manipulation-%'
                            OR id LIKE 'remote-prompts:huggingface-space-%'""",
                )
            }

        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE items ADD COLUMN requiresApiKey INTEGER")
                database.execSQL(
                    """UPDATE items SET title = COALESCE(author, title) || ' — صفحة غير متحقق منها',
                        originalTitle = COALESCE(author, originalTitle, title),
                        description = 'لم يتم التحقق من شروط المجانية أو حدود الاستخدام؛ راجع صفحة المصدر الرسمية.',
                        originalDescription = NULL, freeStatus = 'UNKNOWN', verificationLevel = 'UNVERIFIED',
                        lastVerifiedAt = NULL, createdAt = 0, updatedAt = 0, publishedAt = NULL,
                        sourceUpdatedAt = NULL, pushedAt = NULL, freeLimit = NULL, requiresAccount = NULL,
                        requiresPaymentCard = NULL, requiresApiKey = NULL, isActive = NULL, dealType = NULL,
                        promoCode = NULL, startAt = NULL, endAt = NULL
                        WHERE id LIKE 'offer-%' OR id LIKE 'free-perk-%' OR id LIKE 'creator-deal-%'""",
                )
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE items ADD COLUMN modelName TEXT")
                database.execSQL("ALTER TABLE items ADD COLUMN generationSettings TEXT")
                database.execSQL("ALTER TABLE items ADD COLUMN discoveredAt INTEGER")
            }
        }

        fun create(context: Context): MersadDatabase {
            val database = Room.databaseBuilder(context, MersadDatabase::class.java, "mersad.db")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
                .build()

            CoroutineScope(Dispatchers.IO).launch {
                if (database.contentDao().countItems() == 0) {
                    SeedContentProvider.items().filter { it.contentType != ContentType.PROMPT }.forEach { item ->
                        val source = item.source ?: return@forEach
                        val category = item.category
                        database.contentDao().upsertRemoteContent(item.toEntity(), source.toEntity(), category?.toEntity())
                    }
                }
                SeedContentProvider.additiveItems().filter { it.contentType != ContentType.PROMPT }.forEach { item ->
                    if (database.contentDao().hasItem(item.id)) return@forEach
                    val source = item.source ?: return@forEach
                    database.contentDao().upsertRemoteContent(
                        item.toEntity(),
                        source.toEntity(),
                        item.category?.toEntity(),
                    )
                }
            }

            return database
        }
    }
}
