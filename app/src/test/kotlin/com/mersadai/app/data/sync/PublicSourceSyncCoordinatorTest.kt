package com.mersadai.app.data.sync

import com.mersadai.app.data.local.CategoryEntity
import com.mersadai.app.data.local.ItemEntity
import com.mersadai.app.data.local.SourceEntity
import com.mersadai.app.data.local.SyncStateEntity
import com.mersadai.app.data.remote.SourceHttpResponse
import com.mersadai.app.data.remote.SourceHttpTransport
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicSourceSyncCoordinatorTest {
    private val now = 1_700_000_000_000L

    @Test
    fun rateLimitedSourcesFailIndependentlyAndLaterSourcesStillSync() = runBlocking {
        val store = FakeStore()
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        val transport = SourceHttpTransport { url, headers ->
            calls += url to headers
            when {
                url.startsWith("https://api.github.com") -> response(403, mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "1700003600"))
                url.contains("/api/models") -> response(429, mapOf("Retry-After" to "120"))
                else -> successFor(url)
            }
        }

        val result = PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        assertFalse(result.hasTransientFailure)
        assertEquals(10, store.states.size)
        assertEquals("FAILURE", store.states.getValue("github").state)
        assertEquals(403, store.states.getValue("github").lastHttpStatus)
        assertEquals(now + 3_600_000, store.states.getValue("github").nextAllowedSyncAt)
        assertEquals("FAILURE", store.states.getValue("hf-models").state)
        assertEquals(now + 120_000, store.states.getValue("hf-models").nextAllowedSyncAt)
        assertEquals("SUCCESS", store.states.getValue("hf-spaces").state)
        assertEquals("SUCCESS", store.states.getValue("prompts-chat").state)
        assertEquals("SUCCESS", store.states.getValue("remote-prompts").state)
        assertTrue(calls.any { it.second["Accept"] == "application/vnd.github+json" })
        assertTrue(calls.any { it.second["X-GitHub-Api-Version"] == "2022-11-28" })
    }

    @Test
    fun githubUsesAtMostThreeUpdatedQueriesAndPersistsRealItems() = runBlocking {
        val store = FakeStore()
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        val transport = SourceHttpTransport { url, headers ->
            calls += url to headers
            successFor(url)
        }

        PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        val githubCalls = calls.filter { it.first.startsWith("https://api.github.com") }
        assertEquals(3, githubCalls.size)
        assertTrue(githubCalls.all { it.first.contains("sort=updated") && it.first.contains("per_page=20") })
        assertTrue(githubCalls.any { it.first.contains("topic%3Aphotography") })
        assertTrue(githubCalls.any { it.first.contains("topic%3Aphoto-editing") })
        assertTrue(githubCalls.any { it.first.contains("topic%3Ainvitation") })
        assertTrue(githubCalls.any { it.first.contains("topic%3Aposter-design") })
        assertTrue(githubCalls.all { it.second["User-Agent"]?.contains("MersadAI-Android") == true })
        assertEquals(3, store.items.count { it.externalId?.startsWith("github:") == true })
        assertTrue(store.items.all { it.freeStatus == "UNKNOWN" })
        assertEquals(0L, store.states.getValue("prompts-chat").remoteTotalCount)
    }

    @Test
    fun liveVideoPromptDatasetIsSyncedAndSavedWithItsCreativeCategory() = runBlocking {
        val store = FakeStore()
        val calls = mutableListOf<String>()
        val transport = SourceHttpTransport { url, _ ->
            calls += url
            if (url.contains("hrrcne%2Fai-video-prompt-book-2026")) {
                response(200, body = VIDEO_PROMPTS_FIXTURE)
            } else {
                successFor(url)
            }
        }

        PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        val item = store.items.single { it.externalId == "video-prompts:22" }
        assertEquals("character-prompts", itemCategory(store, item.id))
        assertEquals("cc-by-4.0", item.license)
        assertEquals("SUCCESS", store.states.getValue("video-prompts").state)
        assertEquals(698L, store.states.getValue("video-prompts").remoteTotalCount)
        assertTrue(calls.any { it.contains("offset=598&length=100") })
    }

    @Test
    fun remotePromptFeedMergesWithoutDuplicatesAndAddsVideoRequirements() = runBlocking {
        val store = FakeStore()
        val entry = """{"id":"film-ad","title":"Film Ad","prompt_type":"video-generation","category_id":"bts-filmmaking","category_name":"BTS & Filmmaking","duration_seconds":30,"prompt":"A 30-second commercial starring [CHARACTER]."}"""
        val feed = """{"schema_version":1,"prompts":[$entry,$entry]}"""
        val transport = SourceHttpTransport { url, _ ->
            if (url.contains("remote_prompts.json")) response(200, body = feed) else successFor(url)
        }

        PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        val saved = store.items.single { it.externalId == "remote-prompts:film-ad" }
        assertEquals("bts-filmmaking", itemCategory(store, saved.id))
        assertTrue(saved.originalDescription.orEmpty().contains("Scene 1 (0-10s)"))
        assertTrue(saved.originalDescription.orEmpty().contains("Scene 2 (10-20s)"))
        assertTrue(saved.originalDescription.orEmpty().contains("Scene 3 (20-30s)"))
        assertTrue(saved.originalDescription.orEmpty().contains("Consistent Character Parameters"))
        assertTrue(saved.originalDescription.orEmpty().contains("Lower-third overlay: Arabic text \"محمد ابوهادي\""))
        assertEquals("SUCCESS", store.states.getValue("remote-prompts").state)
    }

    @Test
    fun remoteFeedParsesPublicationDatesAndOfficialDeals() = runBlocking {
        val publishedAt = now - 60_000L
        val feed = """{"schema_version":1,"prompts":[{"id":"fresh-prompt","title":"Fresh prompt","category_id":"ai-prompts","prompt":"A useful prompt.","published_at":$publishedAt}],"sections":[{"id":"ai-deals-and-trials","title":"AI Deals & Trials","items":[{"id":"free-plan","title":"Official free plan","description":"A free plan.","url":"https://example.com/pricing","provider":"Example","free_status":"FREE_TIER","published_at":$publishedAt,"verified_at":$now,"requires_account":true,"requires_payment_card":false,"free_limit":"Daily usage"}]}]}"""
        val transport = SourceHttpTransport { url, _ ->
            if (url.contains("remote_prompts.json")) response(200, body = feed) else successFor(url)
        }
        val store = FakeStore()

        PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        val prompt = store.items.single { it.externalId == "remote-prompts:fresh-prompt" }
        assertEquals(publishedAt, prompt.publishedAt)
        assertEquals("ai-prompts", itemCategory(store, prompt.id))
        val deal = store.items.single { it.externalId == "remote-prompts:free-plan" }
        assertEquals(ContentType.AI_TOOL.name, deal.contentType)
        assertEquals(FreeStatus.FREE_TIER.name, deal.freeStatus)
        assertEquals(publishedAt, deal.publishedAt)
        assertEquals(now, deal.lastVerifiedAt)
        assertEquals("free-perks", itemCategory(store, deal.id))
    }

    @Test
    fun remoteFeedPersistsNewCinematicCategoriesLocally() = runBlocking {
        val entries = listOf(
            "action-vfx" to "Action",
            "comedy-satire" to "Comedy",
            "character-animation" to "Animation",
            "cinematic-bts" to "Cinematic BTS",
        ).mapIndexed { index, (categoryId, title) ->
            """{"id":"category-$index","title":"$title","prompt_type":"video-generation","category_id":"$categoryId","category_name":"$title","duration_seconds":10,"prompt":"A short cinematic video prompt."}"""
        }
        val feed = """{"schema_version":1,"prompts":[${entries.joinToString(",")}] }"""
        val transport = SourceHttpTransport { url, _ ->
            if (url.contains("remote_prompts.json")) response(200, body = feed) else successFor(url)
        }
        val store = FakeStore()

        PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        assertEquals(
            setOf("action-vfx", "comedy-satire", "character-animation", "cinematic-bts"),
            store.items.filter { it.externalId?.startsWith("remote-prompts:") == true }
                .mapNotNull { itemCategory(store, it.id) }
                .toSet(),
        )
    }

    @Test
    fun notModifiedKeepsCachedItemsAndCountsAsSuccess() = runBlocking {
        val store = FakeStore()
        val existing = item("cached")
        store.items += existing
        val transport = SourceHttpTransport { _, _ -> response(304, mapOf("ETag" to "\"unchanged\"")) }

        PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        assertEquals(listOf(existing), store.items)
        assertEquals(now, store.states.getValue("github").lastSuccessAt)
        assertEquals("SUCCESS", store.states.getValue("github").state)
    }

    @Test
    fun freshCacheSkipsAllNetworkRequests() = runBlocking {
        val store = FakeStore()
        listOf("github", "hf-models", "hf-spaces", "android-developers", "google-developers", "openai-news", "prompts-chat", "image-prompts", "video-prompts", "remote-prompts")
            .forEach { id -> store.states[id] = SyncStateEntity(id, "SUCCESS", now, now, null, lastAttemptAt = now, lastSuccessAt = now) }
        var requests = 0
        val transport = SourceHttpTransport { _, _ -> requests++; response(200) }

        PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = false)

        assertEquals(0, requests)
    }

    @Test
    fun networkErrorIsRetryableButDoesNotStopFollowingSources() = runBlocking {
        val store = FakeStore()
        val completed = mutableListOf<String>()
        val transport = SourceHttpTransport { url, _ ->
            if (url.contains("/api/models")) error("offline")
            completed += url
            successFor(url)
        }

        val result = PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        assertTrue(result.hasTransientFailure)
        assertTrue(completed.any { it.contains("/api/spaces") })
        assertTrue(completed.any { it.contains("datasets-server") })
        assertEquals("FAILURE", store.states.getValue("hf-models").state)
        assertNull(store.states.getValue("hf-models").lastSuccessAt)
    }

    @Test
    fun githubFailureKeepsItsCachedItemsWhileOtherSourcesSucceed() = runBlocking {
        val store = FakeStore()
        val cached = item("github-cached").copy(externalId = "github:cached")
        store.items += cached
        val transport = SourceHttpTransport { url, _ ->
            if (url.startsWith("https://api.github.com")) error("offline") else successFor(url)
        }

        PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        assertTrue(store.items.contains(cached))
        assertTrue(store.notifications.isEmpty())
        assertEquals("FAILURE", store.states.getValue("github").state)
        assertEquals("SUCCESS", store.states.getValue("hf-spaces").state)
    }

    @Test
    fun onlyNewItemsWithRecentRealSourceDatesAreReportedAfterSuccessfulSync() = runBlocking {
        val store = FakeStore()
        val body = """{"items":[
            {"id":11,"full_name":"octo/recent","html_url":"https://github.com/octo/recent","pushed_at":"${java.time.Instant.ofEpochMilli(now - 60_000).toString()}"},
            {"id":12,"full_name":"octo/old","html_url":"https://github.com/octo/old","pushed_at":"${java.time.Instant.ofEpochMilli(now - 30L * 24 * 60 * 60 * 1000).toString()}"}
        ]}"""
        val transport = SourceHttpTransport { url, _ ->
            if (url.startsWith("https://api.github.com")) response(200, body = body) else successFor(url)
        }

        val result = PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        assertEquals(listOf("github:11"), result.newlyDiscoveredItems.map { it.externalId })
        assertEquals(listOf("external:github:11"), store.notifications.map { it.notificationKey })
        assertEquals("android-projects", store.notifications.single().notificationType)
    }

    private fun successFor(url: String): SourceHttpResponse = when {
        url.startsWith("https://api.github.com") -> response(
            200,
            mapOf("ETag" to "\"github\"", "X-RateLimit-Remaining" to "54"),
            """{"incomplete_results":false,"items":[{"id":1,"full_name":"octo/repo","html_url":"https://github.com/octo/repo","owner":{"login":"octo"}},{"id":2,"full_name":"octo/repo-2","html_url":"https://github.com/octo/repo-2","owner":{"login":"octo"}},{"id":3,"full_name":"octo/repo-3","html_url":"https://github.com/octo/repo-3","owner":{"login":"octo"}}]}""",
        )
        url.contains("/api/models") || url.contains("/api/spaces") -> response(200, body = "[]")
        url.contains("datasets-server") -> response(200, body = """{"rows":[],"num_rows_total":0}""")
        url.contains("remote_prompts.json") -> response(200, body = """{"schema_version":1,"prompts":[]}""")
        else -> response(200, body = "<rss version=\"2.0\"><channel></channel></rss>")
    }

    private fun response(status: Int, headers: Map<String, String> = emptyMap(), body: String = "") =
        SourceHttpResponse(status, headers, body)

    private fun itemCategory(store: FakeStore, itemId: String): String? = store.categories[itemId]

    private fun item(id: String) = ItemEntity(
        id = id,
        title = id,
        originalTitle = null,
        description = null,
        originalDescription = null,
        url = null,
        contentType = "OTHER",
        freeStatus = "UNKNOWN",
        verificationLevel = "UNVERIFIED",
        lastVerifiedAt = null,
        createdAt = now,
        updatedAt = now,
        language = null,
        thumbnailUrl = null,
        externalId = null,
    )

    private class FakeStore : SyncStore {
        val states = mutableMapOf<String, SyncStateEntity>()
        val items = mutableListOf<ItemEntity>()
        val categories = mutableMapOf<String, String?>()
        val notifications = mutableListOf<com.mersadai.app.data.local.NotificationHistoryEntity>()

        override suspend fun getState(sourceId: String): SyncStateEntity? = states[sourceId]
        override suspend fun saveState(state: SyncStateEntity) { states[state.id] = state }
        override suspend fun saveContent(item: ItemEntity, source: SourceEntity, category: CategoryEntity?): Boolean {
            val inserted = items.none { it.id == item.id || (item.externalId != null && it.externalId == item.externalId) }
            items.removeAll { it.id == item.id }
            items += item
            categories[item.id] = category?.id
            return inserted
        }

        override suspend fun enqueueNotifications(entries: List<com.mersadai.app.data.local.NotificationHistoryEntity>) {
            notifications += entries
        }
    }

    private companion object {
        const val VIDEO_PROMPTS_FIXTURE = """{"num_rows_total":698,"rows":[{"row_idx":22,"row":{"id":23,"category":"pack:character-transformation","group":"Character transformation trend","prompt":"Vertical 9:16 cyberpunk avatar transformation"}}]}"""
    }
}