package com.mersadai.app.domain

import com.mersadai.app.data.local.ItemEntity
import com.mersadai.app.data.local.ItemWithMetadata
import com.mersadai.app.data.mapper.toDomain
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import com.mersadai.app.domain.model.VerificationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentModelTest {
    @Test
    fun missingExternalFieldsRemainNullable() {
        val item = ItemWithMetadata(
            item = ItemEntity(
                id = "local-id",
                title = "Local item",
                originalTitle = null,
                description = null,
                originalDescription = null,
                url = null,
                contentType = ContentType.OTHER.name,
                freeStatus = FreeStatus.UNKNOWN.name,
                verificationLevel = VerificationLevel.UNVERIFIED.name,
                lastVerifiedAt = null,
                createdAt = 1L,
                updatedAt = 1L,
                language = null,
                thumbnailUrl = null,
                externalId = null,
            ),
            sources = emptyList(),
            categories = emptyList(),
        ).toDomain()

        assertNull(item.url)
        assertNull(item.externalId)
        assertNull(item.source)
        assertEquals(FreeStatus.UNKNOWN, item.freeStatus)
    }

    @Test
    fun unknownIsNotFreeTier() {
        assertNotEquals(FreeStatus.FREE_TIER, FreeStatus.UNKNOWN)
        assertEquals(FreeStatus.UNKNOWN, FreeStatus.valueOf("UNKNOWN"))
    }
}
