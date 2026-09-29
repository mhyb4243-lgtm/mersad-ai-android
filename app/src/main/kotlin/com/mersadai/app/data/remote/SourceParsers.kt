package com.mersadai.app.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mersadai.app.data.dedup.DeduplicationService
import com.mersadai.app.domain.model.Category
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import com.mersadai.app.domain.model.Source
import com.mersadai.app.domain.model.VerificationLevel
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.math.min

object SourceParsers {
    fun githubIncompleteResults(body: String): Boolean = JsonParser.parseString(body)
        .asObjectOrNull()?.get("incomplete_results")?.let { runCatching { it.asBoolean }.getOrNull() } == true

    fun githubRepositories(body: String, now: Long = System.currentTimeMillis()): List<ContentItem> {
        val root = JsonParser.parseString(body).asObjectOrNull()
            ?: throw SourceSchemaException("GitHub response is not an object")
        val results = root.array("items") ?: throw SourceSchemaException("GitHub response has no items array")
        return results.mapNotNull { element ->
            val repository = element.asObjectOrNull() ?: return@mapNotNull null
            val owner = repository.obj("owner")
            val id = repository.string("id") ?: return@mapNotNull null
            val name = repository.string("full_name") ?: repository.string("name") ?: return@mapNotNull null
            val license = repository.obj("license")?.string("spdx_id")?.takeUnless { it == "NOASSERTION" }
            val tags = repository.strings("topics")
            ContentItem(
                id = "github:$id",
                externalId = "github:$id",
                title = name,
                originalTitle = repository.string("name"),
                description = repository.string("description")?.shortText(),
                originalDescription = repository.string("description"),
                url = repository.string("html_url")?.httpsUrl(),
                contentType = ContentType.ANDROID_PROJECT,
                category = Category("android", "Android"),
                freeStatus = FreeStatus.UNKNOWN,
                verificationLevel = VerificationLevel.OFFICIAL,
                source = Source("github", "GitHub", "github", "https://github.com", "https://api.github.com"),
                createdAt = repository.string("created_at").epochMillis() ?: now,
                updatedAt = repository.string("updated_at").epochMillis() ?: now,
                sourceUpdatedAt = repository.string("updated_at").epochMillis(),
                pushedAt = repository.string("pushed_at").epochMillis(),
                language = repository.string("language"),
                thumbnailUrl = owner?.string("avatar_url")?.httpsUrl(),
                tags = (tags + listOfNotNull(license?.let { "license:$it" })).distinct(),
                license = license,
                starsCount = repository.long("stargazers_count"),
                forksCount = repository.long("forks_count"),
                openIssuesCount = repository.long("open_issues_count"),
                author = owner?.string("login"),
                archived = repository.boolean("archived"),
                isFork = repository.boolean("fork"),
            )
        }
    }

    fun huggingFaceModels(body: String, now: Long = System.currentTimeMillis()): List<ContentItem> {
        val results = JsonParser.parseString(body).asArrayOrNull()
            ?: throw SourceSchemaException("Hugging Face models response is not an array")
        return results.mapNotNull { element ->
            val model = element.asObjectOrNull() ?: return@mapNotNull null
            val id = model.string("id") ?: return@mapNotNull null
            val tags = model.strings("tags")
            val card = model.obj("cardData")
            val pipeline = model.string("pipeline_tag")
            val license = tags.firstOrNull { it.startsWith("license:", ignoreCase = true) }
                ?.substringAfter(':')?.takeIf(String::isNotBlank)
            val created = model.string("createdAt").epochMillis()
            ContentItem(
                id = "hf:model:$id",
                externalId = "hf:model:$id",
                title = id,
                description = card?.string("description")?.shortText(),
                originalDescription = card?.string("description"),
                url = "https://huggingface.co/$id",
                contentType = ContentType.MODEL,
                category = Category("models", "Models"),
                freeStatus = FreeStatus.UNKNOWN,
                verificationLevel = VerificationLevel.OFFICIAL,
                source = Source("hf-models", "Hugging Face", "huggingface-models", "https://huggingface.co", "https://huggingface.co/api/models"),
                createdAt = created ?: now,
                updatedAt = model.string("lastModified").epochMillis() ?: now,
                sourceUpdatedAt = model.string("lastModified").epochMillis(),
                tags = tags,
                license = license,
                pipelineTag = pipeline,
                pipelineCategory = pipelineCategory(pipeline),
                downloads = model.long("downloads"),
                likes = model.long("likes"),
                trendingScore = model.double("trendingScore"),
                author = model.string("author") ?: id.substringBefore('/'),
                gated = model.boolean("gated"),
                isPrivate = model.boolean("private"),
                libraryName = model.string("library_name"),
            )
        }
    }

