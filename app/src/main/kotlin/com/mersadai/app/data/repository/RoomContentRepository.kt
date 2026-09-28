package com.mersadai.app.data.repository

import com.mersadai.app.data.local.ContentDao
import com.mersadai.app.data.local.ContentDao.Companion.EXTERNAL_SYNC_ID
import com.mersadai.app.data.mapper.toDomain
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.SyncRecord
import com.mersadai.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomContentRepository(private val dao: ContentDao) : ContentRepository {
    override fun observeItems(): Flow<List<ContentItem>> = dao.observeItems().map { rows -> rows.map { it.toDomain() } }

    override fun searchItems(query: String): Flow<List<ContentItem>> =
        dao.searchItems(query.trim()).map { rows -> rows.map { it.toDomain() } }

    override fun observeFavorites(): Flow<List<ContentItem>> =
        dao.observeFavorites().map { rows -> rows.map { it.toDomain() } }

    override fun observeItem(id: String): Flow<ContentItem?> = dao.observeItem(id).map { it?.toDomain() }

    override fun observeFavorite(id: String): Flow<Boolean> = dao.observeFavorite(id)

    override fun observeSyncRecord(): Flow<SyncRecord?> =
        dao.observeSyncState(EXTERNAL_SYNC_ID).map { it?.toDomain() }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        dao.setFavorite(id, favorite, System.currentTimeMillis())
    }

    override suspend fun clearCache() {
        dao.deleteUnfavoritedItems()
    }
}
