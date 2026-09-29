package com.mersadai.app.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class ItemWithMetadata(
    @Embedded val item: ItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(value = ItemSourceEntity::class, parentColumn = "itemId", entityColumn = "sourceId"),
    )
    val sources: List<SourceEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(value = ItemCategoryEntity::class, parentColumn = "itemId", entityColumn = "categoryId"),
    )
    val categories: List<CategoryEntity>,
    @Relation(parentColumn = "id", entityColumn = "itemId")
    val translations: List<TranslationEntity> = emptyList(),
)
