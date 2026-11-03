package com.juying.tv.data.source

import androidx.room.withTransaction
import com.juying.tv.data.db.AppDatabase
import com.juying.tv.data.db.MediaEntity
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.data.db.SourceEntity
import com.juying.tv.util.stableId

class LibraryImporter(private val database: AppDatabase) {

    suspend fun import(source: SourceEntity, files: List<ScannedFile>, scanStartedAt: Long): Int {
        val groups = MediaGrouper.group(files)
        val seriesDao = database.seriesDao()
        val mediaDao = database.mediaDao()
        val progressDao = database.progressDao()

        val existingSeries = seriesDao.bySource(source.id).associateBy { it.remotePath }
        val existingMedia = mediaDao.bySource(source.id).associateBy { it.remoteId }
        val subtitlesByFolder = files.filter { it.kind == ScannedKind.SUBTITLE }
            .groupBy { it.parentPath }
        val now = System.currentTimeMillis()

        val seriesList = ArrayList<SeriesEntity>(groups.size)
        val mediaList = ArrayList<MediaEntity>(files.size)

        groups.forEach { group ->
            val seriesId = stableId("series", source.id, group.key)
            val oldSeries = existingSeries[group.key]
            val series = SeriesEntity(
                id = seriesId,
                sourceId = source.id,
                remotePath = group.key,
                autoTitle = group.title,
                customTitle = oldSeries?.customTitle,
                kind = group.kind,
                year = group.year ?: oldSeries?.year,
                overview = oldSeries?.overview,
                coverRef = group.coverRef ?: oldSeries?.coverRef,
                coverUrl = oldSeries?.coverUrl,
                hidden = oldSeries?.hidden ?: false,
                kidsAllowed = oldSeries?.kidsAllowed ?: false,
                lastSeenAt = scanStartedAt,
                createdAt = oldSeries?.createdAt ?: now,
                updatedAt = now
            )
            seriesList += series

            group.files.forEachIndexed { index, grouped ->
                val file = grouped.file
                val parsed = grouped.parsed
                val mediaId = stableId("media", source.id, file.remoteId)
                val old = existingMedia[file.remoteId]
                val subtitles = findSubtitles(file, parsed, subtitlesByFolder[file.parentPath].orEmpty())
                mediaList += MediaEntity(
                    id = mediaId,
                    seriesId = seriesId,
                    sourceId = source.id,
                    remoteId = file.remoteId,
                    parentPath = file.parentPath,
                    fileName = file.name,
                    title = (if (group.kind == com.juying.tv.data.db.SeriesKind.SERIES) parsed.groupTitle else parsed.title)
                        .ifBlank { file.name.substringBeforeLast('.') },
                    season = parsed.season,
                    episode = parsed.episode,
                    sortIndex = index,
                    durationMs = old?.durationMs ?: 0L,
                    sizeBytes = file.size,
                    mime = file.mime,
                    playRef = file.playRef,
                    subtitleRefs = SubtitleRefs.encode(subtitles),
                    hidden = old?.hidden ?: false,
                    kidsAllowed = old?.kidsAllowed ?: series.kidsAllowed,
                    lastSeenAt = scanStartedAt,
                    updatedAt = now
                )
            }
        }

        database.withTransaction {
            if (seriesList.isNotEmpty()) seriesDao.upsertAll(seriesList)
            if (mediaList.isNotEmpty()) mediaDao.upsertAll(mediaList)
            mediaDao.deleteStale(source.id, scanStartedAt)
            seriesDao.deleteEmpty(source.id)
            progressDao.deleteOrphans()
        }
        return mediaList.size
    }

    private fun findSubtitles(
        video: ScannedFile,
        videoParsed: ParsedName,
        subtitles: List<ScannedFile>
    ): List<SubtitleRef> {
        if (subtitles.isEmpty()) return emptyList()
        val videoBase = video.name.substringBeforeLast('.').lowercase()
        val matches = subtitles.filter { sub ->
            val subBase = sub.name.substringBeforeLast('.').lowercase()
            val subParsed = MediaNameParser.parse(sub.name)
            when {
                subBase == videoBase -> true
                videoBase.startsWith(subBase) || subBase.startsWith(videoBase) -> true
                subParsed.explicitEpisode &&
                    subParsed.episode == videoParsed.episode &&
                    (subParsed.season == videoParsed.season || subParsed.season == 1) -> true
                subtitles.size == 1 && !videoParsed.hasEpisodeMarker -> true
                else -> false
            }
        }
        return matches
            .distinctBy { it.playRef }
            .map { sub ->
                SubtitleRef(
                    ref = sub.playRef,
                    label = subtitleLabel(sub.name),
                    mime = mimeForExtension(extensionOf(sub.name))
                )
            }
            .sortedBy { subtitleRank(it.label) }
    }

    private fun subtitleLabel(fileName: String): String {
        val name = fileName.lowercase()
        return when {
            "chs" in name || "简" in name || "sc" in name -> "简体中文"
            "cht" in name || "繁" in name || "tc" in name -> "繁体中文"
            "eng" in name || ".en." in name -> "English"
            "jpn" in name || "日" in name -> "日本語"
            else -> fileName.substringBeforeLast('.')
        }
    }

    private fun subtitleRank(label: String): Int = when (label) {
        "简体中文" -> 0
        "繁体中文" -> 1
        else -> 2
    }
}
