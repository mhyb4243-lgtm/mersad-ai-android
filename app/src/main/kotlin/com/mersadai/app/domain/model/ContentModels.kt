package com.mersadai.app.domain.model

enum class ContentType { AI_TOOL, ANDROID_PROJECT, MODEL, PROMPT, NEWS, OTHER }

enum class FreeStatus { FULLY_FREE, FREE_TIER, FREE_CREDIT, TEMPORARY_OFFER, EXPIRED, UNKNOWN }

enum class VerificationLevel { OFFICIAL, COMMUNITY_SOURCE, UNVERIFIED }

enum class TriState { YES, NO, UNKNOWN }

enum class SyncState { IDLE, SYNCING, SUCCESS, FAILURE, NOT_CONFIGURED }

data class Category(val id: String, val name: String, val parentId: String? = null)

data class Source(
    val id: String,
    val name: String,
    val externalId: String? = null,
    val homepageUrl: String? = null,
    val apiUrl: String? = null,
)

data class ContentItem(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val description: String? = null,
    val originalDescription: String? = null,
    val url: String? = null,
    val contentType: ContentType = ContentType.OTHER,
    val category: Category? = null,
    val freeStatus: FreeStatus = FreeStatus.UNKNOWN,
    val verificationLevel: VerificationLevel = VerificationLevel.UNVERIFIED,
    val source: Source? = null,
    val lastVerifiedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val language: String? = null,
    val thumbnailUrl: String? = null,
    val externalId: String? = null,
)

data class SyncRecord(val state: SyncState, val lastFinishedAt: Long? = null, val message: String? = null)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(val themeMode: ThemeMode = ThemeMode.SYSTEM, val autoUpdate: Boolean = false)
