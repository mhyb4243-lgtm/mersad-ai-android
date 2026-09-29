package com.mersadai.app.domain.model

enum class ContentType { AI_TOOL, ANDROID_PROJECT, MODEL, PROMPT, NEWS, OTHER }

enum class FreeStatus {
    FULLY_FREE,
    FREE_TIER,
    FREE_CREDIT,
    TEMPORARY_OFFER,
    OPEN_SOURCE,
    OPEN_WEIGHT,
    LOCAL_RUNNABLE,
    EXPIRED,
    UNKNOWN,
}

enum class OpenSourceStatus { OPEN_SOURCE, OPEN_WEIGHT, CLOSED, UNKNOWN }

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
    val openSourceStatus: OpenSourceStatus = OpenSourceStatus.UNKNOWN,
    val verificationLevel: VerificationLevel = VerificationLevel.UNVERIFIED,
    val source: Source? = null,
    val lastVerifiedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val language: String? = null,
    val thumbnailUrl: String? = null,
    val externalId: String? = null,
    val tags: List<String> = emptyList(),
    val license: String? = null,
    val starsCount: Long? = null,
    val forksCount: Long? = null,
    val openIssuesCount: Long? = null,
    val pipelineTag: String? = null,
    val pipelineCategory: String? = null,
    val downloads: Long? = null,
    val likes: Long? = null,
    val trendingScore: Double? = null,
    val sdk: String? = null,
    val emoji: String? = null,
    val publishedAt: Long? = null,
    val author: String? = null,
    val promptForDevelopers: Boolean? = null,
    val promptType: String? = null,
    val contributor: String? = null,
    val archived: Boolean? = null,
    val isFork: Boolean? = null,
    val sourceUpdatedAt: Long? = null,
    val pushedAt: Long? = null,
    val gated: Boolean? = null,
    val isPrivate: Boolean? = null,
    val libraryName: String? = null,
    val requiresAccount: Boolean? = null,
    val requiresPaymentCard: Boolean? = null,
    val freeLimit: String? = null,
    val localRunnable: Boolean? = null,
    val lastVerificationError: String? = null,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val displayTitleAr: String? = null,
    val displayDescriptionAr: String? = null,
) {
    fun isFreeNow(): Boolean = freeStatus in setOf(
        FreeStatus.FULLY_FREE,
        FreeStatus.FREE_TIER,
        FreeStatus.FREE_CREDIT,
        FreeStatus.TEMPORARY_OFFER,
    ) && (endAt == null || endAt > System.currentTimeMillis())

    fun isOpenSource(): Boolean = openSourceStatus == OpenSourceStatus.OPEN_SOURCE ||
        openSourceStatus == OpenSourceStatus.OPEN_WEIGHT ||
        freeStatus in setOf(FreeStatus.OPEN_SOURCE, FreeStatus.OPEN_WEIGHT) ||
        license?.let { value ->
            value.split(Regex("[,/|]"), limit = 20)
                .map(String::trim)
                .any { candidate ->
                    candidate.equals("MIT", ignoreCase = true) ||
                        candidate.equals("Apache-2.0", ignoreCase = true) ||
                        candidate.equals("BSD-3-Clause", ignoreCase = true) ||
                        candidate.equals("GPL-3.0", ignoreCase = true) ||
                        candidate.equals("MPL-2.0", ignoreCase = true) ||
                        candidate.contains("apache", ignoreCase = true) ||
                        candidate.contains("mit", ignoreCase = true)
                }
        } == true ||
        tags.any { tag ->
            tag.contains("open-source", ignoreCase = true) ||
                tag.contains("openweight", ignoreCase = true) ||
                tag.contains("open-weight", ignoreCase = true) ||
                tag.contains("oss", ignoreCase = true)
        }

    fun isOpenWeight(): Boolean = openSourceStatus == OpenSourceStatus.OPEN_WEIGHT ||
        freeStatus == FreeStatus.OPEN_WEIGHT ||
        tags.any { tag ->
            tag.contains("open-weight", ignoreCase = true) ||
                tag.contains("gguf", ignoreCase = true) ||
                tag.contains("onnx", ignoreCase = true) ||
                tag.contains("llama.cpp", ignoreCase = true)
        }

    fun isLocalRunnable(): Boolean = localRunnable == true ||
        freeStatus == FreeStatus.LOCAL_RUNNABLE ||
        tags.any { tag ->
            tag.contains("llama.cpp", ignoreCase = true) ||
                tag.contains("gguf", ignoreCase = true) ||
                tag.contains("onnx", ignoreCase = true) ||
                tag.contains("android-ai", ignoreCase = true) ||
                tag.contains("on-device", ignoreCase = true)
        } ||
        libraryName?.contains("llama.cpp", ignoreCase = true) == true
}

data class SyncRecord(
    val state: SyncState,
    val lastFinishedAt: Long? = null,
    val message: String? = null,
    val lastSuccessAt: Long? = null,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoUpdate: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val notifyAiTools: Boolean = true,
    val notifyAndroidProjects: Boolean = true,
    val notifyModels: Boolean = true,
    val notifyPrompts: Boolean = true,
    val notifyNews: Boolean = true,
    val notificationPermissionRequested: Boolean = false,
)
