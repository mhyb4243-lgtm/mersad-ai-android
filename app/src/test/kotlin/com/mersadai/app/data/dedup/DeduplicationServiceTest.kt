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

    private fun item(
        id: String,
        externalId: String? = null,
        url: String? = null,
        title: String = "A tool",
    ) = ContentItem(id = id, title = title, externalId = externalId, url = url, createdAt = 1, updatedAt = 1)
}
