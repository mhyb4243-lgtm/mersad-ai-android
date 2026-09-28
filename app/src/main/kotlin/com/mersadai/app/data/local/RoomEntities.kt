package com.mersadai.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "items", indices = [Index("url"), Index("externalId")])
data class ItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val originalTitle: String?,
    val description: String?,
    val originalDescription: String?,
    val url: String?,
    val contentType: String,
    val freeStatus: String,
    val verificationLevel: String,
    val lastVerifiedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val language: String?,
    val thumbnailUrl: String?,
    val externalId: String?,
    val tags: String? = null,
    val license: String? = null,
    val starsCount: Long? = null,
    val forksCount: Long? = null,
    val openIssuesCount: Long? = null,
    val pipelineTag: String? = null,
    val pipelineCategory: String? = null,
    val downloads: Long? = null,
    val likes: Long? = null,
    val trendingScore: Double? = null,
    val sdk: String? = null,
    val emoji: String? = null,
    val publishedAt: Long? = null,
    val author: String? = null,
    val promptForDevelopers: Boolean? = null,
    val promptType: String? = null,
    val contributor: String? = null,
    val archived: Boolean? = null,
    val isFork: Boolean? = null,
    val sourceUpdatedAt: Long? = null,
    val pushedAt: Long? = null,
    val gated: Boolean? = null,
    val isPrivate: Boolean? = null,
)

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val externalId: String?,
    val homepageUrl: String?,
    val apiUrl: String?,
)

@Entity(
    tableName = "item_sources",
    primaryKeys = ["itemId", "sourceId"],
    foreignKeys = [
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sourceId")],
)
data class ItemSourceEntity(val itemId: String, val sourceId: String)

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val parentId: String?)

@Entity(
    tableName = "item_categories",
    primaryKeys = ["itemId", "categoryId"],
    foreignKeys = [
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId")],
)
data class ItemCategoryEntity(val itemId: String, val categoryId: String)

@Entity(
    tableName = "favorites",
    foreignKeys = [ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("savedAt")],
)
data class FavoriteEntity(@PrimaryKey val itemId: String, val savedAt: Long)

@Entity(
    tableName = "translations",
    primaryKeys = ["itemId", "field", "language"],
    foreignKeys = [ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")],
)
data class TranslationEntity(
    val itemId: String,
    val field: String,
    val language: String,
    val translatedText: String,
    val updatedAt: Long,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: String,
    val state: String,
    val lastStartedAt: Long?,
    val lastFinishedAt: Long?,
    val message: String?,
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastHttpStatus: Int? = null,
    val lastError: String? = null,
    val etag: String? = null,
    val lastModifiedHeader: String? = null,
    val rateLimitRemaining: Long? = null,
    val rateLimitResetAt: Long? = null,
    val nextAllowedSyncAt: Long? = null,
    val isSyncing: Boolean = false,
)

@Entity(
    tableName = "star_snapshots",
    primaryKeys = ["sourceId", "itemId", "capturedAt"],
    foreignKeys = [ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")],
)
data class StarSnapshotEntity(
    val sourceId: String,
    val itemId: String,
    val starsCount: Long?,
    val capturedAt: Long,
)
