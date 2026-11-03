package com.juying.tv.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY createdAt ASC")
    suspend fun getAll(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun find(id: String): SourceEntity?

    @Upsert
    suspend fun upsert(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("SELECT * FROM sources WHERE type = :type AND config = :config LIMIT 1")
    suspend fun findByConfig(type: String, config: String): SourceEntity?
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE hidden = 0 ORDER BY autoTitle COLLATE NOCASE ASC")
    fun observeVisible(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE hidden = 0 AND kidsAllowed = 1 ORDER BY autoTitle COLLATE NOCASE ASC")
    fun observeKidsVisible(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE hidden = 1 ORDER BY autoTitle COLLATE NOCASE ASC")
    fun observeHidden(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series ORDER BY autoTitle COLLATE NOCASE ASC")
    suspend fun getAll(): List<SeriesEntity>

    @Query("UPDATE series SET kidsAllowed = :allow, updatedAt = :now")
    suspend fun setAllKidsAllowed(allow: Boolean, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM series WHERE hidden = 0")
    fun observeVisibleCount(): Flow<Int>

    @Query("SELECT * FROM series WHERE id = :id")
    fun observeById(id: String): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun find(id: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE sourceId = :sourceId")
    suspend fun bySource(sourceId: String): List<SeriesEntity>

    @Upsert
    suspend fun upsert(item: SeriesEntity)

    @Upsert
    suspend fun upsertAll(items: List<SeriesEntity>)

    @Query("UPDATE series SET customTitle = :title, updatedAt = :now WHERE id = :id")
    suspend fun setCustomTitle(id: String, title: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE series SET coverUrl = :url, updatedAt = :now WHERE id = :id")
    suspend fun setCoverUrl(id: String, url: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE series SET hidden = :hidden, updatedAt = :now WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE series SET kidsAllowed = :allow, updatedAt = :now WHERE id = :id")
    suspend fun setKidsAllowed(id: String, allow: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM series WHERE sourceId = :sourceId AND id NOT IN (SELECT DISTINCT seriesId FROM media WHERE sourceId = :sourceId)")
    suspend fun deleteEmpty(sourceId: String)

    @Query("DELETE FROM series WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("SELECT COUNT(*) FROM series")
    suspend fun count(): Int
}

@Dao
interface MediaDao {
    @Query(
        "SELECT * FROM media WHERE seriesId = :seriesId AND hidden = 0 " +
            "AND (:kidsOnly = 0 OR kidsAllowed = 1) " +
            "ORDER BY season ASC, episode ASC, sortIndex ASC, fileName ASC"
    )
    fun observeBySeries(seriesId: String, kidsOnly: Boolean): Flow<List<MediaEntity>>

    @Query(
        "SELECT * FROM media WHERE seriesId = :seriesId AND hidden = 0 " +
            "AND (:kidsOnly = 0 OR kidsAllowed = 1) " +
            "ORDER BY season ASC, episode ASC, sortIndex ASC, fileName ASC"
    )
    suspend fun getBySeries(seriesId: String, kidsOnly: Boolean): List<MediaEntity>

    @Query("SELECT * FROM media WHERE seriesId = :seriesId ORDER BY season ASC, episode ASC, sortIndex ASC")
    suspend fun allBySeries(seriesId: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE seriesId = :seriesId AND hidden = 0 ORDER BY season ASC, episode ASC, sortIndex ASC")
    fun observeAllBySeries(seriesId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun find(id: String): MediaEntity?

    @Query("SELECT * FROM media WHERE sourceId = :sourceId")
    suspend fun bySource(sourceId: String): List<MediaEntity>

    @Upsert
    suspend fun upsertAll(items: List<MediaEntity>)

    @Upsert
    suspend fun upsert(item: MediaEntity)

    @Query("UPDATE media SET hidden = :hidden, updatedAt = :now WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE media SET kidsAllowed = :allow, updatedAt = :now WHERE id = :id")
    suspend fun setKidsAllowed(id: String, allow: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE media SET kidsAllowed = :allow, updatedAt = :now WHERE seriesId = :seriesId")
    suspend fun setKidsAllowedForSeries(seriesId: String, allow: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE media SET kidsAllowed = :allow, updatedAt = :now")
    suspend fun setAllKidsAllowed(allow: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE media SET durationMs = :duration WHERE id = :id")
    suspend fun setDuration(id: String, duration: Long)

    @Query("SELECT COUNT(*) FROM media WHERE seriesId = :seriesId AND kidsAllowed = 1 AND hidden = 0")
    suspend fun countKidsAllowed(seriesId: String): Int

    @Query("SELECT COUNT(*) FROM media WHERE seriesId = :seriesId AND hidden = 0")
    suspend fun countVisible(seriesId: String): Int

    @Query("DELETE FROM media WHERE sourceId = :sourceId AND lastSeenAt < :seenBefore")
    suspend fun deleteStale(sourceId: String, seenBefore: Long)

    @Query("SELECT COUNT(*) FROM media WHERE sourceId = :sourceId")
    suspend fun countBySource(sourceId: String): Int
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortIndex ASC, createdAt ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY sortIndex ASC, createdAt ASC")
    suspend fun getCollections(): List<CollectionEntity>

    @Query("SELECT * FROM collection_items ORDER BY sortIndex ASC")
    fun observeAllItems(): Flow<List<CollectionItemEntity>>

    @Query("SELECT * FROM collection_items WHERE collectionId = :collectionId ORDER BY sortIndex ASC")
    fun observeItems(collectionId: String): Flow<List<CollectionItemEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun find(id: String): CollectionEntity?

    @Upsert
    suspend fun upsertCollection(item: CollectionEntity)

    @Upsert
    suspend fun upsertItem(item: CollectionItemEntity)

    @Upsert
    suspend fun upsertItems(items: List<CollectionItemEntity>)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId AND seriesId = :seriesId")
    suspend fun removeItem(collectionId: String, seriesId: String)

    @Query("SELECT * FROM collection_items WHERE collectionId = :collectionId ORDER BY sortIndex ASC")
    suspend fun getItems(collectionId: String): List<CollectionItemEntity>

    @Query("SELECT * FROM collection_items WHERE seriesId = :seriesId")
    suspend fun itemsForSeries(seriesId: String): List<CollectionItemEntity>

    @Query("UPDATE collection_items SET sortIndex = :index WHERE collectionId = :collectionId AND seriesId = :seriesId")
    suspend fun setItemIndex(collectionId: String, seriesId: String, index: Int)

    @Query("SELECT MAX(sortIndex) FROM collections")
    suspend fun maxCollectionSort(): Int?

    @Query("SELECT MAX(sortIndex) FROM collection_items WHERE collectionId = :collectionId")
    suspend fun maxItemSort(collectionId: String): Int?

    @Query("UPDATE collections SET sortIndex = :index WHERE id = :id")
    suspend fun setCollectionSort(id: String, index: Int)

    @Query("UPDATE collections SET showOnHome = :show WHERE id = :id")
    suspend fun setShowOnHome(id: String, show: Boolean)

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun setName(id: String, name: String)

    @Query("UPDATE collections SET coverUrl = :url WHERE id = :id")
    suspend fun setCoverUrl(id: String, url: String?)
}

@Dao
interface ProgressDao {
    @Query(
        "SELECT p.mediaId AS mediaId, m.seriesId AS seriesId, m.title AS mediaTitle, " +
            "s.autoTitle AS seriesTitle, s.customTitle AS customTitle, s.coverRef AS coverRef, " +
            "s.coverUrl AS coverUrl, m.season AS season, m.episode AS episode, s.kind AS kind, " +
            "p.positionMs AS positionMs, p.durationMs AS durationMs, p.updatedAt AS updatedAt " +
            "FROM progress p JOIN media m ON m.id = p.mediaId JOIN series s ON s.id = m.seriesId " +
            "WHERE s.hidden = 0 AND m.hidden = 0 AND p.positionMs > 30000 AND p.durationMs > 0 " +
            "AND p.positionMs < p.durationMs * 95 / 100 " +
            "AND (:kidsOnly = 0 OR (s.kidsAllowed = 1 AND m.kidsAllowed = 1)) " +
            "ORDER BY p.updatedAt DESC LIMIT :limit"
    )
    fun observeContinue(kidsOnly: Boolean, limit: Int = 30): Flow<List<ContinueItem>>

    @Query("SELECT * FROM progress WHERE mediaId = :mediaId")
    suspend fun find(mediaId: String): ProgressEntity?

    @Query("SELECT * FROM progress WHERE seriesId = :seriesId")
    fun observeForSeries(seriesId: String): Flow<List<ProgressEntity>>

    @Upsert
    suspend fun upsert(progress: ProgressEntity)

    @Query("DELETE FROM progress WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String)

    @Query("DELETE FROM progress WHERE mediaId NOT IN (SELECT id FROM media)")
    suspend fun deleteOrphans()
}
