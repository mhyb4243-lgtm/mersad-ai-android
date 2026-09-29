package com.mersadai.app.domain.search

import com.mersadai.app.domain.model.Category
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.Source
import com.mersadai.app.data.local.SeedContentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalContentSearchTest {
    @Test
    fun normalizesCommonArabicDiacriticsAlefTaaMarbutaAndYehWithoutChangingStoredText() {
        val title = "إدارة الذكاء الاصطناعي"
        val item = item("one", title = title)

        val results = LocalContentSearch.search(listOf(item), "اداره الذكاء الاصطناعي")

        assertEquals(listOf("one"), results.map(ContentItem::id))
        assertEquals(title, item.title)
    }

    @Test
    fun ranksTitleBeforeTagsAndDescription() {
        val descriptionMatch = item("description", description = "Compose navigation patterns")
        val tagMatch = item("tag", tags = listOf("compose"))
        val titleMatch = item("title", title = "Compose tools")

        assertEquals(
            listOf("title", "tag", "description"),
            LocalContentSearch.search(listOf(descriptionMatch, tagMatch, titleMatch), "compose").map(ContentItem::id),
        )
    }

    @Test
    fun filtersBySourceAndTypeAndSearchesCategoryAndContentType() {
        val project = item("project", title = "Compose toolkit", type = ContentType.ANDROID_PROJECT).copy(
            source = Source("github", "GitHub"),
            category = Category("android", "Android"),
        )
        val news = item("news", title = "Android update", type = ContentType.NEWS).copy(
            source = Source("google-developers", "Google Developers"),
            category = Category("developer-tools", "Developer Tools"),
        )
        val items = listOf(project, news)

        assertEquals(listOf("project"), LocalContentSearch.search(items, "compose", SearchFilters(setOf(ContentType.ANDROID_PROJECT))).map(ContentItem::id))
        assertEquals(listOf("news"), LocalContentSearch.search(items, "Google", SearchFilters(sourceId = "google-developers")).map(ContentItem::id))
        assertTrue(LocalContentSearch.search(items, "مشاريع Android").any { it.id == "project" })
        assertEquals(listOf("news"), LocalContentSearch.search(items, "developer tools").map(ContentItem::id))
    }

    @Test
    fun recognizesPromptAndGenerationTerms() {
        val prompt = item("prompt", title = "AI photo prompt", type = ContentType.PROMPT)

        assertTrue(LocalContentSearch.search(listOf(prompt), "برومبت").any { it.id == "prompt" })
        assertTrue(LocalContentSearch.search(listOf(prompt), "أمر توليد").any { it.id == "prompt" })
    }

    @Test
    fun seedsAndroidReadyAiCatalog() {
        val items = SeedContentProvider.items()

        assertTrue(items.any { it.contentType == ContentType.ANDROID_PROJECT && it.title.contains("Android") })
        assertTrue(items.any { it.contentType == ContentType.MODEL && it.title.contains("Gemma") })
        assertTrue(items.any { it.id == "offer-chatgpt-free" })
        assertTrue(items.any { it.id == "seed-image-prompt-01" })
        assertEquals(8, items.count { it.category?.id == "reels-prompts" })
        assertEquals(2, items.count { it.category?.id == "character-prompts" })
        assertEquals(4, items.count { it.category?.id == "photorealistic-prompts" })
        assertTrue(items.filter { it.category?.id in setOf("reels-prompts", "character-prompts", "photorealistic-prompts") }
            .all { it.description?.containsArabicText() == true && it.originalDescription?.contains(" ") == true })
        assertTrue(items.single { it.id == "seed-character-prompt-01" }.originalDescription?.contains("Replace [CHARACTER AND ERA]") == true)
    }

    private fun String.containsArabicText(): Boolean = any { it in '\u0600'..'\u06FF' }

    private fun item(
        id: String,
        title: String = id,
        description: String? = null,
        tags: List<String> = emptyList(),
        type: ContentType = ContentType.OTHER,
    ) = ContentItem(
        id = id,
        title = title,
        description = description,
        tags = tags,
        contentType = type,
        createdAt = 1L,
        updatedAt = 1L,
    )
}