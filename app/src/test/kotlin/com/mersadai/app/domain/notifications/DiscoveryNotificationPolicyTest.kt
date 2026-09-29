package com.mersadai.app.domain.notifications

import com.mersadai.app.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryNotificationPolicyTest {
    @Test
    fun batchesMultipleTypesAndDeduplicatesByStableNotificationKey() {
        val settings = AppSettings(notificationsEnabled = true)
        val candidates = listOf(
            candidate("github:1", "android-projects"),
            candidate("github:1", "android-projects"),
            candidate("news:1", "news"),
        )

        val plan = DiscoveryNotificationPolicy.plan(candidates, settings, notificationPermissionGranted = true, now = NOW)

        assertEquals(2, plan.batch?.total)
        assertEquals(1, plan.batch?.counts?.get(DiscoveryNotificationType.ANDROID_PROJECTS))
        assertEquals(1, plan.batch?.counts?.get(DiscoveryNotificationType.NEWS))
        assertEquals(setOf("github:1", "news:1"), plan.handledKeys)
    }

    @Test
    fun oldItemsAndDisabledTypesAreHandledWithoutCreatingABatch() {
        val settings = AppSettings(notificationsEnabled = true, notifyPrompts = false)
        val candidates = listOf(
            candidate("old", "news", discoveredAt = NOW - 30L * 24 * 60 * 60 * 1000),
            candidate("muted", "prompts"),
        )

        val plan = DiscoveryNotificationPolicy.plan(candidates, settings, notificationPermissionGranted = true, now = NOW)

        assertNull(plan.batch)
        assertEquals(setOf("old", "muted"), plan.handledKeys)
    }

    @Test
    fun deniedRuntimePermissionDoesNotConsumeEnabledCandidates() {
        val plan = DiscoveryNotificationPolicy.plan(
            listOf(candidate("new", "ai-tools")),
            AppSettings(notificationsEnabled = true),
            notificationPermissionGranted = false,
            now = NOW,
        )

        assertNull(plan.batch)
        assertTrue(plan.handledKeys.isEmpty())
    }

    private fun candidate(key: String, type: String, discoveredAt: Long = NOW) =
        DiscoveryNotificationCandidate(key, key, type, discoveredAt)

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}