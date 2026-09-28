package com.mersadai.app.data.remote

import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import com.mersadai.app.domain.model.VerificationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceParsersTest {
    @Test
    fun parsesGitHubFieldsAndToleratesNullDescriptionAndLicense() {
        val item = SourceParsers.githubRepositories(GITHUB_FIXTURE, now = 10L).single()

        assertEquals("github:42", item.externalId)
        assertEquals("octo/android-app", item.title)
        assertEquals("https://github.com/octo/android-app", item.url)
        assertEquals("Kotlin", item.language)
        assertEquals(17L, item.starsCount)
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
        assertEquals("Text", item.pipelineCategory)
        assertEquals("apache-2.0", item.license)
        assertNull(item.description)
        assertEquals(FreeStatus.UNKNOWN, item.freeStatus)
    }

    @Test
    fun parsesSpaceMetadataAndKeepsOptionalCardFieldsNull() {
        val item = SourceParsers.huggingFaceSpaces(SPACE_FIXTURE, now = 10L).single()

        assertEquals("hf:space:org/demo", item.externalId)
        assertEquals("Demo", item.title)
        assertEquals("https://huggingface.co/spaces/org/demo", item.url)
        assertEquals("gradio", item.sdk)
        assertNull(item.description)
        assertNull(item.emoji)
        assertEquals(FreeStatus.UNKNOWN, item.freeStatus)
    }

    @Test
    fun parsesRssCdataNamespacesDatesAndRemovesMarkup() {
        val item = SourceParsers.rssFeed(RSS_FIXTURE, "openai-news", "OpenAI News", "https://openai.com/news/rss.xml", now = 10L).single()

        assertEquals("A & B", item.title)
        assertEquals("https://openai.com/news/example", item.url)
        assertEquals("Useful update", item.description)
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
        assertEquals("https://prompts.chat/", item.url)
        assertEquals("contributor", item.contributor)
        assertEquals(VerificationLevel.COMMUNITY_SOURCE, item.verificationLevel)
        assertEquals(FreeStatus.UNKNOWN, item.freeStatus)
    }

    @Test
    fun malformedRootIsRejectedButMissingOptionalFieldsAreAccepted() {
        assertTrue(SourceParsers.huggingFaceModels("[{}]").isEmpty())
        assertTrue(runCatching { SourceParsers.githubRepositories("[]") }.isFailure)
        assertEquals("Video", SourceParsers.pipelineCategory("text-to-video"))
        assertEquals("Speech", SourceParsers.pipelineCategory("text-to-speech"))
    }

    private companion object {
        const val GITHUB_FIXTURE = """{"total_count":1,"incomplete_results":false,"items":[{"id":42,"name":"android-app","full_name":"octo/android-app","description":null,"html_url":"https://github.com/octo/android-app","owner":{"login":"octo","avatar_url":"https://avatars.githubusercontent.com/u/42"},"language":"Kotlin","stargazers_count":17,"forks_count":2,"open_issues_count":1,"license":{"spdx_id":"NOASSERTION"},"topics":["android"],"created_at":"2024-01-01T00:00:00Z","updated_at":"2025-01-01T00:00:00Z","pushed_at":"2025-01-02T00:00:00Z","archived":false,"fork":false}]}"""
        const val MODEL_FIXTURE = """[{"id":"org/model","author":"org","createdAt":"2024-01-01T00:00:00Z","lastModified":"2025-01-01T00:00:00Z","downloads":100,"likes":5,"trendingScore":2.5,"pipeline_tag":"text-generation","library_name":"transformers","tags":["license:apache-2.0"],"gated":false,"private":false}]"""
        const val SPACE_FIXTURE = """[{"id":"org/demo","author":"org","lastModified":"2025-01-01T00:00:00Z","likes":9,"trendingScore":3.5,"sdk":"gradio","tags":["chat"],"createdAt":"2024-01-01T00:00:00Z"}]"""
        const val RSS_FIXTURE = """<?xml version="1.0"?><rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><item><title><![CDATA[A & B]]></title><link>https://openai.com/news/example</link><description><![CDATA[<p>Useful <b>update</b></p>]]></description><pubDate>Mon, 01 Jan 2024 12:00:00 +0000</pubDate><guid>post-1</guid><category>AI</category><dc:creator>OpenAI</dc:creator></item></channel></rss>"""
        const val PROMPTS_FIXTURE = """{"num_rows_total":1,"rows":[{"row_idx":7,"row":{"act":"Writing helper","prompt":"Write a short note.","for_devs":false,"type":"writing","contributor":"contributor"}}]}"""
    }
}