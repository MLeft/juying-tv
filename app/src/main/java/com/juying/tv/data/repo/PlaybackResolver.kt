package com.juying.tv.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.room.withTransaction
import com.juying.tv.data.db.AppDatabase
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.data.db.SourceType
import com.juying.tv.data.source.QuarkConfig
import com.juying.tv.data.source.SourceConfigs
import com.juying.tv.data.source.SubtitleRef
import com.juying.tv.data.source.SubtitleRefs
import com.juying.tv.data.source.WebDavConfig
import com.juying.tv.data.source.quark.QuarkClient
import com.juying.tv.data.source.webdav.WebDavClient
import com.juying.tv.util.SubtitleEncoding
import com.juying.tv.util.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class SubtitleSpec(
    val uri: Uri,
    val label: String,
    val mime: String,
    val language: String?
)

data class ResolvedEpisode(
    val mediaId: String,
    val title: String,
    val url: String,
    val mime: String?,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleSpec>,
    val startPositionMs: Long,
    val durationMs: Long
)

data class PlaybackBundle(
    val series: SeriesEntity,
    val episodes: List<com.juying.tv.data.db.MediaEntity>,
    val index: Int,
    val resolved: ResolvedEpisode
)

class PlaybackResolver(
    private val context: Context,
    private val database: AppDatabase,
    private val webDav: WebDavClient,
    private val quark: QuarkClient
) {

    private val thumbnailGate = Semaphore(2)
    private val thumbMemory = LruCache<String, File>(256)
    private val seriesThumbMemory = LruCache<String, File>(128)
    private val thumbFailedAt = ConcurrentHashMap<String, Long>()
    private val seriesThumbFailedAt = ConcurrentHashMap<String, Long>()

    private fun recentlyFailed(map: ConcurrentHashMap<String, Long>, key: String): Boolean {
        val at = map[key] ?: return false
        if (System.currentTimeMillis() - at < 30 * 60 * 1000L) return true
        map.remove(key)
        return false
    }

    suspend fun prepare(
        mediaId: String,
        kidsOnly: Boolean,
        fromStart: Boolean = false,
        forceRefresh: Boolean = false
    ): PlaybackBundle {
        val media = database.mediaDao().find(mediaId) ?: throw IOException("媒体条目已不存在，请重新扫描")
        val series = database.seriesDao().find(media.seriesId)
            ?: throw IOException("剧集信息已丢失，请重新扫描")
        val episodes = database.mediaDao().getBySeries(series.id, kidsOnly)
        val index = episodes.indexOfFirst { it.id == mediaId }
        if (index < 0) throw IOException("该内容未在儿童模式允许列表中")
        val resolved = resolve(mediaId, ignoreProgress = fromStart, forceRefresh = forceRefresh)
        return PlaybackBundle(series, episodes, index, resolved)
    }

    suspend fun resolve(
        mediaId: String,
        ignoreProgress: Boolean = false,
        forceRefresh: Boolean = false
    ): ResolvedEpisode {
        val media = database.mediaDao().find(mediaId) ?: throw IOException("媒体条目已不存在")
        val source = database.sourceDao().find(media.sourceId)
            ?: throw IOException("媒体源已被删除")
        val config = SourceConfigs.decode(source.type, source.config)
        val progress = database.progressDao().find(mediaId)
        val start = if (ignoreProgress) {
            0L
        } else {
            progress?.let {
                if (it.positionMs > 30_000 && it.durationMs > 0 && it.positionMs < it.durationMs * 95 / 100) {
                    it.positionMs
                } else 0L
            } ?: 0L
        }

        var headers: Map<String, String> = emptyMap()
        val url: String
        when (source.type) {
            SourceType.WEBDAV -> {
                val dav = config as WebDavConfig
                url = media.playRef
                headers = webDav.playbackHeaders(dav)
            }
            SourceType.QUARK -> {
                val quarkConfig = config as QuarkConfig
                url = quark.getDownloadUrl(quarkConfig, media.playRef, forceRefresh)
                headers = quark.playbackHeaders(quarkConfig)
            }
            else -> {
                url = media.playRef
            }
        }

        val subtitles = resolveSubtitles(media.subtitleRefs, source.type, config)
        return ResolvedEpisode(
            mediaId = mediaId,
            title = media.title,
            url = url,
            mime = media.mime,
            headers = headers,
            subtitles = subtitles,
            startPositionMs = start,
            durationMs = progress?.durationMs ?: media.durationMs
        )
    }

    /** 单集缩略图：夸克走网盘缩略图接口并缓存，本地文件抽取第 3 秒画面。 */
    suspend fun episodeThumbnail(mediaId: String): File? = withContext(Dispatchers.IO) {
        thumbMemory.get(mediaId)?.takeIf { it.exists() && it.length() > 0 }?.let { return@withContext it }
        if (recentlyFailed(thumbFailedAt, mediaId)) return@withContext null

        val media = database.mediaDao().find(mediaId) ?: return@withContext null
        val source = database.sourceDao().find(media.sourceId) ?: return@withContext null
        val dir = File(context.filesDir, "episode_thumbs").apply { mkdirs() }
        val cache = File(dir, "${sha256(media.id).take(32)}.img")
        if (cache.exists() && cache.length() > 0) {
            thumbMemory.put(mediaId, cache)
            return@withContext cache
        }
        if (source.type == SourceType.WEBDAV) {
            thumbFailedAt[mediaId] = System.currentTimeMillis()
            return@withContext null
        }

        val file = thumbnailGate.withPermit {
            thumbMemory.get(mediaId)?.takeIf { it.exists() && it.length() > 0 } ?: when (source.type) {
                SourceType.QUARK -> {
                    val config = SourceConfigs.decode(source.type, source.config) as QuarkConfig
                    val bytes = quark.fetchThumbnail(config, media.remoteId)
                    if (bytes != null) {
                        cache.writeBytes(bytes)
                        cache
                    } else {
                        null
                    }
                }
                else -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, Uri.parse(media.playRef))
                        val bitmap = retriever.getScaledFrameAtTime(
                            3_000_000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            480,
                            270
                        )
                        if (bitmap != null) {
                            try {
                                java.io.FileOutputStream(cache).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                                }
                                cache
                            } finally {
                                bitmap.recycle()
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    } finally {
                        runCatching { retriever.release() }
                    }
                }
            }
        }
        if (file != null) {
            thumbMemory.put(mediaId, file)
            thumbFailedAt.remove(mediaId)
        } else {
            thumbFailedAt[mediaId] = System.currentTimeMillis()
        }
        file
    }

    /** 剧集封面兜底：取第一集（按季/集/排序）的缩略图。 */
    suspend fun seriesThumbnail(seriesId: String): File? {
        seriesThumbMemory.get(seriesId)?.takeIf { it.exists() && it.length() > 0 }?.let { return it }
        if (recentlyFailed(seriesThumbFailedAt, seriesId)) return null
        val first = database.mediaDao().firstVisibleBySeries(seriesId) ?: return null
        val file = episodeThumbnail(first.id)
        if (file != null) {
            seriesThumbMemory.put(seriesId, file)
        } else {
            seriesThumbFailedAt[seriesId] = System.currentTimeMillis()
        }
        return file
    }

    /** 清理已不再被任何剧集引用的 Agent 导入封面。 */
    suspend fun pruneCoverCache() = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "covers")
        val referenced = database.seriesDao().getAll()
            .flatMap { listOfNotNull(it.coverUrl, it.coverRef) }
            .toSet()
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith("agent-") && Uri.fromFile(file).toString() !in referenced) {
                runCatching { file.delete() }
            }
        }
    }

    /** 拖动/快进时抽取目标时间附近的画面，失败时回退到首集缩略图。 */
    suspend fun seekPreview(mediaId: String, positionMs: Long): File? = withContext(Dispatchers.IO) {
        val bucket = (positionMs / 5_000).coerceAtLeast(0)
        val dir = File(context.filesDir, "seek_thumbs").apply { mkdirs() }
        val cache = File(dir, "${sha256("$mediaId:$bucket").take(32)}.jpg")
        if (cache.exists() && cache.length() > 0) return@withContext cache
        val resolved = runCatching { resolve(mediaId, ignoreProgress = true) }.getOrNull()
            ?: return@withContext episodeThumbnail(mediaId)
        val retriever = MediaMetadataRetriever()
        try {
            if (resolved.headers.isEmpty()) {
                retriever.setDataSource(context, Uri.parse(resolved.url))
            } else {
                retriever.setDataSource(resolved.url, resolved.headers)
            }
            val bitmap = retriever.getScaledFrameAtTime(
                positionMs * 1_000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                480,
                270
            ) ?: return@withContext episodeThumbnail(mediaId)
            try {
                java.io.FileOutputStream(cache).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                cache
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            runCatching { episodeThumbnail(mediaId) }.getOrNull()
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** 控制首集/选集缩略图缓存占用，按最久未使用淘汰。 */
    suspend fun pruneThumbCache(maxBytes: Long = 200L * 1024 * 1024) = withContext(Dispatchers.IO) {
        pruneDir(File(context.filesDir, "episode_thumbs"), maxBytes)
        pruneDir(File(context.filesDir, "seek_thumbs"), 60L * 1024 * 1024)
    }

    private fun pruneDir(dir: File, maxBytes: Long) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total > maxBytes) {
                val size = file.length()
                if (file.delete()) total -= size
            }
        }
    }

    suspend fun updateDuration(mediaId: String, durationMs: Long) {
        if (durationMs <= 0) return
        database.mediaDao().setDuration(mediaId, durationMs)
    }

    private suspend fun resolveSubtitles(
        raw: String,
        sourceType: String,
        config: Any
    ): List<SubtitleSpec> {
        val refs = SubtitleRefs.decode(raw)
        if (refs.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            refs.mapNotNull { subtitle ->
                runCatching {
                    when (sourceType) {
                        SourceType.WEBDAV -> {
                            val dav = config as WebDavConfig
                            val ext = extensionForMime(subtitle.mime)
                            val file = cacheSubtitle(subtitle.ref, ext) {
                                webDav.fetchBytes(subtitle.ref, dav)
                            }
                            SubtitleSpec(Uri.fromFile(file), subtitle.label, subtitle.mime, languageOf(subtitle))
                        }
                        SourceType.QUARK -> {
                            val quarkConfig = config as QuarkConfig
                            val direct = quark.getDownloadUrl(quarkConfig, subtitle.ref)
                            val ext = extensionForMime(subtitle.mime)
                            val file = cacheSubtitle(subtitle.ref, ext) {
                                quark.fetchBytes(direct, quarkConfig)
                            }
                            SubtitleSpec(Uri.fromFile(file), subtitle.label, subtitle.mime, languageOf(subtitle))
                        }
                        else -> {
                            val uri = localSubtitleUri(Uri.parse(subtitle.ref))
                            SubtitleSpec(uri, subtitle.label, subtitle.mime, languageOf(subtitle))
                        }
                    }
                }.getOrNull()
            }
        }
    }

    private suspend fun cacheSubtitle(key: String, extension: String, downloader: suspend () -> ByteArray): File {
        val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
        // utf8 后缀用于区分旧版本缓存的原始编码字幕。
        val file = File(dir, "${sha256("$key|utf8").take(24)}.$extension")
        if (file.exists() && file.length() > 0) return file
        val bytes = SubtitleEncoding.toUtf8(downloader())
        if (bytes.isEmpty()) throw IOException("字幕内容为空")
        file.writeBytes(bytes)
        return file
    }

    private suspend fun localSubtitleUri(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return@withContext uri
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty() || bytes.size > 4 * 1024 * 1024) return@withContext uri
            val converted = SubtitleEncoding.toUtf8(bytes)
            if (converted.contentEquals(bytes)) {
                uri
            } else {
                val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
                val ext = uri.path?.substringAfterLast('.', "")?.lowercase()
                    ?.takeIf { it in setOf("srt", "ass", "ssa", "vtt") } ?: "srt"
                val file = File(dir, "local-${sha256(uri.toString()).take(24)}.$ext")
                file.writeBytes(converted)
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            uri
        }
    }

    private fun extensionForMime(mime: String): String = when {
        mime.contains("ssa", true) -> "ass"
        mime.contains("vtt", true) -> "vtt"
        else -> "srt"
    }

    private fun languageOf(subtitle: SubtitleRef): String? = when {
        subtitle.label.contains("简体") || subtitle.label.contains("繁体") -> "zh"
        subtitle.label.contains("English") -> "en"
        subtitle.label.contains("日本語") -> "ja"
        else -> null
    }
}
