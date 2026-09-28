package com.mersadai.app.data.sync

import com.mersadai.app.domain.model.SyncState

interface SyncCoordinator {
    suspend fun synchronize(): SyncState
}

class DeferredSyncCoordinator : SyncCoordinator {
    override suspend fun synchronize(): SyncState = SyncState.NOT_CONFIGURED
}
