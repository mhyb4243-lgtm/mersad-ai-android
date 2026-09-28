package com.mersadai.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Transaction
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun observeItems(): Flow<List<ItemWithMetadata>>

    @Transaction
    @Query("SELECT items.* FROM items INNER JOIN favorites ON favorites.itemId = items.id ORDER BY favorites.savedAt DESC")
    fun observeFavorites(): Flow<List<ItemWithMetadata>>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    fun observeItem(id: String): Flow<ItemWithMetadata?>

    @Transaction
    @Query("SELECT * FROM items WHERE :query = '' OR title LIKE '%' || :query || '%' OR originalTitle LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR originalDescription LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchItems(query: String): Flow<List<ItemWithMetadata>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE itemId = :id)")
    fun observeFavorite(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE itemId = :id)")
    suspend fun isFavorite(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<ItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: SourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

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

    companion object {
        const val EXTERNAL_SYNC_ID = "external_sources"
    }
}
