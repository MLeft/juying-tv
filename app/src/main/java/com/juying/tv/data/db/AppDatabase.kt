package com.juying.tv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SourceEntity::class,
        SeriesEntity::class,
        MediaEntity::class,
        CollectionEntity::class,
        CollectionItemEntity::class,
        ProgressEntity::class,
        ScrapeMetaEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun seriesDao(): SeriesDao
    abstract fun mediaDao(): MediaDao
    abstract fun collectionDao(): CollectionDao
    abstract fun progressDao(): ProgressDao
    abstract fun scrapeMetaDao(): ScrapeMetaDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scrape_meta` (
                        `seriesId` TEXT NOT NULL,
                        `provider` TEXT NOT NULL,
                        `externalId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `originalTitle` TEXT,
                        `year` INTEGER,
                        `overview` TEXT,
                        `posterUrl` TEXT,
                        `matchedScore` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`seriesId`),
                        FOREIGN KEY(`seriesId`) REFERENCES `series`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scrape_meta_externalId` ON `scrape_meta` (`externalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scrape_meta_provider_externalId` ON `scrape_meta` (`provider`, `externalId`)")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "juying.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
