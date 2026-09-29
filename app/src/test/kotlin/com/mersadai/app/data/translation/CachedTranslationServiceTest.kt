package com.mersadai.app.data.translation

import com.mersadai.app.data.local.TranslationEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedTranslationServiceTest {
    @Test
    fun reusesTranslationOnlyWhenTheOriginalTextAndLanguageMatch() = runBlocking {
        val cache = FakeCache()
        var translationCalls = 0
        val service = CachedTranslationService(cache, TranslationManager { text, _ ->
            translationCalls++
            "عربي: $text"
        }) { 42L }

        assertEquals("عربي: Original", service.translateToArabic("item", TranslationFields.TITLE, "Original"))
        assertEquals("عربي: Original", service.translateToArabic("item", TranslationFields.TITLE, "Original"))
        assertEquals("عربي: Changed", service.translateToArabic("item", TranslationFields.TITLE, "Changed"))
        assertEquals(2, translationCalls)
        assertEquals("Changed", cache.entries.single().sourceText)
    }

    @Test
    fun doesNotCacheBlankOrUnchangedTranslationsOrMutateInput() = runBlocking {
        val cache = FakeCache()
        var receivedText: String? = null
        val service = CachedTranslationService(cache, TranslationManager { text, _ ->
            receivedText = text
            text
        })
        val original = "Use {variable} and JSON exactly"

        assertNull(service.translateToArabic("prompt", TranslationFields.DESCRIPTION, original))
        assertEquals(original, receivedText)
        assertEquals(0, cache.entries.size)
    }

    private class FakeCache : TranslationCache {
        val entries = mutableListOf<TranslationEntity>()

        override suspend fun get(itemId: String, field: String, language: String): TranslationEntity? =
            entries.firstOrNull { it.itemId == itemId && it.field == field && it.language == language }

        override suspend fun put(translation: TranslationEntity) {
            entries.removeAll {
                it.itemId == translation.itemId && it.field == translation.field && it.language == translation.language
            }
            entries += translation
        }
    }
}