    fun huggingFaceSpaces(body: String, now: Long = System.currentTimeMillis()): List<ContentItem> {
        val results = JsonParser.parseString(body).asArrayOrNull()
            ?: throw SourceSchemaException("Hugging Face spaces response is not an array")
        return results.mapNotNull { element ->
            val space = element.asObjectOrNull() ?: return@mapNotNull null
            val id = space.string("id") ?: return@mapNotNull null
            val card = space.obj("cardData")
            val tags = space.strings("tags")
            val created = space.string("createdAt").epochMillis()
            val isAiSpace = hasAiTaskTags(tags)
            ContentItem(
                id = "hf:space:$id",
                externalId = "hf:space:$id",
                title = card?.string("title")?.takeIf(String::isNotBlank) ?: id,
                originalTitle = id,
                description = card?.string("short_description")?.shortText(),
                originalDescription = card?.string("short_description"),
                url = "https://huggingface.co/spaces/$id",
                contentType = if (isAiSpace) ContentType.AI_TOOL else ContentType.OTHER,
                category = if (isAiSpace) Category("ai-tools", "AI Tools") else null,
                freeStatus = FreeStatus.UNKNOWN,
                verificationLevel = VerificationLevel.OFFICIAL,
                source = Source("hf-spaces", "Hugging Face", "huggingface-spaces", "https://huggingface.co/spaces", "https://huggingface.co/api/spaces"),
                createdAt = created ?: now,
                updatedAt = space.string("lastModified").epochMillis() ?: now,
                sourceUpdatedAt = space.string("lastModified").epochMillis(),
                tags = tags,
                likes = space.long("likes"),
                trendingScore = space.double("trendingScore"),
                sdk = space.string("sdk"),
                emoji = card?.string("emoji"),
                author = space.string("author") ?: id.substringBefore('/'),
            )
        }
    }

    fun promptsChat(body: String, now: Long = System.currentTimeMillis()): List<ContentItem> {
        val root = JsonParser.parseString(body).asObjectOrNull()
            ?: throw SourceSchemaException("Dataset response is not an object")
        val rows = root.array("rows") ?: throw SourceSchemaException("Dataset response has no rows array")
        return rows.mapNotNull { element ->
            val record = element.asObjectOrNull()?.obj("row") ?: return@mapNotNull null
            val index = element.asObjectOrNull()?.long("row_idx") ?: return@mapNotNull null
            val act = record.string("act")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val prompt = record.string("prompt")
            ContentItem(
                id = "prompts-chat:$index",
                externalId = "prompts-chat:$index",
                title = act,
                originalTitle = act,
                description = null,
                originalDescription = prompt,
                url = "https://prompts.chat/",
                contentType = ContentType.PROMPT,
                category = Category("prompts", "Prompts"),
                freeStatus = FreeStatus.UNKNOWN,
                verificationLevel = VerificationLevel.COMMUNITY_SOURCE,
                source = Source("prompts-chat", "prompts.chat", "fka/prompts.chat", "https://prompts.chat", "https://datasets-server.huggingface.co/rows"),
                createdAt = now,
                updatedAt = now,
                tags = listOfNotNull(record.string("type")),
                license = "CC0-1.0",
                author = record.string("contributor"),
                promptForDevelopers = record.boolean("for_devs"),
                promptType = record.string("type"),
                contributor = record.string("contributor"),
            )
        }
    }

    fun promptsChatTotalCount(body: String): Long? = runCatching {
        JsonParser.parseString(body).asObjectOrNull()?.long("num_rows_total")
    }.getOrNull()

    fun rssFeed(body: String, sourceId: String, sourceName: String, feedUrl: String, now: Long = System.currentTimeMillis()): List<ContentItem> {
        val document = Jsoup.parse(body, "", Parser.xmlParser())
        val entries = document.getElementsByTag("item").ifEmpty { document.getElementsByTag("entry") }
        val category = when (sourceId) {
            "openai-news" -> Category("ai-news", "AI News")
            "android-developers" -> Category("android-news", "Android News")
            else -> Category("developer-tools", "Developer Tools")
        }
        return entries.mapNotNull { entry ->
            val title = entry.childText("title")?.shortText(240) ?: return@mapNotNull null
            val links = entry.getElementsByTag("link")
            val linkElement = links.firstOrNull { it.attr("rel").isBlank() || it.attr("rel").equals("alternate", true) }
                ?: links.firstOrNull()
            val link = linkElement?.attr("href")?.takeIf(String::isNotBlank)
                ?: linkElement?.text()?.takeIf(String::isNotBlank)
            val safeLink = link?.httpsUrl() ?: return@mapNotNull null
            val originalDescription = entry.childData("description", "summary", "content:encoded")
            val description = originalDescription?.shortText()
            val thumbnailUrl = entry.getElementsByTag("media:thumbnail").firstOrNull()?.attr("url")
                ?: entry.getElementsByTag("enclosure").firstOrNull { it.attr("type").startsWith("image/", ignoreCase = true) }?.attr("url")
            val date = entry.childText("pubDate", "published", "updated", "dc:date").epochMillis()
            val guid = entry.childText("guid", "id")?.takeIf(String::isNotBlank)
            val canonicalUrl = DeduplicationService().canonicalUrl(safeLink) ?: safeLink
            val stableId = guid ?: "url-${canonicalUrl.sha256()}"
            val tags = entry.getElementsByTag("category").mapNotNull { element ->
                (element.attr("term").takeIf(String::isNotBlank) ?: element.text().takeIf(String::isNotBlank))
            }
            ContentItem(
                id = "$sourceId:$stableId",
                externalId = "$sourceId:$stableId",
                title = title,
                originalTitle = title,
                description = description,
                originalDescription = originalDescription,
                url = safeLink,
                contentType = ContentType.NEWS,
                category = category,
                freeStatus = FreeStatus.UNKNOWN,
                verificationLevel = VerificationLevel.OFFICIAL,
                source = Source(sourceId, sourceName, sourceId, feedUrl, feedUrl),
                thumbnailUrl = thumbnailUrl.httpsUrl(),
                createdAt = date ?: now,
                updatedAt = date ?: now,
                sourceUpdatedAt = date,
                publishedAt = date,
                tags = tags,
            )
        }
    }

