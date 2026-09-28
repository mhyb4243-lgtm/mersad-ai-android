package com.mersadai.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Transaction
    @Query("SELECT * FROM items ORDER BY COALESCE(publishedAt, sourceUpdatedAt, createdAt) DESC")
    fun observeItems(): Flow<List<ItemWithMetadata>>

    @Transaction
    @Query("SELECT items.* FROM items INNER JOIN favorites ON favorites.itemId = items.id ORDER BY favorites.savedAt DESC")
    fun observeFavorites(): Flow<List<ItemWithMetadata>>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    fun observeItem(id: String): Flow<ItemWithMetadata?>

    @Transaction
    @Query("SELECT * FROM items WHERE :query = '' OR title LIKE '%' || :query || '%' OR originalTitle LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR originalDescription LIKE '%' || :query || '%' OR EXISTS (SELECT 1 FROM sources INNER JOIN item_sources ON sources.id = item_sources.sourceId WHERE item_sources.itemId = items.id AND sources.name LIKE '%' || :query || '%') OR tags LIKE '%' || :query || '%' OR id IN (SELECT item_categories.itemId FROM item_categories INNER JOIN categories ON categories.id = item_categories.categoryId WHERE categories.name LIKE '%' || :query || '%') ORDER BY COALESCE(publishedAt, sourceUpdatedAt, createdAt) DESC")
    fun searchItems(query: String): Flow<List<ItemWithMetadata>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE itemId = :id)")
    fun observeFavorite(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE itemId = :id)")
    suspend fun isFavorite(id: String): Boolean

    @Upsert
    suspend fun upsertItem(item: ItemEntity)

    @Upsert
    suspend fun upsertItems(items: List<ItemEntity>)

    @Upsert
    suspend fun upsertSource(source: SourceEntity)

    @Upsert
    suspend fun upsertCategory(category: CategoryEntity)

    @Transaction
    suspend fun upsertRemoteContent(item: ItemEntity, source: SourceEntity, category: CategoryEntity?) {
        upsertSource(source)
        upsertItem(item)
        upsertItemSource(ItemSourceEntity(item.id, source.id))
        if (category != null) {
            upsertCategory(category)
            upsertItemCategory(ItemCategoryEntity(item.id, category.id))
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItemSource(relation: ItemSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItemCategory(relation: ItemCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTranslation(translation: TranslationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStarSnapshot(snapshot: StarSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE itemId = :id")
    suspend fun deleteFavorite(id: String)

    @Transaction
    suspend fun setFavorite(id: String, favorite: Boolean, savedAt: Long) {
        if (favorite) insertFavorite(FavoriteEntity(id, savedAt)) else deleteFavorite(id)
    }

    @Query("DELETE FROM items WHERE id NOT IN (SELECT itemId FROM favorites)")
    suspend fun deleteUnfavoritedItems()

    @Query("SELECT * FROM sync_state WHERE id = :id LIMIT 1")
    fun observeSyncState(id: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state")
    fun observeSyncStates(): Flow<List<SyncStateEntity>>

    @Query("SELECT * FROM sync_state WHERE id = :id LIMIT 1")
    suspend fun getSyncState(id: String): SyncStateEntity?

    companion object {
        const val EXTERNAL_SYNC_ID = "external_sources"
    }
}
