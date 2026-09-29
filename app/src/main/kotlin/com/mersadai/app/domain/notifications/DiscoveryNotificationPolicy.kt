package com.mersadai.app.domain.notifications

import com.mersadai.app.domain.model.AppSettings

enum class DiscoveryNotificationType(val storageKey: String) {
    AI_TOOLS("ai-tools"),
    ANDROID_PROJECTS("android-projects"),
    MODELS("models"),
    PROMPTS("prompts"),
    NEWS("news"),
    ;

    fun enabled(settings: AppSettings): Boolean = when (this) {
        AI_TOOLS -> settings.notifyAiTools
        ANDROID_PROJECTS -> settings.notifyAndroidProjects
        MODELS -> settings.notifyModels
        PROMPTS -> settings.notifyPrompts
        NEWS -> settings.notifyNews
    }

    companion object {
        fun fromStorageKey(value: String): DiscoveryNotificationType? = entries.firstOrNull { it.storageKey == value }
    }
}

data class DiscoveryNotificationCandidate(
    val notificationKey: String,
    val itemId: String,
    val notificationType: String,
    val discoveredAt: Long,
)

data class NotificationBatch(
    val candidates: List<DiscoveryNotificationCandidate>,
    val counts: Map<DiscoveryNotificationType, Int>,
) {
    val total: Int get() = candidates.size
}

data class NotificationPlan(
    val batch: NotificationBatch?,
    val handledKeys: Set<String>,
)

object DiscoveryNotificationPolicy {
    private const val MAX_AGE_MILLIS = 7 * 24 * 60 * 60 * 1000L

    fun plan(
        candidates: List<DiscoveryNotificationCandidate>,
        settings: AppSettings,
        notificationPermissionGranted: Boolean,
        now: Long,
    ): NotificationPlan {
        if (!settings.notificationsEnabled) return NotificationPlan(batch = null, handledKeys = emptySet())

        val distinctCandidates = candidates.distinctBy(DiscoveryNotificationCandidate::notificationKey)
        val recent = distinctCandidates.filter { it.discoveredAt in (now - MAX_AGE_MILLIS)..now }
        val enabled = recent.filter { candidate ->
            DiscoveryNotificationType.fromStorageKey(candidate.notificationType)?.enabled(settings) == true
        }
        val handled = distinctCandidates.asSequence()
            .filterNot { it in recent && DiscoveryNotificationType.fromStorageKey(it.notificationType)?.enabled(settings) == true }
            .map(DiscoveryNotificationCandidate::notificationKey)
            .toSet()

        if (!notificationPermissionGranted || enabled.isEmpty()) {
            return NotificationPlan(batch = null, handledKeys = handled)
        }

        val counts = enabled.mapNotNull { candidate ->
            DiscoveryNotificationType.fromStorageKey(candidate.notificationType)
        }.groupingBy { it }.eachCount()
        return NotificationPlan(NotificationBatch(enabled, counts), distinctCandidates.mapTo(mutableSetOf()) { it.notificationKey })
    }
}