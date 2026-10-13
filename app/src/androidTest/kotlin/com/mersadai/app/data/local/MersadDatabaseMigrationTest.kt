package com.mersadai.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MersadDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MersadDatabase::class.java,
        emptyList(),
    )

    @Test
    fun migrationSixToSevenKeepsItemsAndFavoritesAndAddsApiKeyRequirement() {
        val databaseName = "migration-6-7-test"
        helper.createDatabase(databaseName, 6).apply {
            execSQL(
                """INSERT INTO items (
                    id, title, contentType, freeStatus, verificationLevel, createdAt, updatedAt,
                    sourceUrl, requiresPaymentCard, freeLimit
                ) VALUES (
                    'saved-item', 'Saved offer', 'AI_TOOL', 'UNKNOWN', 'UNVERIFIED', 123, 456,
                    'https://example.com/offer', 0, 'Existing limit'
                )""",
            )
            execSQL("INSERT INTO favorites (itemId, savedAt) VALUES ('saved-item', 789)")
            execSQL(
                """INSERT INTO items (
                    id, title, contentType, freeStatus, verificationLevel, lastVerifiedAt,
                    createdAt, updatedAt, author, freeLimit, requiresAccount, publishedAt
                ) VALUES (
                    'creator-deal-old', 'ChatGPT Free', 'AI_TOOL', 'FREE_TIER', 'OFFICIAL_SOURCE',
                    111, 123, 456, 'OpenAI', 'Unverified claim', 1, 222
                )""",
            )
            execSQL("INSERT INTO favorites (itemId, savedAt) VALUES ('creator-deal-old', 987)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            7,
            true,
            MersadDatabase.MIGRATION_6_7,
        )
        try {
            migrated.query(
                "SELECT title, freeStatus, sourceUrl, requiresPaymentCard, freeLimit, requiresApiKey FROM items WHERE id = 'saved-item'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Saved offer", cursor.getString(0))
                assertEquals("UNKNOWN", cursor.getString(1))
                assertEquals("https://example.com/offer", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals("Existing limit", cursor.getString(4))
                assertTrue(cursor.isNull(5))
            }
            migrated.query("SELECT savedAt FROM favorites WHERE itemId = 'saved-item'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(789L, cursor.getLong(0))
            }
            migrated.query(
                "SELECT freeStatus, verificationLevel, freeLimit, requiresAccount, publishedAt, requiresApiKey FROM items WHERE id = 'creator-deal-old'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("UNKNOWN", cursor.getString(0))
                assertEquals("UNVERIFIED", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.isNull(5))
            }
            migrated.query("SELECT savedAt FROM favorites WHERE itemId = 'creator-deal-old'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(987L, cursor.getLong(0))
            }
        } finally {
            migrated.close()
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrationSevenToEightKeepsPromptItemsAndFavorites() {
        val databaseName = "migration-7-8-test"
        helper.createDatabase(databaseName, 7).apply {
            execSQL(
                """INSERT INTO items (
                    id, title, contentType, freeStatus, verificationLevel, createdAt, updatedAt,
                    fetchedAt, originalDescription, thumbnailUrl, sourceUrl, externalId
                ) VALUES (
                    'civitai:image:42', 'A cinematic portrait', 'PROMPT', 'UNKNOWN',
                    'OFFICIAL_SOURCE', 0, 0, 1234, 'Original prompt', 'https://image.civitai.com/42.jpg',
                    'https://civitai.com/images/42', 'civitai:image:42'
                )""",
            )
            execSQL("INSERT INTO favorites (itemId, savedAt) VALUES ('civitai:image:42', 5678)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            8,
            true,
            MersadDatabase.MIGRATION_7_8,
        )
        try {
            migrated.query(
                "SELECT contentType, originalDescription, fetchedAt, thumbnailUrl, sourceUrl, modelName, generationSettings, discoveredAt FROM items WHERE id = 'civitai:image:42'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("PROMPT", cursor.getString(0))
                assertEquals("Original prompt", cursor.getString(1))
                assertEquals(1234L, cursor.getLong(2))
                assertEquals("https://image.civitai.com/42.jpg", cursor.getString(3))
                assertEquals("https://civitai.com/images/42", cursor.getString(4))
                assertTrue(cursor.isNull(5))
                assertTrue(cursor.isNull(6))
                assertTrue(cursor.isNull(7))
            }
            migrated.query("SELECT savedAt FROM favorites WHERE itemId = 'civitai:image:42'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(5678L, cursor.getLong(0))
            }
        } finally {
            migrated.close()
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrationEightToNineKeepsExistingOpportunityFavoritesAndCreatesUniquePromptStorage() {
        val databaseName = "migration-8-9-test"
        helper.createDatabase(databaseName, 8).apply {
            execSQL(
                """INSERT INTO items (
                    id, title, contentType, freeStatus, verificationLevel, createdAt, updatedAt
                ) VALUES (
                    'free-perk-existing', 'Existing opportunity', 'AI_TOOL', 'UNKNOWN',
                    'OFFICIAL_SOURCE', 123, 456
                )""",
            )
            execSQL("INSERT INTO favorites (itemId, savedAt) VALUES ('free-perk-existing', 789)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            9,
            true,
            MersadDatabase.MIGRATION_8_9,
        )
        try {
            migrated.query(
                "SELECT title, contentType FROM items WHERE id = 'free-perk-existing'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Existing opportunity", cursor.getString(0))
                assertEquals("AI_TOOL", cursor.getString(1))
            }
            migrated.query(
                "SELECT savedAt FROM favorites WHERE itemId = 'free-perk-existing'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(789L, cursor.getLong(0))
            }

            migrated.execSQL(
                """INSERT OR IGNORE INTO prompts (
                    sourceId, sourcePostId, postId, prompt, postUrl, imageUrl, modelName,
                    publishedAt, discoveredAt
                ) VALUES (
                    'civitai', '9001', 9001, 'Original prompt',
                    'https://civitai.com/posts/9001', 'https://image.civitai.com/42.jpg',
                    'Checkpoint One', 1000, 2000
                )""",
            )
            migrated.execSQL(
                """INSERT OR IGNORE INTO prompts (
                    sourceId, sourcePostId, postId, prompt, postUrl, imageUrl, modelName,
                    publishedAt, discoveredAt
                ) VALUES (
                    'civitai', '9001', 9001, 'Duplicate prompt',
                    'https://civitai.com/posts/9001', NULL, NULL, NULL, 3000
                )""",
            )
            migrated.query(
                "SELECT prompt, imageUrl, discoveredAt FROM prompts WHERE sourceId = 'civitai' AND sourcePostId = '9001'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Original prompt", cursor.getString(0))
                assertEquals("https://image.civitai.com/42.jpg", cursor.getString(1))
                assertEquals(2000L, cursor.getLong(2))
                assertEquals(false, cursor.moveToNext())
            }
        } finally {
            migrated.close()
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
        }
    }
}
