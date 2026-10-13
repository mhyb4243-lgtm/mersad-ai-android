package com.mersadai.app.data.remote

import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import com.mersadai.app.domain.model.VerificationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceParsersTest {
    @Test
    fun parsesLatestGitHubReleaseWithDirectApkDownloadLink() {
        val item = SourceParsers.githubLatestRelease(
            """{"tag_name":"v2.0","name":"Release 2","html_url":"https://github.com/org/app/releases/tag/v2.0","published_at":"2025-01-01T00:00:00Z","body":"Bug fixes","assets":[{"name":"app.apk","browser_download_url":"https://github.com/org/app/releases/download/v2.0/app.apk"}]}""",
            "org/app",
            now = 10L,
        )

        assertEquals("android-media-design", item.category?.id)
        assertEquals(ContentType.ANDROID_PROJECT, item.contentType)
        assertEquals("https://github.com/org/app/releases/download/v2.0/app.apk", item.url)
        assertTrue(item.description.orEmpty().contains("app.apk:"))
        assertEquals("github-release:org/app:v2.0", item.externalId)
    }

    @Test
    fun parsesCivitaiImageWithOnlyPublishedMetadata() {
        val item = SourceParsers.civitaiImages(
            """{"items":[{"id":42,"url":"https://image.civitai.com/42.jpg","createdAt":"2025-01-01T00:00:00Z","username":"artist","meta":{"prompt":"A cinematic portrait","Model":"Flux Realism","negativePrompt":"blurry","seed":7,"steps":24}}]}""",
        ).single()

        assertEquals("civitai:image:42", item.externalId)
        assertEquals(ContentType.PROMPT, item.contentType)
        assertEquals("A cinematic portrait", item.originalDescription)
        assertEquals("social-portraits", item.category?.id)
        assertEquals("https://image.civitai.com/42.jpg", item.thumbnailUrl)
        assertEquals("https://civitai.com/images/42", item.sourceUrl)
        assertEquals(1_735_689_600_000L, item.publishedAt)
        assertEquals("Flux Realism", item.modelName)
        assertTrue(item.generationSettings.orEmpty().contains("negativePrompt"))
        assertEquals("artist", item.author)
        assertEquals("civitai", item.source?.id)
    }

    @Test
    fun keepsImageWhenPromptAndDateAreUnavailableButSkipsEmptyOrInvalidImage() {
        val items = SourceParsers.civitaiImages(
            """{"items":[
                {"id":1,"url":"https://image.civitai.com/1.jpg","createdAt":"not-a-date","meta":{}},
                {"id":2,"url":"http://image.civitai.com/2.jpg","meta":{}},
                {"id":3,"meta":{"prompt":"A prompt without an image"}},
                {"id":4,"url":"https://images.example/4.jpg","meta":{}}
            ]}""",
        )

        assertEquals(2, items.size)
        val imageWithoutPrompt = items.single { it.thumbnailUrl != null }
        assertNull(imageWithoutPrompt.originalDescription)
        assertNull(imageWithoutPrompt.publishedAt)
        assertNull(imageWithoutPrompt.createdAt)
        assertEquals("A prompt without an image", items.single { it.thumbnailUrl == null }.originalDescription)
    }

    @Test
    fun rejectsMalformedCivitaiResponse() {
        assertTrue(runCatching { SourceParsers.civitaiImages("""{"data":[]}""") }.isFailure)
    }

    @Test
    fun parsesGitHubFieldsAndToleratesNullDescriptionAndLicense() {
        val item = SourceParsers.githubRepositories(GITHUB_FIXTURE, now = 10L).single()

        assertEquals("github:42", item.externalId)
        assertEquals("octo/android-app", item.title)
        assertEquals("https://github.com/octo/android-app", item.url)
        assertEquals("Kotlin", item.language)
        assertEquals(17L, item.starsCount)
        assertTrue((item.pushedAt ?: 0L) > 10L)
        assertEquals("octo", item.author)
        assertNull(item.description)
        assertNull(item.license)
        assertEquals(FreeStatus.UNKNOWN, item.freeStatus)
        assertEquals(VerificationLevel.OFFICIAL, item.verificationLevel)
    }

    @Test
    fun parsesHuggingFaceModelAndDoesNotInventLicenseOrDescription() {
        val item = SourceParsers.huggingFaceModels(MODEL_FIXTURE, now = 10L).single()

        assertEquals("hf:model:org/model", item.externalId)
        assertEquals(ContentType.MODEL, item.contentType)
        assertEquals("معالجة النصوص", item.pipelineCategory)
        assertEquals("apache-2.0", item.license)
        assertEquals("transformers", item.libraryName)
        assertEquals(false, item.gated)
        assertEquals(false, item.isPrivate)
        assertEquals("transformers", item.libraryName)
        assertNull(item.description)
        assertEquals(FreeStatus.OPEN_SOURCE, item.freeStatus)
    }

    @Test
    fun openRouterMarksOnlyZeroPricedModelsAsFreeAndRequiresAnApiKey() {
        val items = SourceParsers.openRouterModels(OPENROUTER_FIXTURE, now = 1_800_000_000_000L)
        val free = items.single { it.externalId == "openrouter:provider/free-model:free" }
        val paid = items.single { it.externalId == "openrouter:provider/paid-model" }

        assertEquals(FreeStatus.FREE_TIER, free.freeStatus)
        assertEquals("https://openrouter.ai/provider/free-model:free", free.url)
        assertEquals("https://openrouter.ai/provider/free-model:free", free.sourceUrl)
        assertEquals("openrouter", free.source?.id)
        assertEquals(true, free.requiresAccount)
        assertEquals(true, free.requiresApiKey)
        assertEquals(FreeStatus.UNKNOWN, paid.freeStatus)
        assertFalse(paid.isFreeNow())
        assertNull(free.freeLimit)
    }

    @Test
    fun parsesSpaceMetadataAndKeepsOptionalCardFieldsNull() {
        val item = SourceParsers.huggingFaceSpaces(SPACE_FIXTURE, now = 10L).single()

        assertEquals("hf:space:org/demo", item.externalId)
        assertEquals("Demo", item.title)
        assertEquals("https://huggingface.co/spaces/org/demo", item.url)
        assertEquals("gradio", item.sdk)
        assertEquals("Hugging Face", item.source?.name)
        assertEquals(ContentType.AI_TOOL, item.contentType)
        assertNull(item.description)
        assertNull(item.emoji)
        assertEquals(FreeStatus.UNKNOWN, item.freeStatus)
    }

    @Test
    fun genericSpaceWithoutAiTaskTagsIsNotClassifiedAsAnAiTool() {
        val item = SourceParsers.huggingFaceSpaces(
            """[{"id":"org/demo","sdk":"gradio","tags":["gradio"]}]""",
            now = 10L,
        ).single()

        assertEquals(ContentType.OTHER, item.contentType)
        assertNull(item.category)
    }

    @Test
    fun parsesRssCdataNamespacesDatesAndRemovesMarkup() {
        val item = SourceParsers.rssFeed(RSS_FIXTURE, "openai-news", "OpenAI News", "https://openai.com/news/rss.xml", now = 10L).single()

        assertEquals("A & B", item.title)
        assertEquals("https://openai.com/news/example", item.url)
        assertEquals("Useful update", item.description)
        assertTrue(item.originalDescription?.contains("Useful") == true)
        assertEquals("https://openai.com/news/image.jpg", item.thumbnailUrl)
        assertEquals("openai-news:post-1", item.externalId)
        assertTrue((item.publishedAt ?: 0L) > 10L)
        assertEquals(VerificationLevel.OFFICIAL, item.verificationLevel)
    }

    @Test
    fun parsesPromptsChatWithoutCreatingPerRowUrl() {
        val item = SourceParsers.promptsChat(PROMPTS_FIXTURE, now = 10L).single()

        assertEquals("prompts-chat:7", item.externalId)
        assertEquals("Writing helper", item.title)
        assertEquals("Write a short note.", item.originalDescription)
        assertEquals("https://huggingface.co/datasets/fka/prompts.chat", item.url)
        assertEquals("contributor", item.contributor)
        assertEquals("CC0-1.0", item.license)
        assertEquals(1L, SourceParsers.promptsChatTotalCount(PROMPTS_FIXTURE))
        assertEquals(VerificationLevel.COMMUNITY_SOURCE, item.verificationLevel)
        assertEquals(FreeStatus.UNKNOWN, item.freeStatus)
    }

    @Test
    fun parsesStableDiffusionImagePromptsAsTheirOwnFunctionalCategory() {
        val item = SourceParsers.imageGenerationPrompts(IMAGE_PROMPTS_FIXTURE, now = 10L).single()

        assertEquals("image-prompts:3", item.externalId)
        assertEquals("وصف لتوليد صورة 4", item.title)
        assertEquals("A cinematic landscape", item.originalDescription)
        assertEquals("image-prompts", item.source?.id)
        assertEquals(listOf("text-to-image", "stable-diffusion", "image-generation"), item.tags)
        assertNull(item.publishedAt)
        assertNull(item.sourceUpdatedAt)
    }

    @Test
    fun parsesLiveVideoPromptsAndClassifiesCharacterTransformationTrends() {
        val item = SourceParsers.videoGenerationPrompts(VIDEO_PROMPTS_FIXTURE, now = 10L).single()

        assertEquals("video-prompts:8", item.externalId)
        assertEquals("فكرة تحول شخصية #9", item.title)
        assertEquals("character-prompts", item.category?.id)
        assertTrue(item.originalDescription.orEmpty().startsWith("Vertical 9:16 cyberpunk avatar transformation"))
        assertTrue(item.originalDescription.orEmpty().endsWith("Lower-third overlay: Arabic text \"محمد ابوهادي\" with green WhatsApp icon and \"+967776577658\""))
        assertEquals("AI Video Prompt Book 2026", item.source?.name)
        assertNull(item.publishedAt)
        assertNull(item.sourceUpdatedAt)
        assertEquals("cc-by-4.0", item.license)
        assertTrue(item.tags.containsAll(listOf("ai-video", "viral-trend", "Veo", "Sora", "Kling", "9:16")))
        assertEquals(698L, SourceParsers.videoGenerationPromptsTotalCount(VIDEO_PROMPTS_FIXTURE))
    }

    @Test
    fun malformedRootIsRejectedButMissingOptionalFieldsAreAccepted() {
        assertTrue(SourceParsers.huggingFaceModels("[{}]").isEmpty())
        assertTrue(runCatching { SourceParsers.githubRepositories("[]") }.isFailure)
        assertEquals("معالجة الفيديو", SourceParsers.pipelineCategory("text-to-video"))
        assertEquals("تحويل الكلام إلى نص", SourceParsers.pipelineCategory("text-to-speech"))
    }

    private companion object {
        const val GITHUB_FIXTURE = """{"total_count":1,"incomplete_results":false,"items":[{"id":42,"name":"android-app","full_name":"octo/android-app","description":null,"html_url":"https://github.com/octo/android-app","owner":{"login":"octo","avatar_url":"https://avatars.githubusercontent.com/u/42"},"language":"Kotlin","stargazers_count":17,"forks_count":2,"open_issues_count":1,"license":{"spdx_id":"NOASSERTION"},"topics":["android"],"created_at":"2024-01-01T00:00:00Z","updated_at":"2025-01-01T00:00:00Z","pushed_at":"2025-01-02T00:00:00Z","archived":false,"fork":false}]}"""
        const val MODEL_FIXTURE = """[{"id":"org/model","author":"org","createdAt":"2024-01-01T00:00:00Z","lastModified":"2025-01-01T00:00:00Z","downloads":100,"likes":5,"trendingScore":2.5,"pipeline_tag":"text-generation","library_name":"transformers","tags":["license:apache-2.0"],"gated":false,"private":false}]"""
        const val OPENROUTER_FIXTURE = """{"data":[{"id":"provider/free-model:free","name":"Free route","description":"A free hosted route.","created":1740000000,"architecture":{"modality":"text->text"},"pricing":{"prompt":"0","completion":"0"}},{"id":"provider/paid-model","name":"Paid route","created":1740000000,"pricing":{"prompt":"0.000001","completion":"0.000002"}}]}"""
        const val SPACE_FIXTURE = """[{"id":"org/demo","author":"org","lastModified":"2025-01-01T00:00:00Z","likes":9,"trendingScore":3.5,"sdk":"gradio","tags":["llm","gradio"],"cardData":{"title":"Demo"},"createdAt":"2024-01-01T00:00:00Z"}]"""
        const val RSS_FIXTURE = """<?xml version="1.0"?><rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:media="http://search.yahoo.com/mrss/"><channel><item><title><![CDATA[A & B]]></title><link>https://openai.com/news/example</link><description><![CDATA[<p>Useful <b>update</b></p>]]></description><media:thumbnail url="https://openai.com/news/image.jpg"/><pubDate>Mon, 01 Jan 2024 12:00:00 +0000</pubDate><guid>post-1</guid><category>AI</category><dc:creator>OpenAI</dc:creator></item></channel></rss>"""
        const val PROMPTS_FIXTURE = """{"num_rows_total":1,"rows":[{"row_idx":7,"row":{"act":"Writing helper","prompt":"Write a short note.","for_devs":false,"type":"writing","contributor":"contributor"}}]}"""
        const val IMAGE_PROMPTS_FIXTURE = """{"rows":[{"row_idx":3,"row":{"Prompt":"A cinematic landscape"}}]}"""
        const val VIDEO_PROMPTS_FIXTURE = """{"num_rows_total":698,"rows":[{"row_idx":8,"row":{"id":9,"category":"pack:character-transformation","group":"Character transformation trend","prompt":"Vertical 9:16 cyberpunk avatar transformation"}}]}"""
    }
}