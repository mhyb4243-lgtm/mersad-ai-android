package com.mersadai.app.data.dedup

import com.mersadai.app.domain.model.ContentItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DeduplicationServiceTest {
    private val service = DeduplicationService()

    @Test
    fun externalIdHasPriorityOverDifferentUrls() {
        val first = item(id = "one", externalId = "repo-42", url = "https://github.com/a/one")
        val second = item(id = "two", externalId = "repo-42", url = "https://github.com/b/two")

        assertEquals(1, service.unique(listOf(first, second)).size)
        assertEquals("external:repo-42", service.keyFor(first))
    }

    @Test
    fun canonicalUrlIgnoresTrailingSlashAndFragment() {
        val first = item(id = "one", title = "first", url = "https://example.org/tool/#overview")
        val second = item(id = "two", title = "second", url = "https://EXAMPLE.org/tool")

        assertEquals(service.keyFor(first), service.keyFor(second))
    }

    @Test
    fun canonicalUrlRemovesTrackingParameters() {
        assertEquals(
            service.canonicalUrl("https://example.org/tool?utm_source=mail&view=full#details"),
            service.canonicalUrl("https://example.org/tool?view=full"),
        )
    }

    @Test
    fun normalizedTitleDoesNotMergeDifferentSourcesOrTypes() {
        val github = item(id = "one", title = "Getting started").copy(
            source = com.mersadai.app.domain.model.Source("github", "GitHub"),
        )
        val feed = item(id = "two", title = "Getting started").copy(
            contentType = com.mersadai.app.domain.model.ContentType.NEWS,
            source = com.mersadai.app.domain.model.Source("android-news", "Android Developers"),
        )

        assertEquals(2, service.unique(listOf(github, feed)).size)
    }

    private fun item(
        id: String,
        externalId: String? = null,
        url: String? = null,
        title: String = "A tool",
    ) = ContentItem(id = id, title = title, externalId = externalId, url = url, createdAt = 1, updatedAt = 1)
}
