package com.juying.tv.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.juying.tv.data.db.AppDatabase
import com.juying.tv.data.db.CollectionEntity
import com.juying.tv.data.db.CollectionItemEntity
import com.juying.tv.data.db.ContinueItem
import com.juying.tv.data.db.MediaEntity
import com.juying.tv.data.db.ProgressEntity
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.data.db.SourceEntity
import com.juying.tv.data.db.SourceType
import com.juying.tv.data.source.LibraryImporter
import com.juying.tv.data.source.LocalConfig
import com.juying.tv.data.source.MediaStoreConfig
import com.juying.tv.data.source.QuarkConfig
import com.juying.tv.data.source.ScanResult
import com.juying.tv.data.source.SourceConfigs
import com.juying.tv.data.source.SourceScanner
import com.juying.tv.data.source.WebDavConfig
import com.juying.tv.util.stableId
import kotlinx.coroutines.flow.Flow
import java.io.IOException

class LibraryRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val scanner: SourceScanner
) {
    private val sourceDao = database.sourceDao()
    private val seriesDao = database.seriesDao()
    private val mediaDao = database.mediaDao()
    private val collectionDao = database.collectionDao()
    private val progressDao = database.progressDao()
    private val importer = LibraryImporter(database)

    val sources: Flow<List<SourceEntity>> = sourceDao.observeAll()
    val visibleSeries: Flow<List<SeriesEntity>> = seriesDao.observeVisible()
    val kidsSeries: Flow<List<SeriesEntity>> = seriesDao.observeKidsVisible()
    val hiddenSeries: Flow<List<SeriesEntity>> = seriesDao.observeHidden()
    val collections: Flow<List<CollectionEntity>> = collectionDao.observeCollections()
    val collectionItems: Flow<List<CollectionItemEntity>> = collectionDao.observeAllItems()

    fun continueWatching(kidsOnly: Boolean): Flow<List<ContinueItem>> =
        progressDao.observeContinue(kidsOnly)

    fun mediaOfSeries(seriesId: String, kidsOnly: Boolean): Flow<List<MediaEntity>> =
        mediaDao.observeBySeries(seriesId, kidsOnly)

    fun allMediaOfSeries(seriesId: String): Flow<List<MediaEntity>> =
        mediaDao.observeAllBySeries(seriesId)

    fun seriesById(seriesId: String): Flow<SeriesEntity?> = seriesDao.observeById(seriesId)

    fun progressOfSeries(seriesId: String): Flow<List<ProgressEntity>> =
        progressDao.observeForSeries(seriesId)

    fun collectionItemsOf(collectionId: String): Flow<List<CollectionItemEntity>> =
        collectionDao.observeItems(collectionId)

    suspend fun getSeries(seriesId: String): SeriesEntity? = seriesDao.find(seriesId)
    suspend fun getMedia(mediaId: String): MediaEntity? = mediaDao.find(mediaId)
    suspend fun getSource(sourceId: String): SourceEntity? = sourceDao.find(sourceId)
    suspend fun getEpisodes(seriesId: String, kidsOnly: Boolean): List<MediaEntity> =
        mediaDao.getBySeries(seriesId, kidsOnly)
    suspend fun getAllEpisodes(seriesId: String): List<MediaEntity> = mediaDao.allBySeries(seriesId)
    suspend fun getProgress(mediaId: String): ProgressEntity? = progressDao.find(mediaId)

    suspend fun addLocalSource(treeUri: Uri): SourceEntity {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val config = SourceConfigs.encodeLocal(LocalConfig(treeUri.toString()))
        val existing = sourceDao.findByConfig(SourceType.LOCAL, config)
        if (existing != null) return existing
        val name = runCatching {
            DocumentFile.fromTreeUri(context, treeUri)?.name
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "本地目录"
        val source = SourceEntity(
            id = stableId("source", SourceType.LOCAL, treeUri.toString()),
            type = SourceType.LOCAL,
            name = name,
            config = config,
            createdAt = System.currentTimeMillis()
        )
        sourceDao.upsert(source)
        return source
    }

    suspend fun addMediaStoreSource(minDurationSec: Int = 60): SourceEntity {
        val config = SourceConfigs.encodeMediaStore(MediaStoreConfig(minDurationSec))
        val existing = sourceDao.findByConfig(SourceType.MEDIASTORE, config)
        if (existing != null) return existing
        val source = SourceEntity(
            id = stableId("source", SourceType.MEDIASTORE, config),
            type = SourceType.MEDIASTORE,
            name = "本机与 U 盘",
            config = config,
            createdAt = System.currentTimeMillis()
        )
        sourceDao.upsert(source)
        return source
    }

    suspend fun addWebDavSource(name: String, config: WebDavConfig): SourceEntity {
        val raw = SourceConfigs.encodeWebDav(config)
        val existing = sourceDao.findByConfig(SourceType.WEBDAV, raw)
        val source = SourceEntity(
            id = existing?.id ?: stableId("source", SourceType.WEBDAV, raw),
            type = SourceType.WEBDAV,
            name = name.ifBlank { "WebDAV" },
            config = raw,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        sourceDao.upsert(source)
        return source
    }

    suspend fun addQuarkSource(name: String, config: QuarkConfig): SourceEntity {
        val raw = SourceConfigs.encodeQuark(config)
        val existing = sourceDao.findByConfig(SourceType.QUARK, raw)
        val source = SourceEntity(
            id = existing?.id ?: stableId("source", SourceType.QUARK, raw),
            type = SourceType.QUARK,
            name = name.ifBlank { "夸克网盘" },
            config = raw,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        sourceDao.upsert(source)
        return source
    }

    suspend fun updateSource(source: SourceEntity) = sourceDao.upsert(source)

    suspend fun setSourceEnabled(id: String, enabled: Boolean) = sourceDao.setEnabled(id, enabled)

    suspend fun deleteSource(id: String) {
        database.withTransaction {
            seriesDao.deleteBySource(id)
            sourceDao.delete(id)
            progressDao.deleteOrphans()
        }
    }

    suspend fun scanSource(source: SourceEntity, onProgress: (Int, String) -> Unit): ScanResult {
        val started = System.currentTimeMillis()
        return try {
            val result = scanner.scan(source, onProgress)
            val imported = importer.import(source, result.files, started)
            sourceDao.upsert(
                source.copy(
                    lastScanAt = System.currentTimeMillis(),
                    lastScanCount = imported,
                    lastError = null
                )
            )
            result
        } catch (e: Exception) {
            val message = e.message ?: "扫描失败"
            sourceDao.upsert(
                source.copy(
                    lastScanAt = System.currentTimeMillis(),
                    lastError = message.take(300)
                )
            )
            throw e
        }
    }

    suspend fun renameSeries(seriesId: String, title: String) {
        seriesDao.setCustomTitle(seriesId, title.trim().ifBlank { null })
    }

    suspend fun setSeriesCover(seriesId: String, url: String?) {
        seriesDao.setCoverUrl(seriesId, url?.trim()?.ifBlank { null })
    }

    suspend fun setSeriesHidden(seriesId: String, hidden: Boolean) {
        seriesDao.setHidden(seriesId, hidden)
    }

    suspend fun setSeriesKidsAllowed(seriesId: String, allow: Boolean) {
        database.withTransaction {
            seriesDao.setKidsAllowed(seriesId, allow)
            mediaDao.setKidsAllowedForSeries(seriesId, allow)
        }
    }

    suspend fun setEpisodeKidsAllowed(mediaId: String, allow: Boolean) {
        database.withTransaction {
            mediaDao.setKidsAllowed(mediaId, allow)
            val media = mediaDao.find(mediaId) ?: return@withTransaction
            val allowed = mediaDao.countKidsAllowed(media.seriesId)
            seriesDao.setKidsAllowed(media.seriesId, allowed > 0)
        }
    }

    suspend fun setAllKidsAllowed(allow: Boolean) {
        database.withTransaction {
            seriesDao.setAllKidsAllowed(allow)
            mediaDao.setAllKidsAllowed(allow)
        }
    }

    suspend fun createCollection(name: String): CollectionEntity {
        val index = (collectionDao.maxCollectionSort() ?: -1) + 1
        val collection = CollectionEntity(
            id = stableId("collection", System.currentTimeMillis().toString(), name),
            name = name.ifBlank { "新合集" },
            sortIndex = index
        )
        collectionDao.upsertCollection(collection)
        return collection
    }

    suspend fun renameCollection(id: String, name: String) {
        collectionDao.setName(id, name.ifBlank { "未命名合集" })
    }

    suspend fun setCollectionCover(id: String, url: String?) {
        collectionDao.setCoverUrl(id, url?.trim()?.ifBlank { null })
    }

    suspend fun setCollectionShowOnHome(id: String, show: Boolean) =
        collectionDao.setShowOnHome(id, show)

    suspend fun deleteCollection(id: String) = collectionDao.deleteCollection(id)

    suspend fun addSeriesToCollection(collectionId: String, seriesId: String) {
        val items = collectionDao.getItems(collectionId)
        if (items.any { it.seriesId == seriesId }) return
        val index = (collectionDao.maxItemSort(collectionId) ?: -1) + 1
        collectionDao.upsertItem(CollectionItemEntity(collectionId, seriesId, index))
    }

    suspend fun removeSeriesFromCollection(collectionId: String, seriesId: String) =
        collectionDao.removeItem(collectionId, seriesId)

    suspend fun moveCollectionItem(collectionId: String, seriesId: String, delta: Int) {
        val items = collectionDao.getItems(collectionId)
        val index = items.indexOfFirst { it.seriesId == seriesId }
        if (index < 0) return
        val target = index + delta
        if (target !in items.indices) return
        database.withTransaction {
            val a = items[index]
            val b = items[target]
            collectionDao.setItemIndex(collectionId, a.seriesId, b.sortIndex)
            collectionDao.setItemIndex(collectionId, b.seriesId, a.sortIndex)
        }
    }

    suspend fun moveCollection(collectionId: String, delta: Int) {
        val all = collectionDao.getCollections()
        val index = all.indexOfFirst { it.id == collectionId }
        if (index < 0) return
        val target = index + delta
        if (target !in all.indices) return
        database.withTransaction {
            val a = all[index]
            val b = all[target]
            collectionDao.setCollectionSort(a.id, b.sortIndex)
            collectionDao.setCollectionSort(b.id, a.sortIndex)
        }
    }

    suspend fun getCollections(): List<CollectionEntity> = collectionDao.getCollections()

    suspend fun getCollectionItems(collectionId: String): List<CollectionItemEntity> =
        collectionDao.getItems(collectionId)

    suspend fun getCollectionsForSeries(seriesId: String): List<String> =
        collectionDao.itemsForSeries(seriesId).map { it.collectionId }

    suspend fun saveProgress(mediaId: String, seriesId: String, positionMs: Long, durationMs: Long) {
        if (positionMs <= 0 || durationMs <= 0) return
        if (positionMs >= durationMs * 95 / 100) {
            progressDao.delete(mediaId)
            return
        }
        progressDao.upsert(
            ProgressEntity(
                mediaId = mediaId,
                seriesId = seriesId,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearProgress(mediaId: String) = progressDao.delete(mediaId)

    suspend fun restoreSeriesHidden(seriesId: String) = seriesDao.setHidden(seriesId, false)

    suspend fun sourceCounts(id: String): Int = mediaDao.countBySource(id)
}
