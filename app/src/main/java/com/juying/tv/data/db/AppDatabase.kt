package com.juying.tv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SourceEntity::class,
        SeriesEntity::class,
        MediaEntity::class,
        CollectionEntity::class,
        CollectionItemEntity::class,
        ProgressEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun seriesDao(): SeriesDao
    abstract fun mediaDao(): MediaDao
    abstract fun collectionDao(): CollectionDao
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "juying.db"
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
