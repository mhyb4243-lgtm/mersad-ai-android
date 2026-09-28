package com.mersadai.app.data.dedup

import com.mersadai.app.domain.model.ContentItem
import java.net.URI
import java.util.Locale

class DeduplicationService {
    fun keyFor(item: ContentItem): String {
        item.externalId.normalized()?.let { return "external:$it" }
        canonicalUrl(item.url)?.let { return "url:$it" }
        item.title.normalized()?.let { return "title:$it" }
        return "source:${item.source?.id.orEmpty()}:${item.id}"
    }

    fun unique(items: Iterable<ContentItem>): List<ContentItem> = items.distinctBy(::keyFor)

    private fun canonicalUrl(value: String?): String? {
        val raw = value.normalized() ?: return null
        return runCatching {
            val uri = URI(raw)
            if (uri.scheme != "https" && uri.scheme != "http") return null
            URI(
                uri.scheme.lowercase(Locale.ROOT), uri.userInfo, uri.host?.lowercase(Locale.ROOT), uri.port,
                uri.path?.trimEnd('/').orEmpty().ifEmpty { "/" }, uri.query, null,
            ).toString()
        }.getOrNull()
    }

    private fun String?.normalized(): String? =
        this?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
}
