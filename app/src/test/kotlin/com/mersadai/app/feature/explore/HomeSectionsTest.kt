package com.mersadai.app.feature.explore

import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.Category
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
                item("image-prompt", ContentType.PROMPT, "image-prompts"),
                item("character-prompt", ContentType.PROMPT, "video-prompts").copy(category = Category("character-prompts", "تحويل الشخصيات والعوالم")),
                item("news", ContentType.NEWS, "openai-news", publishedAt = now),
                item("dev-news", ContentType.NEWS, "google-developers", publishedAt = now),
            ),
        )

        assertEquals(
            setOf(HomeSection.LATEST, HomeSection.AI_TOOLS, HomeSection.ANDROID_PROJECTS, HomeSection.MODELS,
                HomeSection.PROMPTS, HomeSection.IMAGE_PROMPTS, HomeSection.CHARACTER_PROMPTS,
                HomeSection.AI_NEWS, HomeSection.DEVELOPER_TOOLS),
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

    @Test
    fun seedContentForImagePromptsAndAiDealsShowsInHomeSections() {
        val offer = item("chatgpt-free", ContentType.AI_TOOL, "ai-offers", sourceUpdatedAt = now)
        val prompt = item("image-1", ContentType.PROMPT, "image-prompts", sourceUpdatedAt = now)

        assertTrue(offer.belongsToHomeSection(HomeSection.AI_TOOLS))
        assertTrue(prompt.belongsToHomeSection(HomeSection.IMAGE_PROMPTS))
        assertTrue(homeSections(listOf(offer, prompt)).map { it.section }.toSet().containsAll(setOf(HomeSection.AI_TOOLS, HomeSection.IMAGE_PROMPTS)))
    }

    @Test
    fun creativeAndroidAppsAndFreePerksAppearInDedicatedSections() {
        val creativeApp = item("moneyprinterturbo", ContentType.ANDROID_PROJECT, "github", sourceUpdatedAt = now)
            .copy(category = Category("android-media-design", "أفضل تطبيقات أندرويد للميديا والتصميم"))
        val freePerk = item("google-ai-studio", ContentType.AI_TOOL, "official-free-perks", sourceUpdatedAt = now)
            .copy(category = Category("free-perks", "فرص وأرصدة مجانية"))

        assertTrue(creativeApp.belongsToHomeSection(HomeSection.ANDROID_MEDIA_DESIGN))
        assertTrue(freePerk.belongsToHomeSection(HomeSection.FREE_PERKS))
        val sections = homeSections(listOf(creativeApp, freePerk)).map { it.section }.toSet()
        assertTrue(sections.contains(HomeSection.ANDROID_MEDIA_DESIGN))
        assertTrue(sections.contains(HomeSection.FREE_PERKS))
    }

    @Test
    fun promptSubcategoriesKeepCreativeStylesAndExcludeOnlyEmptyAbstractBackgrounds() {
        val reels = item("reel-1", ContentType.PROMPT, "curated-prompts").copy(category = Category("reels-prompts", "ريلز"))
        val character = item("avatar-1", ContentType.PROMPT, "curated-prompts").copy(
            title = "Surreal cyberpunk character transformation",
            category = Category("character-prompts", "تحويل الشخصيات والعوالم"),
        )
        val photorealistic = item("photo-1", ContentType.PROMPT, "curated-prompts").copy(category = Category("photorealistic-prompts", "صور واقعية"))
        val excluded = reels.copy(id = "abstract-1", originalDescription = "Abstract shapes background, no subject")

        assertTrue(reels.belongsToHomeSection(HomeSection.REELS_PROMPTS))
        assertFalse(reels.belongsToHomeSection(HomeSection.PHOTOREALISTIC_PROMPTS))
        assertTrue(character.belongsToHomeSection(HomeSection.CHARACTER_PROMPTS))
        assertFalse(character.hasExcludedPromptStyle())
        assertTrue(photorealistic.belongsToHomeSection(HomeSection.PHOTOREALISTIC_PROMPTS))
        assertFalse(excluded.belongsToHomeSection(HomeSection.REELS_PROMPTS))
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