package com.mersadai.app.feature.explore

import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSectionsTest {
    private val now = 1_700_000_000_000L

    @Test
    fun realSourcesMapToExpectedSectionsAndEmptySectionsAreOmitted() {
        val sections = homeSections(
            listOf(
                item("space", ContentType.AI_TOOL, "hf-spaces", sourceUpdatedAt = now),
                item("repo", ContentType.ANDROID_PROJECT, "github", pushedAt = now),
                item("model", ContentType.MODEL, "hf-models", sourceUpdatedAt = now),
                item("prompt", ContentType.PROMPT, "prompts-chat"),
                item("news", ContentType.NEWS, "openai-news", publishedAt = now),
                item("dev-news", ContentType.NEWS, "google-developers", publishedAt = now),
            ),
        )

        assertEquals(
            setOf(HomeSection.LATEST, HomeSection.AI_TOOLS, HomeSection.ANDROID_PROJECTS, HomeSection.MODELS,
                HomeSection.PROMPTS, HomeSection.AI_NEWS, HomeSection.DEVELOPER_TOOLS),
            sections.map(HomeSectionContent::section).toSet(),
        )
        assertTrue(sections.all { it.items.isNotEmpty() })
    }

    @Test
    fun unknownDatesDoNotAppearAsLatestOrReceiveNewBadge() {
        val item = item("prompt", ContentType.PROMPT, "prompts-chat", createdAt = now)

        assertFalse(item.isNewAt(now))
        assertTrue(homeSections(listOf(item)).none { it.section == HomeSection.LATEST })
        assertTrue(homeSections(emptyList()).isEmpty())
    }

    @Test
    fun newBadgeUsesOnlyARecentRealSourceTimestamp() {
        val recent = item("recent", ContentType.NEWS, "openai-news", publishedAt = now - 60_000)
        val old = item("old", ContentType.NEWS, "openai-news", publishedAt = now - 8 * 24 * 60 * 60 * 1000L)
        val future = item("future", ContentType.NEWS, "openai-news", publishedAt = now + 1)

        assertTrue(recent.isNewAt(now))
        assertFalse(old.isNewAt(now))
        assertFalse(future.isNewAt(now))
    }

    private fun item(
        id: String,
        type: ContentType,
        sourceId: String,
        publishedAt: Long? = null,
        pushedAt: Long? = null,
        sourceUpdatedAt: Long? = null,
        createdAt: Long = 1,
    ) = ContentItem(
        id = id,
        title = id,
        contentType = type,
        source = Source(sourceId, sourceId),
        publishedAt = publishedAt,
        pushedAt = pushedAt,
        sourceUpdatedAt = sourceUpdatedAt,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}