    fun pipelineCategory(pipeline: String?): String {
        val value = pipeline.orEmpty().lowercase(Locale.ROOT)
        return when {
            value.contains("speech") -> "Speech"
            value.contains("multimodal") || value.contains("visual-question-answering") -> "Multimodal"
            value.contains("video") -> "Video"
            value.contains("image") -> "Image"
            value.contains("audio") -> "Audio"
            value.contains("text") || value.contains("translation") || value.contains("summarization") -> "Text"
            else -> "Other"
        }
    }

    private fun hasAiTaskTags(tags: List<String>): Boolean {
        val aiTags = setOf(
            "ai", "artificial-intelligence", "machine-learning", "deep-learning", "transformers", "diffusers",
            "llm", "large-language-models", "text-generation", "text2text-generation", "text-to-image",
            "image-generation", "text-to-video", "automatic-speech-recognition", "text-to-speech",
            "question-answering", "image-classification", "sentence-similarity", "feature-extraction",
            "translation", "summarization", "computer-vision",
        )
        return tags.any { it.lowercase(Locale.ROOT) in aiTags }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = takeUnless { isJsonNull || !isJsonObject }?.asJsonObject
    private fun JsonElement.asArrayOrNull(): JsonArray? = takeUnless { isJsonNull || !isJsonArray }?.asJsonArray
    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.asObjectOrNull()
    private fun JsonObject.array(key: String): JsonArray? = get(key)?.asArrayOrNull()
    private fun JsonObject.string(key: String): String? = get(key)?.takeUnless { it.isJsonNull || !it.isJsonPrimitive }
        ?.asString?.takeIf(String::isNotBlank)
    private fun JsonObject.long(key: String): Long? = get(key)?.takeUnless { it.isJsonNull || !it.isJsonPrimitive }
        ?.let { runCatching { it.asLong }.getOrNull() }
    private fun JsonObject.double(key: String): Double? = get(key)?.takeUnless { it.isJsonNull || !it.isJsonPrimitive }
        ?.let { runCatching { it.asDouble }.getOrNull() }
    private fun JsonObject.boolean(key: String): Boolean? = get(key)?.takeUnless { it.isJsonNull || !it.isJsonPrimitive }
        ?.let { runCatching { it.asBoolean }.getOrNull() }
    private fun JsonObject.strings(key: String): List<String> = array(key)?.mapNotNull { element ->
        element.takeUnless { it.isJsonNull || !it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)
    }.orEmpty()

    private fun org.jsoup.nodes.Element.childText(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        getElementsByTag(name).firstOrNull()?.let { child ->
            child.attr("href").takeIf(String::isNotBlank) ?: child.text().takeIf(String::isNotBlank)
        }
    }

    private fun org.jsoup.nodes.Element.childData(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        getElementsByTag(name).firstOrNull()?.let { child ->
            child.data().takeIf(String::isNotBlank) ?: child.text().takeIf(String::isNotBlank)
        }
    }

    private fun String?.shortText(limit: Int = 600): String? {
        val text = this?.let { Jsoup.parse(it).text() }?.trim()?.takeIf(String::isNotBlank) ?: return null
        return text.take(min(limit, text.length))
    }

    private fun String?.httpsUrl(): String? = this?.trim()?.takeIf { raw ->
        runCatching { URI(raw).scheme?.equals("https", ignoreCase = true) == true }.getOrDefault(false)
    }

    private fun String?.epochMillis(): Long? {
        val value = this?.trim()?.takeIf(String::isNotBlank) ?: return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching {
                java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).parse(value)?.time
            }.getOrNull()
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

class SourceSchemaException(message: String) : IllegalStateException(message)