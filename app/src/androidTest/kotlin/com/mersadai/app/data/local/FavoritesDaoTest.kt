package com.mersadai.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import com.mersadai.app.domain.model.VerificationLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesDaoTest {
    @Test
    fun favoriteSurvivesCacheClearAndCanBeRemoved() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, MersadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.contentDao()
            dao.upsertItems(listOf(item("saved"), item("temporary")))
            dao.setFavorite("saved", true, 10L)
            dao.upsertRemoteContent(
                item("saved").copy(title = "updated"),
                SourceEntity("github", "GitHub", "github", "https://github.com", "https://api.github.com"),
                CategoryEntity("android", "Android", null),
            )
            dao.deleteUnfavoritedItems()

            assertEquals(listOf("saved"), dao.observeFavorites().first().map { it.item.id })
            assertEquals("updated", dao.observeFavorites().first().single().item.title)
            assertTrue(dao.isFavorite("saved"))

            dao.setFavorite("saved", false, 20L)
            assertTrue(dao.observeFavorites().first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun localSearchFindsSourceTagsAndCategory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, MersadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.contentDao()
            dao.upsertRemoteContent(
                item("model").copy(contentType = ContentType.MODEL.name, tags = "text-generation\nlicense:apache-2.0"),
                SourceEntity("hf-models", "Hugging Face", "hub", "https://huggingface.co", "https://huggingface.co/api/models"),
                CategoryEntity("models", "Models", null),
            )

            assertEquals("model", dao.searchItems("apache-2.0").first().single().item.id)
            assertEquals("model", dao.searchItems("Hugging Face").first().single().item.id)
            assertEquals("model", dao.searchItems("Models").first().single().item.id)
            assertEquals("model", dao.searchItems("نماذج").first().single().item.id)
        } finally {
            database.close()
        }
    }

    @Test
    fun localSearchFindsArabicTranslationAndOriginalEnglish() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, MersadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.contentDao()
            dao.upsertItem(item("translated").copy(title = "Image model", originalTitle = "Image model"))
            dao.upsertTranslation(
                TranslationEntity(
                    itemId = "translated",
                    field = "title",
                    language = "ar",
                    sourceText = "Image model",
                    sourceLanguage = "en",
                    translatedText = "نموذج صور",
                    updatedAt = 42L,
                ),
            )

            assertEquals("translated", dao.searchItems("نموذج صور").first().single().item.id)
            assertEquals("translated", dao.searchItems("Image model").first().single().item.id)
        } finally {
            database.close()
        }
    }

    private fun item(id: String) = ItemEntity(
        id = id,
        title = id,
        originalTitle = null,
        description = null,
        originalDescription = null,
        url = null,
        contentType = ContentType.OTHER.name,
        freeStatus = FreeStatus.UNKNOWN.name,
        verificationLevel = VerificationLevel.UNVERIFIED.name,
        lastVerifiedAt = null,
        createdAt = 1L,
        updatedAt = 1L,
        language = null,
        thumbnailUrl = null,
        externalId = null,
    )
}
