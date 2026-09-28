package com.mersadai.app.data.sync

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mersadai.app.data.dedup.DeduplicationService
import com.mersadai.app.data.local.CategoryEntity
import com.mersadai.app.data.local.SourceEntity
import com.mersadai.app.data.local.SyncStateEntity
import com.mersadai.app.data.mapper.toEntity
import com.mersadai.app.data.remote.SourceHttpResponse
import com.mersadai.app.data.remote.SourceHttpTransport
import com.mersadai.app.data.remote.SourceParsers
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.SyncState
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
            if (outcome.items.isNotEmpty()) {
                for (item in deduplication.unique(outcome.items)) {
                    val itemSource = item.source ?: continue
                    try {
                        store.saveContent(
                            item = item.toEntity(),
                            source = itemSource.toEntity(),
                            category = item.category?.let { CategoryEntity(it.id, it.name, it.parentId) },
                        )
                    } catch (_: Exception) {
                        outcome = outcome.copy(error = "تعذر حفظ بيانات ${source.name}؛ احتُفظ بالعناصر السابقة.")
                        break
                    }
                }
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
                ),
            )
        }
        return SyncRunResult(transientFailure)
    }

    private suspend fun fetch(source: SourceDefinition, previous: SyncStateEntity?): FetchOutcome = when (source.id) {
        "github" -> fetchGitHub(source, previous)
        "hf-models" -> fetchSingle(source, previous, source.endpoint) { SourceParsers.huggingFaceModels(it) }
        "hf-spaces" -> fetchSingle(source, previous, source.endpoint) { SourceParsers.huggingFaceSpaces(it) }
        "android-developers", "google-developers", "openai-news" ->
            fetchSingle(source, previous, source.endpoint) { body ->
                SourceParsers.rssFeed(body, source.id, source.name, source.endpoint)
            }
        "prompts-chat" -> fetchSingle(source, previous, source.endpoint) { SourceParsers.promptsChat(it) }
        else -> FetchOutcome(error = "مصدر غير معروف.")
    }

    private suspend fun fetchSingle(
        source: SourceDefinition,
        previous: SyncStateEntity?,
        url: String,
        parser: (String) -> List<ContentItem>,
    ): FetchOutcome {
        val headers = conditionalHeaders(previous)
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
            headerData.copy(items = parser(response.body))
        } catch (_: Exception) {
            headerData.copy(error = "تغير تنسيق بيانات ${source.name}؛ احتُفظ بالنسخة المخزنة.")
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
            etag = metadata.etag ?: previous?.etag,
        )
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
    )

    private companion object {
        const val HOUR = 60 * 60 * 1000L
        const val DAY = 24 * HOUR
        const val RATE_LIMIT_COOLDOWN = HOUR
        const val FAILURE_COOLDOWN = 15 * 60 * 1000L
        val githubQueries = listOf("android language:Kotlin", "android \"Jetpack Compose\"", "android AI")
        val sources = listOf(
            SourceDefinition("github", "GitHub", "https://api.github.com/search/repositories", 6 * HOUR),
            SourceDefinition("hf-models", "Hugging Face Models", "https://huggingface.co/api/models?sort=trendingScore&direction=-1&limit=20&full=true", 3 * HOUR),
            SourceDefinition("hf-spaces", "Hugging Face Spaces", "https://huggingface.co/api/spaces?sort=trendingScore&direction=-1&limit=20&full=true", 3 * HOUR),
            SourceDefinition("android-developers", "Android Developers", "https://android-developers.googleblog.com/feeds/posts/default?alt=rss", 6 * HOUR),
            SourceDefinition("google-developers", "Google Developers", "https://developers.googleblog.com/feeds/posts/default?alt=rss", 6 * HOUR),
            SourceDefinition("openai-news", "OpenAI News", "https://openai.com/news/rss.xml", 6 * HOUR),
            SourceDefinition("prompts-chat", "prompts.chat", "https://datasets-server.huggingface.co/rows?dataset=fka%2Fprompts.chat&config=default&split=train&offset=0&length=100", DAY),
        )
    }
}