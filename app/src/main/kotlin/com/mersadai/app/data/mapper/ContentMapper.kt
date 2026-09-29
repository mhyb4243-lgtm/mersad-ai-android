package com.mersadai.app.data.mapper

import com.mersadai.app.data.local.ItemEntity
import com.mersadai.app.data.local.ItemWithMetadata
import com.mersadai.app.data.local.CategoryEntity
import com.mersadai.app.data.local.SourceEntity
import com.mersadai.app.data.local.SyncStateEntity
import com.mersadai.app.domain.model.Category
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import com.mersadai.app.domain.model.SyncRecord
import com.mersadai.app.domain.model.SyncState
import com.mersadai.app.domain.model.Source
import com.mersadai.app.domain.model.VerificationLevel
import com.mersadai.app.data.translation.TranslationFields

fun ContentItem.toEntity(): ItemEntity = ItemEntity(
    id = id,
    title = title,
    originalTitle = originalTitle,
    description = description,
    originalDescription = originalDescription,
    url = url,
    contentType = contentType.name,
    freeStatus = freeStatus.name,
    openSourceStatus = openSourceStatus.name,
    verificationLevel = verificationLevel.name,
    lastVerifiedAt = lastVerifiedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    language = language,
    thumbnailUrl = thumbnailUrl,
    externalId = externalId,
    tags = tags.takeIf { it.isNotEmpty() }?.joinToString("\n"),
    license = license,
    starsCount = starsCount,
    forksCount = forksCount,
    openIssuesCount = openIssuesCount,
    pipelineTag = pipelineTag,
    pipelineCategory = pipelineCategory,
    downloads = downloads,
    likes = likes,
    trendingScore = trendingScore,
    sdk = sdk,
    emoji = emoji,
    publishedAt = publishedAt,
    author = author,
    promptForDevelopers = promptForDevelopers,
    promptType = promptType,
    contributor = contributor,
    archived = archived,
    isFork = isFork,
    sourceUpdatedAt = sourceUpdatedAt,
    pushedAt = pushedAt,
    gated = gated,
    isPrivate = isPrivate,
    libraryName = libraryName,
    requiresAccount = requiresAccount,
    requiresPaymentCard = requiresPaymentCard,
    freeLimit = freeLimit,
    localRunnable = localRunnable,
    lastVerificationError = lastVerificationError,
    startAt = startAt,
    endAt = endAt,
)

fun com.mersadai.app.domain.model.Source.toEntity(): SourceEntity = SourceEntity(
    id = id,
    name = name,
    externalId = externalId,
    homepageUrl = homepageUrl,
    apiUrl = apiUrl,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(id = id, name = name, parentId = parentId)

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
    openSourceStatus = runCatching { com.mersadai.app.domain.model.OpenSourceStatus.valueOf(item.openSourceStatus ?: "UNKNOWN") }
        .getOrDefault(com.mersadai.app.domain.model.OpenSourceStatus.UNKNOWN),
    verificationLevel = runCatching { VerificationLevel.valueOf(item.verificationLevel) }
        .getOrDefault(VerificationLevel.UNVERIFIED),
    source = sources.firstOrNull()?.let { Source(it.id, it.name, it.externalId, it.homepageUrl, it.apiUrl) },
    lastVerifiedAt = item.lastVerifiedAt,
    createdAt = item.createdAt,
    updatedAt = item.updatedAt,
    language = item.language,
    thumbnailUrl = item.thumbnailUrl,
    externalId = item.externalId,
    tags = item.tags?.split('\n')?.filter(String::isNotBlank).orEmpty(),
    license = item.license,
    starsCount = item.starsCount,
    forksCount = item.forksCount,
    openIssuesCount = item.openIssuesCount,
    pipelineTag = item.pipelineTag,
    pipelineCategory = item.pipelineCategory,
    downloads = item.downloads,
    likes = item.likes,
    trendingScore = item.trendingScore,
    sdk = item.sdk,
    emoji = item.emoji,
    publishedAt = item.publishedAt,
    author = item.author,
    promptForDevelopers = item.promptForDevelopers,
    promptType = item.promptType,
    contributor = item.contributor,
    archived = item.archived,
    isFork = item.isFork,
    sourceUpdatedAt = item.sourceUpdatedAt,
    pushedAt = item.pushedAt,
    gated = item.gated,
    isPrivate = item.isPrivate,
    libraryName = item.libraryName,
    requiresAccount = item.requiresAccount,
    requiresPaymentCard = item.requiresPaymentCard,
    freeLimit = item.freeLimit,
    localRunnable = item.localRunnable,
    lastVerificationError = item.lastVerificationError,
    startAt = item.startAt,
    endAt = item.endAt,
    displayTitleAr = translations.firstOrNull {
        it.field == TranslationFields.TITLE && it.language == TranslationFields.ARABIC &&
            it.sourceLanguage == "en" && it.sourceText == item.title
    }?.translatedText,
    displayDescriptionAr = translations.firstOrNull {
        it.field == TranslationFields.DESCRIPTION && it.language == TranslationFields.ARABIC &&
            it.sourceLanguage == "en" && it.sourceText == (item.description ?: item.originalDescription)
    }?.translatedText,
)

fun SyncStateEntity.toDomain(): SyncRecord = SyncRecord(
    state = runCatching { SyncState.valueOf(state) }.getOrDefault(SyncState.IDLE),
    lastFinishedAt = lastFinishedAt,
    message = message,
    lastSuccessAt = lastSuccessAt,
)
