package com.mersadai.app.data.sync

import com.mersadai.app.data.local.CategoryEntity
import com.mersadai.app.data.local.ContentDao
import com.mersadai.app.data.local.ItemEntity
import com.mersadai.app.data.local.SourceEntity
import com.mersadai.app.data.local.SyncStateEntity

interface SyncStore {
    suspend fun getState(sourceId: String): SyncStateEntity?
    suspend fun saveState(state: SyncStateEntity)
    suspend fun saveContent(item: ItemEntity, source: SourceEntity, category: CategoryEntity?)
}

class RoomSyncStore(private val dao: ContentDao) : SyncStore {
    override suspend fun getState(sourceId: String): SyncStateEntity? = dao.getSyncState(sourceId)

    override suspend fun saveState(state: SyncStateEntity) = dao.upsertSyncState(state)

    override suspend fun saveContent(item: ItemEntity, source: SourceEntity, category: CategoryEntity?) {
        dao.upsertRemoteContent(item, source, category)
    }
}