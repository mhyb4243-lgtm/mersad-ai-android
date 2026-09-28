package com.mersadai.app.data.mapper

import com.mersadai.app.data.local.ItemEntity
import com.mersadai.app.data.local.ItemWithMetadata
import com.mersadai.app.data.local.SyncStateEntity
import com.mersadai.app.domain.model.Category
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import com.mersadai.app.domain.model.SyncRecord
import com.mersadai.app.domain.model.SyncState
import com.mersadai.app.domain.model.Source
import com.mersadai.app.domain.model.VerificationLevel

fun ItemWithMetadata.toDomain(): ContentItem = ContentItem(
    id = item.id,
    title = item.title,
    originalTitle = item.originalTitle,
    description = item.description,
    originalDescription = item.originalDescription,
    url = item.url,
    contentType = runCatching { ContentType.valueOf(item.contentType) }.getOrDefault(ContentType.OTHER),
    category = categories.firstOrNull()?.let { Category(it.id, it.name, it.parentId) },
    freeStatus = runCatching { FreeStatus.valueOf(item.freeStatus) }.getOrDefault(FreeStatus.UNKNOWN),
    verificationLevel = runCatching { VerificationLevel.valueOf(item.verificationLevel) }
        .getOrDefault(VerificationLevel.UNVERIFIED),
    source = sources.firstOrNull()?.let { Source(it.id, it.name, it.externalId, it.homepageUrl, it.apiUrl) },
    lastVerifiedAt = item.lastVerifiedAt,
    createdAt = item.createdAt,
    updatedAt = item.updatedAt,
    language = item.language,
    thumbnailUrl = item.thumbnailUrl,
    externalId = item.externalId,
)

fun SyncStateEntity.toDomain(): SyncRecord = SyncRecord(
    state = runCatching { SyncState.valueOf(state) }.getOrDefault(SyncState.IDLE),
    lastFinishedAt = lastFinishedAt,
    message = message,
)
