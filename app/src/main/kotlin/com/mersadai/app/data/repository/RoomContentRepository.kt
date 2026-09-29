package com.mersadai.app.data.repository

import com.mersadai.app.data.local.ContentDao
import com.mersadai.app.data.mapper.toDomain
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.SyncRecord
import com.mersadai.app.domain.model.SyncState
import com.mersadai.app.domain.repository.ContentRepository
import com.mersadai.app.domain.search.LocalContentSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomContentRepository(private val dao: ContentDao) : ContentRepository {
    override fun observeItems(): Flow<List<ContentItem>> = dao.observeItems().map { rows -> rows.map { it.toDomain() } }

    override fun searchItems(query: String): Flow<List<ContentItem>> =
        dao.observeItems()
            .map { rows -> rows.map { it.toDomain() } }
            .map { items -> LocalContentSearch.search(items, query) }

    override fun observeFavorites(): Flow<List<ContentItem>> =
        dao.observeFavorites().map { rows -> rows.map { it.toDomain() } }

    override fun observeItem(id: String): Flow<ContentItem?> = dao.observeItem(id).map { it?.toDomain() }

    override fun observeFavorite(id: String): Flow<Boolean> = dao.observeFavorite(id)

    override fun observeSyncRecord(): Flow<SyncRecord?> =
        dao.observeSyncStates().map { records ->
            val attempted = records.filter { it.lastAttemptAt != null }
            if (attempted.isEmpty()) return@map null
            val lastSuccess = attempted.mapNotNull { it.lastSuccessAt }.maxOrNull()
            val failures = attempted.count { it.state == SyncState.FAILURE.name }
            SyncRecord(
                state = when {
                    attempted.any { it.isSyncing } -> SyncState.SYNCING
                    lastSuccess != null -> SyncState.SUCCESS
                    else -> SyncState.FAILURE
                },
                lastFinishedAt = attempted.mapNotNull { it.lastFinishedAt }.maxOrNull(),
                message = when {
                    failures == 0 -> null
                    lastSuccess != null -> "تعذر تحديث بعض المصادر؛ ما زالت البيانات المخزنة متاحة."
                    else -> "تعذر الاتصال بالمصادر؛ ما زالت البيانات المخزنة متاحة."
                },
                lastSuccessAt = lastSuccess,
            )
        }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        dao.setFavorite(id, favorite, System.currentTimeMillis())
    }

    override suspend fun clearCache() {
        dao.deleteUnfavoritedItems()
    }
}
