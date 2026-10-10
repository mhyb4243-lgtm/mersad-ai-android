package com.mersadai.app.data.sync

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mersadai.app.data.dedup.DeduplicationService
import com.mersadai.app.data.local.CategoryEntity
import com.mersadai.app.data.local.NotificationHistoryEntity
import com.mersadai.app.data.local.SourceEntity
import com.mersadai.app.data.local.SyncStateEntity
import com.mersadai.app.data.mapper.toEntity
import com.mersadai.app.data.remote.SourceHttpResponse
import com.mersadai.app.data.remote.SourceHttpTransport
import com.mersadai.app.data.remote.SourceParsers
import com.mersadai.app.domain.model.Category
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import com.mersadai.app.domain.model.Source
import com.mersadai.app.domain.model.SyncState
import com.mersadai.app.domain.model.VerificationLevel
import com.mersadai.app.domain.prompts.VideoPromptPolicy
import com.mersadai.app.domain.prompts.FactVerseContent
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PublicSourceSyncCoordinator(
    private val store: SyncStore,
    private val transport: SourceHttpTransport,
    private val now: () -> Long = System::currentTimeMillis,
) : SyncCoordinator {
    private val deduplication = DeduplicationService()
    private val gson = Gson()

    override suspend fun synchronize(force: Boolean): SyncRunResult {
        var transientFailure = false
        val newlyDiscovered = mutableListOf<ContentItem>()
        sources.forEach { source ->
            val previous = store.getState(source.id)
            val timestamp = now()
            if (!force && previous?.lastSuccessAt?.let { timestamp - it < source.cacheTtlMillis } == true) return@forEach
            if (previous?.nextAllowedSyncAt?.let { it > timestamp } == true) return@forEach

            store.saveState(
                (previous ?: SyncStateEntity(source.id, SyncState.IDLE.name, null, null, null)).copy(
                    state = SyncState.SYNCING.name,
                    lastStartedAt = timestamp,
                    lastAttemptAt = timestamp,
                    lastError = null,
                    message = null,
                    isSyncing = true,
                ),
            )

            var outcome = try {
                fetch(source, previous)
            } catch (error: Exception) {
                FetchOutcome(
                    error = "تعذر الاتصال بمصدر ${source.name}.",
                    transient = true,
                    etag = previous?.etag,
                    lastModified = previous?.lastModifiedHeader,
                )
            }
            val sourceDiscoveries = mutableListOf<ContentItem>()
            val sourceNotifications = mutableListOf<NotificationHistoryEntity>()
            if (outcome.items.isNotEmpty()) {
                for (item in deduplication.unique(outcome.items)) {
                    val itemSource = item.source ?: continue
                    try {
                        val discoveredAt = now()
                        val persistedItem = item.copy(
                            freeStatus = if (item.license == null && item.freeStatus == com.mersadai.app.domain.model.FreeStatus.OPEN_SOURCE) {
                                com.mersadai.app.domain.model.FreeStatus.UNKNOWN
                            } else {
                                item.freeStatus
                            },
                        )
                        val inserted = store.saveContent(
                            item = persistedItem.toEntity(),
                            source = itemSource.toEntity(),
                            category = item.category?.let { CategoryEntity(it.id, it.name, it.parentId) },
                        )
                        val notificationType = item.notificationType()
                        if (inserted && item.hasRecentSourceDate(discoveredAt) && notificationType != null) {
                            sourceDiscoveries += item
                            sourceNotifications += NotificationHistoryEntity(
                                notificationKey = item.externalId?.let { "external:$it" }
                                    ?: "source:${item.source.id}:${item.id}",
                                itemId = item.id,
                                notificationType = notificationType,
                                discoveredAt = discoveredAt,
                            )
                        }
                    } catch (_: Exception) {
                        outcome = outcome.copy(error = "تعذر حفظ بيانات ${source.name}؛ احتُفظ بالعناصر السابقة.")
                        break
                    }
                }
            }
            if (outcome.error == null) {
                newlyDiscovered += sourceDiscoveries
                store.enqueueNotifications(sourceNotifications)
            }
            if (outcome.transient) transientFailure = true
            val finishedAt = now()
            val succeeded = outcome.error == null
            store.saveState(
                (previous ?: SyncStateEntity(source.id, SyncState.IDLE.name, null, null, null)).copy(
                    state = if (succeeded) SyncState.SUCCESS.name else SyncState.FAILURE.name,
                    lastStartedAt = timestamp,
                    lastFinishedAt = finishedAt,
                    message = outcome.message ?: outcome.error,
                    lastAttemptAt = timestamp,
                    lastSuccessAt = if (succeeded) finishedAt else previous?.lastSuccessAt,
                    lastHttpStatus = outcome.statusCode,
                    lastError = outcome.error,
                    etag = outcome.etag ?: previous?.etag,
                    lastModifiedHeader = outcome.lastModified ?: previous?.lastModifiedHeader,
                    rateLimitRemaining = outcome.rateLimitRemaining,
                    rateLimitResetAt = outcome.rateLimitResetAt,
                    nextAllowedSyncAt = outcome.nextAllowedSyncAt,
                    isSyncing = false,
                    remoteTotalCount = outcome.remoteTotalCount ?: previous?.remoteTotalCount,
                ),
            )
        }
        return SyncRunResult(transientFailure, newlyDiscovered.distinctBy(deduplication::keyFor))
    }

    private fun ContentItem.hasRecentSourceDate(timestamp: Long): Boolean =
        (publishedAt ?: pushedAt ?: sourceUpdatedAt)?.let { it in (timestamp - NOTIFICATION_FRESHNESS_WINDOW)..timestamp } == true

    private fun ContentItem.notificationType(): String? = when (contentType) {
        com.mersadai.app.domain.model.ContentType.AI_TOOL -> "ai-tools"
        com.mersadai.app.domain.model.ContentType.ANDROID_PROJECT -> "android-projects"
        com.mersadai.app.domain.model.ContentType.MODEL -> "models"
        com.mersadai.app.domain.model.ContentType.PROMPT -> "prompts"
        com.mersadai.app.domain.model.ContentType.NEWS -> "news"
        com.mersadai.app.domain.model.ContentType.OTHER -> null
    }

    private suspend fun fetch(source: SourceDefinition, previous: SyncStateEntity?): FetchOutcome = when (source.id) {
        "github" -> fetchGitHub(source, previous)
        "android-media-releases" -> fetchAndroidMediaReleases(source, previous)
        "hf-models" -> fetchSingle(source, previous, source.endpoint) { SourceParsers.huggingFaceModels(it) }
        "hf-spaces" -> fetchSingle(source, previous, source.endpoint) { SourceParsers.huggingFaceSpaces(it) }
        "android-developers", "google-developers", "openai-news" ->
            fetchSingle(source, previous, source.endpoint) { body ->
                SourceParsers.rssFeed(body, source.id, source.name, source.endpoint)
            }
        "prompts-chat" -> fetchSingle(
            source,
            previous,
            source.endpoint,
            totalCount = SourceParsers::promptsChatTotalCount,
        ) { SourceParsers.promptsChat(it) }
        "image-prompts" -> fetchSingle(source, previous, source.endpoint) { SourceParsers.imageGenerationPrompts(it) }
        "lexica" -> fetchSingle(source, previous, source.endpoint) { SourceParsers.lexicaImagePrompts(it) }
        "video-prompts" -> fetchLatestVideoPrompts(source, previous)
        "remote-prompts" -> fetchSingle(source, previous, source.endpoint, parser = ::remotePromptFeed)
        else -> FetchOutcome(error = "مصدر غير معروف.")
    }

    private fun remotePromptFeed(body: String): List<ContentItem> {
        val root = JsonParser.parseString(body)
        if (root.isJsonArray) return root.asJsonArray.mapNotNull(::remotePromptItem)
        val feed = root.asJsonObject
        val prompts = feed.getAsJsonArray("prompts")
            ?: throw IllegalArgumentException("Remote prompt feed has no prompts array")
        val promptItems = prompts.mapNotNull(::remotePromptItem)
        val dealItems = feed.getAsJsonArray("sections")?.flatMap { sectionElement ->
            val section = sectionElement.takeIf { it.isJsonObject }?.asJsonObject ?: return@flatMap emptyList()
            if (section.get("id")?.takeIf { it.isJsonPrimitive }?.asString != "ai-deals-and-trials") return@flatMap emptyList()
            section.getAsJsonArray("items")?.mapNotNull(::remoteDealItem).orEmpty()
        }.orEmpty()
        val factVerseItems = feed.getAsJsonArray("sections")?.flatMap { sectionElement ->
            val section = sectionElement.takeIf { it.isJsonObject }?.asJsonObject ?: return@flatMap emptyList()
            if (section.get("id")?.takeIf { it.isJsonPrimitive }?.asString != "factverse-science") return@flatMap emptyList()
            section.getAsJsonArray("items")?.mapNotNull(::remoteFactVerseNewsItem).orEmpty()
        }.orEmpty()
        return promptItems + dealItems + factVerseItems
    }

    private fun remotePromptItem(element: com.google.gson.JsonElement): ContentItem? {
        val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val externalId = entry.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val title = entry.get("title")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val rawPrompt = entry.get("prompt")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val categoryId = entry.get("category_id")?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf(String::isNotBlank) ?: "reels-prompts"
        val categoryName = entry.get("category_name")?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf(String::isNotBlank) ?: "🎬 برومبتات ريلز وفيديو سينمائي"
        val promptType = entry.get("prompt_type")?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf(String::isNotBlank) ?: "text-to-video"
        val isVideo = promptType.contains("video", ignoreCase = true) || promptType.contains("reel", ignoreCase = true)
        val durationSeconds = runCatching { entry.get("duration_seconds")?.asInt }.getOrNull()
        val prompt = if (isVideo && categoryId != "factverse") {
            VideoPromptPolicy.apply(rawPrompt, durationSeconds == 30)
        } else {
            rawPrompt
        }
        val tags = entry.getAsJsonArray("tags")?.mapNotNull { tag ->
            tag.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)
        }.orEmpty()
        val timestamp = entry.long("published_at") ?: now()
        return ContentItem(
            id = "remote-prompts:$externalId",
            externalId = "remote-prompts:$externalId",
            title = title,
            originalTitle = entry.get("original_title")?.takeIf { it.isJsonPrimitive }?.asString,
            description = entry.get("description")?.takeIf { it.isJsonPrimitive }?.asString,
            originalDescription = prompt,
            url = entry.get("url")?.takeIf { it.isJsonPrimitive }?.asString ?: REMOTE_PROMPTS_FEED_URL,
            contentType = ContentType.PROMPT,
            category = Category(categoryId, categoryName),
            freeStatus = FreeStatus.UNKNOWN,
            verificationLevel = VerificationLevel.COMMUNITY_SOURCE,
            source = Source(
                "remote-prompts",
                "موجز البرومبتات المتجدد",
                "mhyb4243-lgtm/mersad-ai-android/remote_prompts.json",
                "https://github.com/mhyb4243-lgtm/mersad-ai-android",
                REMOTE_PROMPTS_FEED_URL,
            ),
            createdAt = timestamp,
            updatedAt = timestamp,
            language = entry.get("language")?.takeIf { it.isJsonPrimitive }?.asString ?: "en",
            tags = tags,
            promptType = promptType,
            publishedAt = entry.long("published_at"),
        )
    }

    private fun remoteFactVerseNewsItem(element: com.google.gson.JsonElement): ContentItem? {
        val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val externalId = entry.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val title = entry.get("title")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val url = entry.get("url")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf { it.startsWith("https://") } ?: return null
        val sourceId = entry.get("source_id")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf { it in FACTVERSE_SOURCE_IDS } ?: return null
        val sourceName = entry.get("source_name")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val timestamp = entry.long("published_at") ?: now()
        val description = entry.get("description")?.takeIf { it.isJsonPrimitive }?.asString
        val factVerseAssets = FactVerseContent.create(
            title = title,
            summary = description.orEmpty(),
            visualPrompt = entry.get("visual_prompt")?.takeIf { it.isJsonPrimitive }?.asString,
            reelsScript = entry.get("reels_script")?.takeIf { it.isJsonPrimitive }?.asString,
        )
        return ContentItem(
            id = "remote-prompts:$externalId",
            externalId = "remote-prompts:$externalId",
            title = title,
            originalTitle = title,
            description = description,
            originalDescription = factVerseAssets.serialize(),
            url = url,
            contentType = ContentType.NEWS,
            category = Category(FACTVERSE_CATEGORY_ID, FACTVERSE_CATEGORY_NAME),
            freeStatus = FreeStatus.UNKNOWN,
            verificationLevel = if (sourceId == "factverse-futurology") {
                VerificationLevel.COMMUNITY_SOURCE
            } else {
                VerificationLevel.OFFICIAL
            },
            source = Source(sourceId, sourceName, sourceId, url, url),
            createdAt = timestamp,
            updatedAt = timestamp,
            sourceUpdatedAt = timestamp,
            publishedAt = timestamp,
            language = "en",
            tags = listOf("FactVerse", "science", "future", "AI"),
        )
    }

    private fun remoteDealItem(element: com.google.gson.JsonElement): ContentItem? {
        val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val externalId = entry.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val title = entry.get("title")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val url = entry.get("url")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf { it.startsWith("https://") } ?: return null
        val timestamp = entry.long("published_at") ?: now()
        val description = entry.get("description")?.takeIf { it.isJsonPrimitive }?.asString
        return ContentItem(
            id = "remote-prompts:$externalId",
            externalId = "remote-prompts:$externalId",
            title = title,
            originalTitle = title,
            description = description,
            originalDescription = description,
            url = url,
            contentType = ContentType.AI_TOOL,
            category = Category("free-perks", "عروض واشتراكات الذكاء الاصطناعي المجانية (AI Deals & Trials)"),
            freeStatus = entry.get("free_status")?.takeIf { it.isJsonPrimitive }?.asString
                ?.let { runCatching { FreeStatus.valueOf(it) }.getOrNull() } ?: FreeStatus.FREE_TIER,
            verificationLevel = if (
                entry.get("source_type")?.takeIf { it.isJsonPrimitive }?.asString == "community"
            ) {
                VerificationLevel.COMMUNITY_SOURCE
            } else {
                VerificationLevel.OFFICIAL_SOURCE
            },
            source = Source("official-free-perks", "مصادر العروض الرسمية", "remote-prompts", url, url),
            lastVerifiedAt = entry.long("verified_at"),
            createdAt = timestamp,
            updatedAt = entry.long("verified_at") ?: timestamp,
            publishedAt = entry.long("published_at"),
            author = entry.get("provider")?.takeIf { it.isJsonPrimitive }?.asString,
            requiresAccount = entry.boolean("requires_account"),
            requiresPaymentCard = entry.boolean("requires_payment_card"),
            freeLimit = entry.get("free_limit")?.takeIf { it.isJsonPrimitive }?.asString,
            dealType = entry.get("deal_type")?.takeIf { it.isJsonPrimitive }?.asString,
            promoCode = entry.get("promo_code")?.takeIf { it.isJsonPrimitive }?.asString,
            isActive = entry.boolean("is_active"),
            startAt = entry.long("start_at"),
            endAt = entry.long("end_at"),
            tags = entry.getAsJsonArray("tags")?.mapNotNull { tag ->
                tag.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)
            }.orEmpty(),
        )
    }

    private fun com.google.gson.JsonObject.long(name: String): Long? =
        runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull()

    private fun com.google.gson.JsonObject.boolean(name: String): Boolean? =
        runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull()

    private suspend fun fetchLatestVideoPrompts(
        source: SourceDefinition,
        previous: SyncStateEntity?,
    ): FetchOutcome {
        val countResponse = transport.get("${source.endpoint}&offset=0&length=1", emptyMap())
        val countMetadata = responseMetadata(countResponse)
        if (countResponse.statusCode == 403 || countResponse.statusCode == 429) {
            return rateLimited(source, countResponse, previous, countMetadata)
        }
        if (countResponse.statusCode !in 200..299) {
            return countMetadata.copy(
                error = "تعذر تحديث ${source.name} (HTTP ${countResponse.statusCode}).",
                transient = countResponse.statusCode >= 500,
                nextAllowedSyncAt = if (countResponse.statusCode >= 500) null else now() + FAILURE_COOLDOWN,
            )
        }
        val totalCount = SourceParsers.videoGenerationPromptsTotalCount(countResponse.body)
            ?: return countMetadata.copy(error = "تغير تنسيق بيانات ${source.name}؛ احتُفظ بالنسخة المخزنة.")
        val offset = (totalCount - VIDEO_PROMPT_PAGE_SIZE).coerceAtLeast(0)
        return fetchSingle(
            source,
            previous,
            "${source.endpoint}&offset=$offset&length=$VIDEO_PROMPT_PAGE_SIZE",
            totalCount = { body -> SourceParsers.videoGenerationPromptsTotalCount(body) ?: totalCount },
            parser = SourceParsers::videoGenerationPrompts,
        )
    }

    private suspend fun fetchSingle(
        source: SourceDefinition,
        previous: SyncStateEntity?,
        url: String,
        totalCount: ((String) -> Long?)? = null,
        parser: (String) -> List<ContentItem>,
    ): FetchOutcome {
        val headers = conditionalHeaders(previous).toMutableMap().apply {
            if (source.id == "lexica") {
                put("Accept", "application/json")
                put("Referer", "https://lexica.art/")
                put("User-Agent", "Mozilla/5.0 (compatible; MersadAI/1.0; +https://github.com/mhyb4243-lgtm/mersad-ai-android)")
            }
        }
        val response = transport.get(url, headers)
        val headerData = responseMetadata(response)
        if (response.statusCode == 304) return headerData.copy(notModified = true)
        if (response.statusCode == 403 || response.statusCode == 429) {
            return rateLimited(source, response, previous, headerData)
        }
        if (response.statusCode !in 200..299) {
            return headerData.copy(
                error = "تعذر تحديث ${source.name} (HTTP ${response.statusCode}).",
                transient = response.statusCode >= 500,
                nextAllowedSyncAt = if (response.statusCode >= 500) null else now() + FAILURE_COOLDOWN,
            )
        }
        return try {
            headerData.copy(items = parser(response.body), remoteTotalCount = totalCount?.invoke(response.body))
        } catch (_: Exception) {
            headerData.copy(error = "تغير تنسيق بيانات ${source.name}؛ احتُفظ بالنسخة المخزنة.")
        }
    }

    private suspend fun fetchAndroidMediaReleases(
        source: SourceDefinition,
        previous: SyncStateEntity?,
    ): FetchOutcome {
        val releases = mutableListOf<ContentItem>()
        var latest = FetchOutcome()
        var failures = 0
        val headers = conditionalHeaders(previous) + mapOf(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to "MersadAI-Android/1.0 (public-source-sync)",
        )
        for (repository in androidMediaRepositories) {
            val response = transport.get("https://api.github.com/repos/$repository/releases/latest", headers)
            val metadata = responseMetadata(response)
            latest = metadata
            if (response.statusCode == 403 || response.statusCode == 429) {
                return rateLimited(source, response, previous, metadata).copy(items = releases)
            }
            if (response.statusCode !in 200..299) {
                failures++
                continue
            }
            try {
                releases += SourceParsers.githubLatestRelease(response.body, repository, now())
            } catch (_: Exception) {
                failures++
            }
        }
        return when {
            releases.isNotEmpty() -> latest.copy(items = releases)
            failures > 0 -> latest.copy(
                error = "تعذر تحديث إصدارات أدوات Android من GitHub؛ احتُفظ بالعناصر السابقة.",
                transient = latest.statusCode == null || latest.statusCode >= 500,
                nextAllowedSyncAt = now() + FAILURE_COOLDOWN,
            )
            else -> latest
        }
    }

    private suspend fun fetchGitHub(source: SourceDefinition, previous: SyncStateEntity?): FetchOutcome {
        val etags = previous?.etag.parseEtagMap()
        val newEtags = etags.toMutableMap()
        val fetched = mutableListOf<ContentItem>()
        var latest = FetchOutcome()
        var incomplete = false
        for ((index, query) in githubQueries.withIndex()) {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name()).replace("+", "%20")
            val url = "https://api.github.com/search/repositories?q=$encodedQuery&sort=updated&order=desc&per_page=20"
            val headers = buildMap {
                put("Accept", "application/vnd.github+json")
                put("X-GitHub-Api-Version", "2022-11-28")
                put("User-Agent", "MersadAI-Android/1.0 (public-source-sync)")
                newEtags[index.toString()]?.let { put("If-None-Match", it) }
                previous?.lastModifiedHeader?.let { put("If-Modified-Since", it) }
            }
            val response = transport.get(url, headers)
            val metadata = responseMetadata(response)
            latest = FetchOutcome(
                statusCode = response.statusCode,
                etag = metadata.etag,
                lastModified = metadata.lastModified,
                rateLimitRemaining = metadata.rateLimitRemaining,
                rateLimitResetAt = metadata.rateLimitResetAt,
            )
            response.header("ETag")?.let { newEtags[index.toString()] = it }
            if (response.statusCode == 304) continue
            if (response.statusCode == 403 || response.statusCode == 429) {
                return rateLimited(source, response, previous, latest).copy(
                    items = fetched,
                    etag = gson.toJson(newEtags),
                )
            }
            if (response.statusCode !in 200..299) {
                return latest.copy(
                    items = fetched,
                    error = "تعذر تحديث ${source.name} (HTTP ${response.statusCode}).",
                    transient = response.statusCode >= 500,
                    etag = gson.toJson(newEtags),
                    nextAllowedSyncAt = if (response.statusCode >= 500) null else now() + FAILURE_COOLDOWN,
                )
            }
            try {
                fetched += SourceParsers.githubRepositories(response.body)
                incomplete = incomplete || SourceParsers.githubIncompleteResults(response.body)
            } catch (_: Exception) {
                return latest.copy(
                    items = fetched,
                    error = "تغير تنسيق بيانات ${source.name}؛ احتُفظ بالنسخة المخزنة.",
                    etag = gson.toJson(newEtags),
                )
            }
        }
        return latest.copy(
            items = fetched,
            etag = gson.toJson(newEtags),
            message = if (incomplete) "أعاد GitHub نتائج غير مكتملة." else null,
        )
    }

    private fun rateLimited(
        source: SourceDefinition,
        response: SourceHttpResponse,
        previous: SyncStateEntity?,
        metadata: FetchOutcome,
    ): FetchOutcome {
        val reset = metadata.rateLimitResetAt
        val retryAfterHeader = response.header("Retry-After")
        val retryAfter = retryAfterHeader?.toLongOrNull()?.let { now() + it * 1000 }
            ?: retryAfterHeader?.httpDateMillis()
        val isRateLimited = response.statusCode == 429 || metadata.rateLimitRemaining == 0L || retryAfter != null
        val allowedAt = retryAfter ?: reset ?: (now() + RATE_LIMIT_COOLDOWN)
        return metadata.copy(
            error = if (isRateLimited) "وصل ${source.name} إلى حد الطلبات؛ ستبقى البيانات المخزنة متاحة." else "رفض ${source.name} الطلب (HTTP 403).",
            nextAllowedSyncAt = allowedAt,
            etag = metadata.etag ?: previous?.etag,            transient = false,        )
    }

    private fun conditionalHeaders(previous: SyncStateEntity?): Map<String, String> = buildMap {
        previous?.etag?.takeUnless { it.startsWith("{") }?.let { put("If-None-Match", it) }
        previous?.lastModifiedHeader?.let { put("If-Modified-Since", it) }
    }

    private fun responseMetadata(response: SourceHttpResponse) = FetchOutcome(
        statusCode = response.statusCode,
        etag = response.header("ETag"),
        lastModified = response.header("Last-Modified"),
        rateLimitRemaining = response.header("X-RateLimit-Remaining")?.toLongOrNull(),
        rateLimitResetAt = response.header("X-RateLimit-Reset")?.toLongOrNull()?.times(1000),
    )

    private fun String?.parseEtagMap(): Map<String, String> = this?.let { value ->
        runCatching {
            JsonParser.parseString(value).asJsonObject.entrySet().associate { it.key to it.value.asString }
        }.getOrDefault(emptyMap())
    }.orEmpty()

    private fun String.httpDateMillis(): Long? = runCatching {
        ZonedDateTime.parse(this, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull()

    private data class SourceDefinition(
        val id: String,
        val name: String,
        val endpoint: String,
        val cacheTtlMillis: Long,
    )

    private data class FetchOutcome(
        val items: List<ContentItem> = emptyList(),
        val statusCode: Int? = null,
        val error: String? = null,
        val transient: Boolean = false,
        val message: String? = null,
        val etag: String? = null,
        val lastModified: String? = null,
        val rateLimitRemaining: Long? = null,
        val rateLimitResetAt: Long? = null,
        val nextAllowedSyncAt: Long? = null,
        val notModified: Boolean = false,
        val remoteTotalCount: Long? = null,
    )

    private companion object {
        const val HOUR = 60 * 60 * 1000L
        const val DAY = 24 * HOUR
        const val RATE_LIMIT_COOLDOWN = HOUR
        const val FAILURE_COOLDOWN = 15 * 60 * 1000L
        const val NOTIFICATION_FRESHNESS_WINDOW = 7 * DAY
        const val VIDEO_PROMPT_PAGE_SIZE = 100L
        const val FACTVERSE_CATEGORY_ID = "factverse"
        const val FACTVERSE_CATEGORY_NAME = "🌌 FactVerse (Science, Future & AI)"
        val FACTVERSE_SOURCE_IDS = setOf(
            "factverse-futurology",
            "factverse-sciencedaily",
            "factverse-singularity-hub",
        )
        const val REMOTE_PROMPTS_FEED_URL = "https://raw.githubusercontent.com/mhyb4243-lgtm/mersad-ai-android/main/remote_prompts.json"
        val githubQueries = listOf(
            "topic:android AND (topic:photo-editor OR topic:video-editor OR topic:generative-ai OR topic:on-device-ai OR topic:design-tool OR topic:photography OR topic:photo-editing OR topic:invitation OR topic:poster-design)",
            "android language:Kotlin",
            "android \"Jetpack Compose\"",
        )
        val sources = listOf(
            SourceDefinition("github", "GitHub", "https://api.github.com/search/repositories", 6 * HOUR),
            SourceDefinition("android-media-releases", "إصدارات تطبيقات الميديا والتصميم", "https://api.github.com", 6 * HOUR),
            SourceDefinition("hf-models", "Hugging Face Models", "https://huggingface.co/api/models?sort=trendingScore&direction=-1&limit=20&full=true", 3 * HOUR),
            SourceDefinition("hf-spaces", "Hugging Face Spaces", "https://huggingface.co/api/spaces?sort=trendingScore&direction=-1&limit=20&full=true", 3 * HOUR),
            SourceDefinition("android-developers", "Android Developers", "https://android-developers.googleblog.com/feeds/posts/default?alt=rss", 6 * HOUR),
            SourceDefinition("google-developers", "Google Developers", "https://developers.googleblog.com/feeds/posts/default?alt=rss", 6 * HOUR),
            SourceDefinition("openai-news", "OpenAI News", "https://openai.com/news/rss.xml", 6 * HOUR),
            SourceDefinition("prompts-chat", "prompts.chat", "https://datasets-server.huggingface.co/rows?dataset=fka%2Fprompts.chat&config=default&split=train&offset=0&length=100", DAY),
            SourceDefinition("image-prompts", "Stable Diffusion Prompts", "https://datasets-server.huggingface.co/rows?dataset=Gustavosta%2FStable-Diffusion-Prompts&config=default&split=train&offset=0&length=100", DAY),
            SourceDefinition("lexica", "Lexica public image prompts", "https://lexica.art/api/v1/search?q=cinematic", 6 * HOUR),
            SourceDefinition("video-prompts", "AI Video Prompt Book 2026", "https://datasets-server.huggingface.co/rows?dataset=hrrcne%2Fai-video-prompt-book-2026&config=default&split=train", 6 * HOUR),
            SourceDefinition("remote-prompts", "موجز البرومبتات المتجدد", REMOTE_PROMPTS_FEED_URL, 12 * HOUR),
        )
        val androidMediaRepositories = listOf("JunkFood02/Seal", "T8RIN/ImageToolbox", "FossifyOrg/Gallery")
    }
}