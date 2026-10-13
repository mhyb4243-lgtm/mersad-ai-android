package com.mersadai.app.feature.explore

import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.Category
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import com.mersadai.app.domain.model.Source
import com.mersadai.app.data.local.SeedContentProvider
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSectionsTest {
    private val now = 1_700_000_000_000L

    @Test
    fun promptFreshnessSeparatesPublicationFromDiscoveryAndDoesNotRefreshOldSourceDates() {
        assertEquals(
            PromptFreshness.SOURCE_NEW,
            item("source-new", ContentType.PROMPT, "civitai", publishedAt = now - 1_000, discoveredAt = now).promptFreshness(now),
        )
        assertEquals(
            PromptFreshness.DISCOVERED_RECENTLY,
            item("discovered", ContentType.PROMPT, "prompts-chat", discoveredAt = now - 1_000).promptFreshness(now),
        )
        assertNull(
            item(
                "old-source",
                ContentType.PROMPT,
                "civitai",
                publishedAt = now - 30L * 24 * 60 * 60 * 1000,
                discoveredAt = now,
            ).promptFreshness(now),
        )
        assertNull(item("unknown", ContentType.PROMPT, "prompts-chat").promptFreshness(now))
    }

    @Test
    fun promptRadarKeepsOnlySourceIdentifiedItemsAndFiltersByTextCategorySourceModelAndLocalFavorite() {
        val civitai = ContentItem(
            id = "civitai:image:42",
            title = "Cinematic landscape",
            originalDescription = "A cinematic blue-hour landscape",
            contentType = ContentType.PROMPT,
            source = Source("civitai", "Civitai"),
            category = Category("photorealistic-prompts", "واقعي"),
            modelName = "Flux",
            publishedAt = now - 1_000,
        )
        val datasetPrompt = item("video-prompts:8", ContentType.PROMPT, "video-prompts", discoveredAt = now)
        val mergedFeedPrompt = item("remote-prompts:photo-1", ContentType.PROMPT, "remote-prompts")
        val seededPrompt = item("seed-image-prompt-01", ContentType.PROMPT, "image-prompts")
        val radar = promptRadarItems(listOf(civitai, datasetPrompt, mergedFeedPrompt, seededPrompt))

        assertEquals(listOf("civitai:image:42", "video-prompts:8"), radar.map(ContentItem::id))
        assertEquals(
            listOf("civitai:image:42"),
            filterPromptRadarItems(
                radar,
                PromptRadarFilters(
                    query = "blue-hour",
                    categoryId = "photorealistic-prompts",
                    sourceId = "civitai",
                    modelName = "Flux",
                    savedOnly = true,
                ),
                favoriteIds = setOf("civitai:image:42"),
            ).map(ContentItem::id),
        )
        assertTrue(
            filterPromptRadarItems(radar, PromptRadarFilters(savedOnly = true), favoriteIds = emptySet()).isEmpty(),
        )
    }

    @Test
    fun promptCopyUsesOnlyTheOriginalPromptAndNeverFallsBackToDescription() {
        val prompt = ContentItem(
            id = "prompt",
            title = "Prompt",
            description = "A descriptive label, not a prompt",
            originalDescription = "  The exact original prompt.  ",
            contentType = ContentType.PROMPT,
        )
        val missing = prompt.copy(originalDescription = null)

        assertEquals("  The exact original prompt.  ", prompt.originalPromptText())
        assertNull(missing.originalPromptText())
    }

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
    fun newTodayBadgeUsesTheLocalCalendarDate() {
        val zone = ZoneId.systemDefault()
        val todayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterday = Instant.ofEpochMilli(todayStart).atZone(zone).minusDays(1)
            .toInstant().toEpochMilli()

        assertTrue(item("today", ContentType.PROMPT, "remote-prompts", publishedAt = todayStart).isNewToday(now))
        assertFalse(item("yesterday", ContentType.PROMPT, "remote-prompts", publishedAt = yesterday).isNewToday(now))
        assertFalse(item("future", ContentType.PROMPT, "remote-prompts", publishedAt = now + 1).isNewToday(now))
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
    fun openRouterFreeApiModelsHaveTheirOwnSectionAndPaidModelsAreExcluded() {
        val freeModel = item("openrouter-free", ContentType.MODEL, "openrouter", publishedAt = now)
            .copy(freeStatus = FreeStatus.FREE_TIER)
        val unknownModel = item("openrouter-paid", ContentType.MODEL, "openrouter", publishedAt = now)

        assertTrue(freeModel.belongsToHomeSection(HomeSection.API_FREE_MODELS))
        assertFalse(unknownModel.belongsToHomeSection(HomeSection.API_FREE_MODELS))
        assertEquals(
            listOf(freeModel.id),
            homeSections(listOf(freeModel, unknownModel))
                .single { it.section == HomeSection.API_FREE_MODELS }
                .items.map(ContentItem::id),
        )
    }

    @Test
    fun unverifiedOffersAreNotSeededAsRealOpportunities() {
        val seededItems = SeedContentProvider.items(now) + SeedContentProvider.additiveItems(now)

        assertFalse(seededItems.any { item ->
            item.id.startsWith("creator-deal-") || item.id.startsWith("free-perk-") || item.id.startsWith("offer-")
        })
    }

    @Test
    fun offlineSeedItemsHaveNoInventedSourceDates() {
        assertTrue(
            (SeedContentProvider.items(now) + SeedContentProvider.additiveItems(now)).all { item ->
                item.createdAt == null && item.updatedAt == null && item.lastVerifiedAt == null &&
                    item.sourceUpdatedAt == null && item.publishedAt == null && item.pushedAt == null
            },
        )
    }

    @Test
    fun publishedContentIsOrderedNewestFirstAndRemotePromptsHaveASection() {
        val older = item("older", ContentType.PROMPT, "remote-prompts", publishedAt = now - 2_000L)
            .copy(category = Category("ai-prompts", "أوامر توليد من prompts.chat"))
        val newer = item("newer", ContentType.PROMPT, "remote-prompts", publishedAt = now - 1_000L)
            .copy(category = Category("ai-prompts", "أوامر توليد من prompts.chat"))

        val promptSection = homeSections(listOf(older, newer)).single { it.section == HomeSection.PROMPTS }

        assertEquals(listOf("newer", "older"), promptSection.items.map(ContentItem::id))
    }

    @Test
    fun duplicateExternalItemsAppearOnceInEachHomeSection() {
        val original = item("remote-one", ContentType.NEWS, "openai-news", publishedAt = now)
            .copy(externalId = "openai:post-1")
        val duplicate = original.copy(id = "room-alias", title = "Duplicate title")

        val latest = homeSections(listOf(original, duplicate)).single { it.section == HomeSection.LATEST }

        assertEquals(listOf("remote-one"), latest.items.map(ContentItem::id))
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

    @Test
    fun btsAndReelsSeedsKeepVideoSignatureAndThirtySecondSceneStructure() {
        val seeds = SeedContentProvider.additiveItems(now)
        val btsItems = seeds.filter { it.category?.id == "bts-filmmaking" }
        val videoPrompts = seeds.filter { it.category?.id in setOf("reels-prompts", "bts-filmmaking") }
        val thirtySecondAd = seeds.single { it.id == "seed-reels-thirty-second-ad" }

        assertEquals(4, btsItems.size)
        assertTrue(btsItems.all { it.belongsToHomeSection(HomeSection.BTS_FILMMAKING) })
        assertTrue(videoPrompts.all { it.originalDescription.orEmpty().endsWith("Lower-third overlay: Arabic text \"محمد ابوهادي\" with green WhatsApp icon and \"+967776577658\"") })
        assertTrue(thirtySecondAd.originalDescription.orEmpty().contains("Scene 1 (0-10s)"))
        assertTrue(thirtySecondAd.originalDescription.orEmpty().contains("Scene 2 (10-20s)"))
        assertTrue(thirtySecondAd.originalDescription.orEmpty().contains("Scene 3 (20-30s)"))
        assertTrue(thirtySecondAd.originalDescription.orEmpty().contains("Consistent Character Parameters"))
    }

    @Test
    fun newCinematicCategoriesHaveOfflineSeedsAndDedicatedSections() {
        val expected = mapOf(
            "action-vfx" to HomeSection.ACTION_VFX,
            "comedy-satire" to HomeSection.COMEDY_SATIRE,
            "character-animation" to HomeSection.CHARACTER_ANIMATION,
            "cinematic-bts" to HomeSection.CINEMATIC_BTS,
        )
        val seeds = SeedContentProvider.additiveItems(now)

        expected.forEach { (categoryId, section) ->
            val prompt = seeds.single { it.category?.id == categoryId }
            assertTrue(prompt.belongsToHomeSection(section))
            assertTrue(prompt.originalDescription.orEmpty().contains("Lower-third overlay:"))
        }
    }

    @Test
    fun factVerseCombinesScienceArticlesAndEnglishCreativePrompts() {
        val article = item("science-article", ContentType.NEWS, "factverse-sciencedaily", publishedAt = now)
            .copy(category = Category("factverse", "🌌 FactVerse (Science, Future & AI)"))
        val prompt = item("science-prompt", ContentType.PROMPT, "remote-prompts", publishedAt = now - 1)
            .copy(category = Category("factverse", "🌌 FactVerse (Science, Future & AI)"))

        assertTrue(article.belongsToHomeSection(HomeSection.FACTVERSE))
        assertTrue(prompt.belongsToHomeSection(HomeSection.FACTVERSE))
        assertEquals(
            listOf("science-article", "science-prompt"),
            homeSections(listOf(prompt, article)).single { it.section == HomeSection.FACTVERSE }
                .items.map(ContentItem::id),
        )
    }

    private fun item(
        id: String,
        type: ContentType,
        sourceId: String,
        publishedAt: Long? = null,
        pushedAt: Long? = null,
        sourceUpdatedAt: Long? = null,
        discoveredAt: Long? = null,
        createdAt: Long = 1,
    ) = ContentItem(
        id = id,
        title = id,
        contentType = type,
        source = Source(sourceId, sourceId),
        publishedAt = publishedAt,
        pushedAt = pushedAt,
        sourceUpdatedAt = sourceUpdatedAt,
        discoveredAt = discoveredAt,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}