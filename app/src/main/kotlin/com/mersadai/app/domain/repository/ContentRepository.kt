package com.mersadai.app.domain.repository

import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.SyncRecord
import kotlinx.coroutines.flow.Flow

interface ContentRepository {
    fun observeItems(): Flow<List<ContentItem>>
    fun searchItems(query: String): Flow<List<ContentItem>>
    fun observeFavorites(): Flow<List<ContentItem>>
    fun observeItem(id: String): Flow<ContentItem?>
    fun observeFavorite(id: String): Flow<Boolean>
    fun observeSyncRecord(): Flow<SyncRecord?>
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun clearCache()
}
