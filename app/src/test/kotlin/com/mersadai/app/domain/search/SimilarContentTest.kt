package com.mersadai.app.domain.search

import com.mersadai.app.domain.model.Category
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class SimilarContentTest {
    @Test
    fun prefersSharedPipelineAndTagsAndExcludesCurrentItemAndOtherTypes() {
        val current = item("current", "Image generation", ContentType.MODEL).copy(
            category = Category("models", "Models"),
            source = Source("hf-models", "Hugging Face"),
            tags = listOf("diffusers", "text-to-image"),
            pipelineTag = "text-to-image",
        )
        val tagMatch = current.copy(id = "tag", title = "Diffusion", pipelineTag = "text-to-video")
        val sourceOnly = item("source", "Other task", ContentType.MODEL).copy(source = current.source)
        val wrongType = current.copy(id = "wrong-type", contentType = ContentType.AI_TOOL)

        assertEquals(
            listOf("tag", "source"),
            SimilarContent.find(current, listOf(sourceOnly, wrongType, tagMatch, current)).map(ContentItem::id),
        )
    }

    @Test
    fun sameCategoryAndTypeAreRelatedWithoutExternalServices() {
        val current = item("repo-1", "Compose widgets", ContentType.ANDROID_PROJECT).copy(
            category = Category("android", "Android"),
            source = Source("github", "GitHub"),
        )
        val candidate = item("repo-2", "Compose navigation", ContentType.ANDROID_PROJECT).copy(
            category = Category("android", "Android"),
            source = Source("github", "GitHub"),
        )

        assertEquals(listOf("repo-2"), SimilarContent.find(current, listOf(candidate)).map(ContentItem::id))
        assertEquals(emptyList<ContentItem>(), SimilarContent.find(current, listOf(candidate), limit = 0))
    }

    private fun item(id: String, title: String, type: ContentType) = ContentItem(
        id = id,
        title = title,
        contentType = type,
        createdAt = 1L,
        updatedAt = 1L,
    )
}