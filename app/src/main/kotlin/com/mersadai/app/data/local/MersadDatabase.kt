package com.mersadai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ItemEntity::class,
        SourceEntity::class,
        ItemSourceEntity::class,
        CategoryEntity::class,
        ItemCategoryEntity::class,
        FavoriteEntity::class,
        TranslationEntity::class,
        SyncStateEntity::class,
        StarSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MersadDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao

    companion object {
        fun create(context: Context): MersadDatabase =
            Room.databaseBuilder(context, MersadDatabase::class.java, "mersad.db").build()
    }
}
