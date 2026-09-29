package com.mersadai.app.data.sync

import com.mersadai.app.domain.model.ContentItem

data class SyncRunResult(
    val hasTransientFailure: Boolean,
    val newlyDiscoveredItems: List<ContentItem> = emptyList(),
)

interface SyncCoordinator {
    suspend fun synchronize(force: Boolean = false): SyncRunResult
}

class DeferredSyncCoordinator : SyncCoordinator {
    override suspend fun synchronize(force: Boolean): SyncRunResult = SyncRunResult(false)
}
