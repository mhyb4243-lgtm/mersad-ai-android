package com.mersadai.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiClassifierTest {
    @Test
    fun freeStatusSupportsExplicitFreeAiSignals() {
        val free = ContentItem(
            id = "free-1",
            title = "Test service",
            contentType = ContentType.AI_TOOL,
            freeStatus = FreeStatus.FULLY_FREE,
            license = "MIT",
            createdAt = 1L,
            updatedAt = 1L,
        )

        val tier = ContentItem(
            id = "free-tier",
            title = "Tier service",
            contentType = ContentType.AI_TOOL,
            freeStatus = FreeStatus.FREE_TIER,
            requiresAccount = true,
            requiresPaymentCard = false,
            freeLimit = "10 messages/day",
            createdAt = 1L,
            updatedAt = 1L,
        )

        assertTrue(free.isFreeNow())
        assertTrue(free.isOpenSource())
        assertEquals(FreeStatus.OPEN_SOURCE, free.classifiedFreeStatus())
        assertTrue(tier.isFreeNow())
        assertFalse(tier.requiresPaymentCard == true)
    }

    @Test
    fun openSourceAndLocalClassificationIsDerivedFromMetadata() {
        val model = ContentItem(
            id = "model-1",
            title = "Llama.cpp",
            contentType = ContentType.MODEL,
            freeStatus = FreeStatus.UNKNOWN,
            license = "MIT",
            tags = listOf("gguf", "llama.cpp", "open-weight"),
            libraryName = "llama.cpp",
            localRunnable = true,
            createdAt = 1L,
            updatedAt = 1L,
        )

        assertTrue(model.isOpenSource())
        assertTrue(model.isLocalRunnable())
        assertTrue(model.isOpenWeight())
    }

    @Test
    fun unknownFreeStateIsNotPromotedToFree() {
        val unknown = ContentItem(
            id = "unknown-1",
            title = "Unverified tool",
            contentType = ContentType.AI_TOOL,
            freeStatus = FreeStatus.UNKNOWN,
            createdAt = 1L,
            updatedAt = 1L,
        )

        assertFalse(unknown.isFreeNow())
        assertFalse(unknown.isOpenSource())
        assertFalse(unknown.isLocalRunnable())
    }

    @Test
    fun explicitOpenSourceLicensesAreClassifiedAsFreeAndOpenSource() {
        listOf("apache-2.0", "MIT", "bsd-3-clause").forEach { license ->
            val item = ContentItem(
                id = license,
                title = "Licensed model",
                contentType = ContentType.MODEL,
                license = license,
                createdAt = 1L,
                updatedAt = 1L,
            )

            assertEquals(FreeStatus.OPEN_SOURCE, item.classifiedFreeStatus())
            assertTrue(item.isFreeNow())
            assertTrue(item.isOpenSource())
        }
    }
}
