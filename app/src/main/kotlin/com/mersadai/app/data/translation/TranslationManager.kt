package com.mersadai.app.data.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

interface TranslationManager {
    suspend fun translateToArabic(originalText: String, sourceLanguageCode: String): String?
}

class MlKitTranslationManager : TranslationManager {
    override suspend fun translateToArabic(originalText: String, sourceLanguageCode: String): String? {
        val source = TranslateLanguage.fromLanguageTag(sourceLanguageCode) ?: return null
        val target = TranslateLanguage.ARABIC ?: return null
        val client = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build(),
        )
        return try {
            client.downloadModelIfNeeded().await()
            client.translate(originalText).await()
        } catch (_: Exception) {
            null
        } finally {
            client.close()
        }
    }
}
