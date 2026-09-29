package com.mersadai.app.data.dedup

import com.mersadai.app.domain.model.ContentItem
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

class DeduplicationService {
    fun keyFor(item: ContentItem): String {
        item.externalId.normalized()?.let { return "external:$it" }
        canonicalUrl(item.url)?.let { return "url:$it" }
        val sourceId = item.source?.id.normalized().orEmpty()
        item.source?.id?.normalized()?.let { return "source-id:$it:${item.id.lowercase(Locale.ROOT)}" }
        item.title.normalized()?.let { return "title:${item.contentType.name}:$it" }
        return "source:$sourceId:${item.contentType.name}:${item.id}"
    }

    fun unique(items: Iterable<ContentItem>): List<ContentItem> = items.distinctBy(::keyFor)

    fun canonicalUrl(value: String?): String? {
        val raw = value.normalized() ?: return null
        return runCatching {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "https" && scheme != "http") return null
            val query = uri.rawQuery?.split('&')?.filterNot { parameter ->
                val rawKey = parameter.substringBefore('=')
                val key = runCatching { URLDecoder.decode(rawKey, "UTF-8") }.getOrDefault(rawKey)
                key.startsWith("utm_", ignoreCase = true)
            }?.joinToString("&")?.takeIf(String::isNotEmpty)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val normalizedHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
            val port = when {
                scheme == "https" && uri.port == 443 -> ""
                scheme == "http" && uri.port == 80 -> ""
                uri.port < 0 -> ""
                else -> ":${uri.port}"
            }
            val path = uri.rawPath?.trimEnd('/').orEmpty().ifEmpty { "/" }
            URI("$scheme://$normalizedHost$port$path${query?.let { "?$it" }.orEmpty()}").toString()
        }.getOrNull()
    }

    private fun String?.normalized(): String? =
        this?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
}
