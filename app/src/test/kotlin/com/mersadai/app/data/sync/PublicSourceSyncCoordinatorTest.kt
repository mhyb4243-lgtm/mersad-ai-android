package com.mersadai.app.data.sync

import com.mersadai.app.data.local.CategoryEntity
import com.mersadai.app.data.local.ItemEntity
import com.mersadai.app.data.local.PromptEntity
import com.mersadai.app.data.local.SourceEntity
import com.mersadai.app.data.local.SyncStateEntity
import com.mersadai.app.data.local.RemoteContentSaveResult
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
        assertEquals(13, store.states.size)
        assertEquals("FAILURE", store.states.getValue("github").state)
        assertEquals(403, store.states.getValue("github").lastHttpStatus)
        assertEquals(now + 3_600_000, store.states.getValue("github").nextAllowedSyncAt)
        assertEquals("FAILURE", store.states.getValue("hf-models").state)
        assertEquals(now + 120_000, store.states.getValue("hf-models").nextAllowedSyncAt)
        assertEquals("SUCCESS", store.states.getValue("hf-spaces").state)
        assertEquals("SUCCESS", store.states.getValue("openrouter-models").state)
        assertEquals("SUCCESS", store.states.getValue("prompts-chat").state)
        assertEquals("SUCCESS", store.states.getValue("remote-prompts").state)
        assertTrue(calls.any { it.second["Accept"] == "application/vnd.github+json" })
        assertTrue(calls.any { it.second["X-GitHub-Api-Version"] == "2022-11-28" })
        assertTrue(calls.any {
            it.first.startsWith("https://civitai.com/api/v1/images") &&
                it.first.contains("sort=Newest") &&
                it.first.contains("withMeta=true")
        })
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

        val githubCalls = calls.filter { it.first.startsWith("https://api.github.com/search/repositories") }
        assertEquals(3, githubCalls.size)
        assertTrue(githubCalls.all { it.first.contains("sort=updated") && it.first.contains("per_page=20") })
        assertTrue(githubCalls.any { it.first.contains("topic%3Aphotography") })
        assertTrue(githubCalls.any { it.first.contains("topic%3Aphoto-editing") })
        assertTrue(githubCalls.any { it.first.contains("topic%3Ainvitation") })
        assertTrue(githubCalls.any { it.first.contains("topic%3Aposter-design") })
        assertTrue(githubCalls.all { it.second["User-Agent"]?.contains("MersadAI-Android") == true })
        assertTrue(calls.any { it.first.contains("/repos/JunkFood02/Seal/releases/latest") })
        assertTrue(calls.any { it.first.contains("/repos/T8RIN/ImageToolbox/releases/latest") })
        assertTrue(calls.any { it.first.contains("/repos/FossifyOrg/Gallery/releases/latest") })
        assertEquals(3, store.items.count { it.externalId?.startsWith("github:") == true })
        assertTrue(store.items.filter { it.externalId?.startsWith("github:") == true }.all { it.freeStatus == "UNKNOWN" })
        assertEquals(0L, store.states.getValue("prompts-chat").remoteTotalCount)
    }

    @Test
    fun civitaiPersistsOriginalPromptMetadataAndDeduplicatesByPostIdentity() = runBlocking {
        val store = FakeStore()
        val body = """{"items":[
            {"id":42,"postId":9001,"url":"https://image.civitai.com/42.jpg","createdAt":"2025-01-01T00:00:00Z","meta":{"prompt":"Original prompt","Model":"Checkpoint One"}},
            {"id":43,"postId":9001,"url":"https://image.civitai.com/43.jpg","meta":{"prompt":"Duplicate post prompt"}},
            {"id":44,"url":"https://image.civitai.com/44.jpg","meta":{}}
        ]}"""
        val transport = SourceHttpTransport { url, _ ->
            if (url.startsWith("https://civitai.com/api/v1/images")) {
                response(200, body = body)
            } else {
                successFor(url)
            }
        }

        PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        assertEquals(1, store.prompts.size)
        val prompt = store.prompts.single()
        assertEquals("civitai", prompt.sourceId)
        assertEquals("9001", prompt.sourcePostId)
        assertEquals(9001L, prompt.postId)
        assertEquals("Original prompt", prompt.prompt)
        assertEquals("https://civitai.com/posts/9001", prompt.postUrl)
        assertEquals("https://image.civitai.com/42.jpg", prompt.imageUrl)
        assertEquals("Checkpoint One", prompt.modelName)
        assertEquals(1_735_689_600_000L, prompt.publishedAt)
        assertEquals(now, prompt.discoveredAt)
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

        @Test
        fun openRouterFreeModelsArePersistedWithApiKeyRequirements() = runBlocking {
            val store = FakeStore()
            val body = """{"data":[{"id":"provider/model:free","name":"Free Model","description":"A documented model.","created":${(now - 60_000L) / 1000},"pricing":{"prompt":"0","completion":"0"}}]}"""
            val transport = SourceHttpTransport { url, _ ->
                if (url == "https://openrouter.ai/api/v1/models") response(200, body = body) else successFor(url)
            }

            PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

            val model = store.items.single { it.externalId == "openrouter:provider/model:free" }
            assertEquals(ContentType.MODEL.name, model.contentType)
            assertEquals(FreeStatus.FREE_TIER.name, model.freeStatus)
            assertEquals(true, model.requiresApiKey)
            assertEquals(true, model.requiresAccount)
            assertEquals("SUCCESS", store.states.getValue("openrouter-models").state)
            assertEquals("models", store.notifications.single { it.itemId == model.id }.notificationType)
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
        val photoPrompt = """{"id":"photo-manipulation-forced-perspective","title":"خدعة المنظور القسري العملاق","description":"وصف عربي.","category_id":"visual-tricks","category_name":"📸 صورة وفوتوغرافي","prompt_type":"image-generation","prompt":"A complete forced-perspective prompt.","url":"https://civitai.com/images/42","source_url":"https://civitai.com/images/42","thumbnail_url":"https://image.civitai.com/preview.jpg","tags":["📸 صورة وفوتوغرافي","خداع بصري / تلاعب"]}"""
        val space = """{"id":"huggingface-space-author-chat","title":"Chat Demo","description":"A public chat app","url":"https://huggingface.co/spaces/author/chat-demo","author":"author","free_status":"UNKNOWN","tags":["text-generation"],"likes":25,"sdk":"gradio","published_at":$now}"""
        val feed = """{"schema_version":3,"prompts":[$entry,$entry],"sections":[{"id":"PHOTO_MANIPULATION","title":"📸 صورة وفوتوغرافي — خداع بصري / تلاعب","items":[$photoPrompt]},{"id":"huggingface-live-spaces","title":"مساحات Hugging Face الحية للذكاء الاصطناعي","items":[$space]}]}"""
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
        val photoItem = store.items.single { it.externalId == "remote-prompts:photo-manipulation-forced-perspective" }
        assertEquals("visual-tricks", itemCategory(store, photoItem.id))
        assertEquals("خدعة المنظور القسري العملاق", photoItem.title)
        assertEquals("https://image.civitai.com/preview.jpg", photoItem.thumbnailUrl)
        assertEquals("https://civitai.com/images/42", photoItem.url)
        assertEquals("https://civitai.com/images/42", photoItem.sourceUrl)
        assertNull(photoItem.publishedAt)
        assertEquals(now, photoItem.fetchedAt)
        assertTrue(photoItem.tags.orEmpty().contains("خداع بصري / تلاعب"))
        val huggingFaceSpace = store.items.single { it.externalId == "remote-prompts:huggingface-space-author-chat" }
        assertEquals(ContentType.AI_TOOL.name, huggingFaceSpace.contentType)
        assertEquals(FreeStatus.UNKNOWN.name, huggingFaceSpace.freeStatus)
        assertEquals("https://huggingface.co/spaces/author/chat-demo", huggingFaceSpace.url)
        assertEquals("SUCCESS", store.states.getValue("remote-prompts").state)
    }

    @Test
    fun remoteFeedParsesPublicationDatesAndOfficialDeals() = runBlocking {
        val publishedAt = now - 60_000L
        val feed = """{"schema_version":3,"prompts":[{"id":"fresh-prompt","title":"Fresh prompt","category_id":"ai-prompts","prompt":"A useful prompt.","published_at":$publishedAt}],"sections":[{"id":"ai-deals-and-trials","title":"AI Deals & Trials","items":[{"id":"free-plan","title":"Official free plan","description":"A free plan.","url":"https://example.com/pricing","source_url":"https://example.com/pricing","provider":"Example","free_status":"FREE_TIER","deal_type":"free plan","promo_code":"TRYFREE","is_active":true,"published_at":$publishedAt,"verified_at":$now,"requires_account":true,"requires_payment_card":false,"free_limit":"Daily usage"}]}]}"""
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
        assertEquals(FreeStatus.UNKNOWN.name, deal.freeStatus)
        assertNull(deal.publishedAt)
        assertNull(deal.lastVerifiedAt)
        assertEquals("free-perks", itemCategory(store, deal.id))
        assertNull(deal.dealType)
        assertNull(deal.promoCode)
        assertNull(deal.isActive)
        assertNull(deal.freeLimit)
        assertEquals("OFFICIAL_SOURCE", deal.verificationLevel)
        assertEquals("https://example.com/pricing", deal.sourceUrl)
        assertTrue(deal.description.orEmpty().contains("لم يتم التحقق"))
    }

    @Test
    fun verifiedDealChangesAreNotifiedOnceAndDoNotUseSyntheticPublicationDates() = runBlocking {
        val store = FakeStore()
        var status = "FREE_TIER"
        var limit = "Five uses"
        val transport = SourceHttpTransport { url, _ ->
            if (url.contains("remote_prompts.json")) {
                response(
                    200,
                    body = """{"schema_version":3,"prompts":[],"sections":[{"id":"ai-deals-and-trials","title":"AI deals","items":[{"id":"changing-offer","title":"Changing offer","description":"Verified details.","url":"https://example.com/offer","source_url":"https://example.com/terms","source_type":"official","free_status":"$status","terms_verified_at":$now,"published_at":$now,"is_active":true,"free_limit":"$limit"}]}]}""",
                )
            } else {
                successFor(url)
            }
        }
        val coordinator = PublicSourceSyncCoordinator(store, transport) { now }

        coordinator.synchronize(force = true)
        val initial = store.items.single { it.externalId == "remote-prompts:changing-offer" }
        assertEquals(FreeStatus.FREE_TIER.name, initial.freeStatus)
        assertNull(initial.publishedAt)
        assertEquals("https://example.com/terms", initial.sourceUrl)
        assertTrue(store.notifications.isEmpty())

        status = "FREE_CREDIT"
        limit = "Three credits"
        coordinator.synchronize(force = true)
        assertEquals(1, store.notifications.size)
        assertTrue(store.notifications.single().notificationKey.startsWith("change:"))
        assertEquals("free-offers", store.notifications.single().notificationType)

        coordinator.synchronize(force = true)
        assertEquals(1, store.notifications.size)
    }

    @Test
    fun remoteFeedParsesFactVersePromptsAndScienceArticles() = runBlocking {
        val publishedAt = now - 60_000L
        val videoPrompt = """{"id":"factverse-physics-reel","title":"Quantum Tides","description":"A three-scene physics reel.","prompt_type":"video-generation","category_id":"factverse","category_name":"🌌 FactVerse (Science, Future & AI)","duration_seconds":30,"language":"en","prompt":"Scene 1 (0-10s): Show a quantum wave.\nVoiceover (English): \"Matter follows probability.\"\nOn-screen text: \"Quantum fields\"\nLower-third overlay: \"FactVerse • Explore The Future\"\nScene 2 (10-20s): Show a detector.\nVoiceover (English): \"Measurement reveals an outcome.\"\nOn-screen text: \"Observe the shift\"\nLower-third overlay: \"FactVerse • Explore The Future\"\nScene 3 (20-30s): Show a clear visual summary.\nVoiceover (English): \"The universe keeps surprising us.\"\nOn-screen text: \"Explore the future\"\nLower-third overlay: \"FactVerse • Explore The Future\""}"""
        val feed = """{"schema_version":3,"prompts":[$videoPrompt],"sections":[{"id":"ai-deals-and-trials","title":"AI Deals & Trials","items":[]},{"id":"factverse-science","title":"🌌 FactVerse (Science, Future & AI)","items":[{"id":"factverse-sciencedaily-123","title":"New discovery in quantum materials","description":"Researchers report a new result.","url":"https://www.sciencedaily.com/releases/2026/10/261009100000.htm","source_id":"factverse-sciencedaily","source_name":"ScienceDaily","published_at":$publishedAt}]}]}"""
        val transport = SourceHttpTransport { url, _ ->
            if (url.contains("remote_prompts.json")) response(200, body = feed) else successFor(url)
        }
        val store = FakeStore()

        PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        assertEquals("SUCCESS", store.states.getValue("remote-prompts").state)
        val prompt = store.items.firstOrNull { it.externalId == "remote-prompts:factverse-physics-reel" }
            ?: error("Missing FactVerse prompt; saved ids: ${store.items.map(ItemEntity::externalId)}")
        assertEquals("PROMPT", prompt.contentType)
        assertEquals("factverse", itemCategory(store, prompt.id))
        assertTrue(prompt.originalDescription.orEmpty().contains("Voiceover (English):"))
        assertFalse(prompt.originalDescription.orEmpty().contains("Audio/Voiceover (Arabic):"))
        assertTrue(prompt.originalDescription.orEmpty().contains("Lower-third overlay: \"FactVerse • Explore The Future\""))
        val article = store.items.firstOrNull { it.externalId == "remote-prompts:factverse-sciencedaily-123" }
            ?: error("Missing FactVerse article; saved ids: ${store.items.map(ItemEntity::externalId)}")
        assertEquals("NEWS", article.contentType)
        assertEquals("factverse", itemCategory(store, article.id))
        assertEquals("factverse-sciencedaily", store.sources[article.id])
        assertEquals("en", article.language)
        assertEquals(publishedAt, article.publishedAt)
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
        listOf(
            "github", "android-media-releases", "hf-models", "openrouter-models", "hf-spaces", "android-developers",
            "google-developers", "openai-news", "prompts-chat", "image-prompts", "civitai-images",
            "video-prompts", "remote-prompts",
        )
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

    @Test
    fun civitaiKeepsStableItemsAcrossSyncsAndQueuesAtMostOneRecentPromptNotification() = runBlocking {
        val store = FakeStore()
        val recentDate = java.time.Instant.ofEpochMilli(now - 60_000L)
        val body = """{"items":[
            {"id":42,"url":"https://image.civitai.com/42.jpg","createdAt":"$recentDate","meta":{"prompt":"A cinematic portrait with detailed lighting"}},
            {"id":43,"url":"https://image.civitai.com/43.jpg","createdAt":"$recentDate","meta":{"prompt":"A surreal landscape with a floating city"}}
        ]}"""
        val requests = mutableListOf<String>()
        val transport = SourceHttpTransport { url, _ ->
            requests += url
            if (url.startsWith("https://civitai.com/api/v1/images")) response(200, body = body)
            else successFor(url)
        }
        val coordinator = PublicSourceSyncCoordinator(store, transport) { now }

        val first = coordinator.synchronize(force = true)
        val second = coordinator.synchronize(force = true)

        assertTrue(requests.any { it.contains("sort=Newest") && it.contains("withMeta=true") })
        val civitaiItems = store.items.filter { it.externalId?.startsWith("civitai:image:") == true }
        assertEquals(setOf("civitai:image:42", "civitai:image:43"), civitaiItems.mapNotNull { it.externalId }.toSet())
        assertEquals(2, civitaiItems.size)
        assertEquals(2, first.newlyDiscoveredItems.count { it.source?.id == "civitai" })
        assertTrue(second.newlyDiscoveredItems.none { it.source?.id == "civitai" })
        assertEquals(1, store.notifications.count { it.notificationType == "prompts" })
        assertEquals("civitai:image:42", store.notifications.single().itemId)
    }

    @Test
    fun oldCivitaiPromptIsSavedWithoutBeingReportedAsNew() = runBlocking {
        val store = FakeStore()
        val oldDate = java.time.Instant.ofEpochMilli(now - 30L * 24 * 60 * 60 * 1000)
        val body = """{"items":[{"id":8,"url":"https://image.civitai.com/8.jpg","createdAt":"$oldDate","meta":{"prompt":"An old original prompt"}}]}"""
        val transport = SourceHttpTransport { url, _ ->
            if (url.startsWith("https://civitai.com/api/v1/images")) response(200, body = body)
            else successFor(url)
        }

        val result = PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        val oldPrompt = store.items.single { it.externalId == "civitai:image:8" }
        assertEquals(now, oldPrompt.fetchedAt)
        assertEquals(now, oldPrompt.discoveredAt)
        assertTrue(result.newlyDiscoveredItems.none { it.externalId == oldPrompt.externalId })
        assertTrue(store.notifications.none { it.itemId == oldPrompt.id })
    }

    @Test
    fun civitaiNetworkFailureKeepsCachedPromptsAndDoesNotBlockOtherSources() = runBlocking {
        val store = FakeStore()
        val cached = item("civitai:image:7").copy(
            externalId = "civitai:image:7",
            contentType = ContentType.PROMPT.name,
            originalDescription = "Previously saved original prompt",
        )
        store.items += cached
        val completed = mutableListOf<String>()
        val transport = SourceHttpTransport { url, _ ->
            if (url.startsWith("https://civitai.com/api/v1/images")) error("offline")
            completed += url
            successFor(url)
        }

        val result = PublicSourceSyncCoordinator(store, transport) { now }.synchronize(force = true)

        assertTrue(result.hasTransientFailure)
        assertTrue(store.items.contains(cached))
        assertEquals("FAILURE", store.states.getValue("civitai-images").state)
        assertEquals("SUCCESS", store.states.getValue("hf-spaces").state)
        assertTrue(completed.any { it.contains("/api/spaces") })
    }

    private fun successFor(url: String): SourceHttpResponse = when {
        url.contains("/releases/latest") -> response(
            200,
            body = """{"tag_name":"v1.0","name":"v1.0","html_url":"https://github.com/org/app/releases/tag/v1.0","published_at":"2025-01-01T00:00:00Z","assets":[{"name":"app.apk","browser_download_url":"https://github.com/org/app/releases/download/v1.0/app.apk"}]}""",
        )
        url.startsWith("https://api.github.com/search/repositories") -> response(
            200,
            mapOf("ETag" to "\"github\"", "X-RateLimit-Remaining" to "54"),
            """{"incomplete_results":false,"items":[{"id":1,"full_name":"octo/repo","html_url":"https://github.com/octo/repo","owner":{"login":"octo"}},{"id":2,"full_name":"octo/repo-2","html_url":"https://github.com/octo/repo-2","owner":{"login":"octo"}},{"id":3,"full_name":"octo/repo-3","html_url":"https://github.com/octo/repo-3","owner":{"login":"octo"}}]}""",
        )
        url == "https://openrouter.ai/api/v1/models" -> response(200, body = """{"data":[]}""")
        url.contains("/api/models") || url.contains("/api/spaces") -> response(200, body = "[]")
        url.startsWith("https://civitai.com/api/v1/images") -> response(200, body = """{"items":[]}""")
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
        val prompts = mutableListOf<PromptEntity>()
        val categories = mutableMapOf<String, String?>()
        val sources = mutableMapOf<String, String>()
        val notifications = mutableListOf<com.mersadai.app.data.local.NotificationHistoryEntity>()

        override suspend fun getState(sourceId: String): SyncStateEntity? = states[sourceId]
        override suspend fun saveState(state: SyncStateEntity) { states[state.id] = state }
        override suspend fun saveContent(
            item: ItemEntity,
            source: SourceEntity,
            category: CategoryEntity?,
        ): RemoteContentSaveResult {
            val previous = items.firstOrNull { it.id == item.id || (item.externalId != null && it.externalId == item.externalId) }
            val inserted = previous == null
            val changed = previous != null && listOf(
                previous.freeStatus != item.freeStatus,
                previous.verificationLevel != item.verificationLevel,
                previous.freeLimit != item.freeLimit,
                previous.isActive != item.isActive,
                previous.endAt != item.endAt,
                previous.requiresAccount != item.requiresAccount,
                previous.requiresPaymentCard != item.requiresPaymentCard,
                previous.requiresApiKey != item.requiresApiKey,
                previous.sourceUrl != item.sourceUrl,
                previous.url != item.url,
            ).any { it }
            items.removeAll { it.id == item.id }
            items += item.copy(
                discoveredAt = previous?.discoveredAt ?: item.discoveredAt ?: item.fetchedAt.takeIf { previous == null },
            )
            categories[item.id] = category?.id
            sources[item.id] = source.id
            return RemoteContentSaveResult(inserted, changed)
        }

        override suspend fun savePrompts(prompts: List<PromptEntity>) {
            prompts.forEach { prompt ->
                if (this.prompts.none {
                        it.sourceId == prompt.sourceId && it.sourcePostId == prompt.sourcePostId
                    }
                ) {
                    this.prompts += prompt
                }
            }
        }

        override suspend fun enqueueNotifications(entries: List<com.mersadai.app.data.local.NotificationHistoryEntity>) {
            notifications += entries
        }
    }

    private companion object {
        const val VIDEO_PROMPTS_FIXTURE = """{"num_rows_total":698,"rows":[{"row_idx":22,"row":{"id":23,"category":"pack:character-transformation","group":"Character transformation trend","prompt":"Vertical 9:16 cyberpunk avatar transformation"}}]}"""
    }
}