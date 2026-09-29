package com.mersadai.app.data.sync

data class SyncRunResult(val hasTransientFailure: Boolean)

interface SyncCoordinator {
    suspend fun synchronize(force: Boolean = false): SyncRunResult
}

class DeferredSyncCoordinator : SyncCoordinator {
    override suspend fun synchronize(force: Boolean): SyncRunResult = SyncRunResult(false)
}
