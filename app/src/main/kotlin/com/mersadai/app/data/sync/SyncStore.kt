package com.mersadai.app.data.sync

import com.mersadai.app.data.local.CategoryEntity
import com.mersadai.app.data.local.ContentDao
import com.mersadai.app.data.local.ItemEntity
import com.mersadai.app.data.local.NotificationHistoryEntity
import com.mersadai.app.data.local.SourceEntity
import com.mersadai.app.data.local.SyncStateEntity

interface SyncStore {
    suspend fun getState(sourceId: String): SyncStateEntity?
    suspend fun saveState(state: SyncStateEntity)
    suspend fun saveContent(
        item: ItemEntity,
        source: SourceEntity,
        category: CategoryEntity?,
    ): Boolean
    suspend fun enqueueNotifications(entries: List<NotificationHistoryEntity>)
}

class RoomSyncStore(private val dao: ContentDao) : SyncStore {
    override suspend fun getState(sourceId: String): SyncStateEntity? = dao.getSyncState(sourceId)

    override suspend fun saveState(state: SyncStateEntity) = dao.upsertSyncState(state)

    override suspend fun saveContent(
        item: ItemEntity,
        source: SourceEntity,
        category: CategoryEntity?,
    ): Boolean = dao.upsertRemoteContent(item, source, category)

    override suspend fun enqueueNotifications(entries: List<NotificationHistoryEntity>) {
        if (entries.isNotEmpty()) dao.enqueueNotificationHistory(entries)
    }
}