package com.mersadai.app.data.translation

import com.mersadai.app.data.local.ContentDao
import com.mersadai.app.data.local.TranslationEntity

object TranslationFields {
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val ARABIC = "ar"
}

interface TranslationCache {
    suspend fun get(itemId: String, field: String, language: String): TranslationEntity?
    suspend fun put(translation: TranslationEntity)
}

class RoomTranslationCache(private val dao: ContentDao) : TranslationCache {
    override suspend fun get(itemId: String, field: String, language: String): TranslationEntity? =
        dao.getTranslation(itemId, field, language)

    override suspend fun put(translation: TranslationEntity) = dao.upsertTranslation(translation)
}

class CachedTranslationService(
    private val cache: TranslationCache,
    private val translationManager: TranslationManager,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun translateToArabic(
        itemId: String,
        field: String,
        sourceText: String,
        sourceLanguage: String = "en",
    ): String? {
        if (sourceText.isBlank()) return null
        val cached = cache.get(itemId, field, TranslationFields.ARABIC)
        if (cached?.sourceText == sourceText && cached.sourceLanguage == sourceLanguage) {
            return cached.translatedText
        }
        val translated = translationManager.translateToArabic(sourceText, sourceLanguage)
            ?.takeIf { it.isNotBlank() && it != sourceText } ?: return null
        cache.put(
            TranslationEntity(
                itemId = itemId,
                field = field,
                language = TranslationFields.ARABIC,
                sourceText = sourceText,
                sourceLanguage = sourceLanguage,
                translatedText = translated,
                updatedAt = now(),
            ),
        )
        return translated
    }
}