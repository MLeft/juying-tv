package com.juying.tv

import com.juying.tv.data.db.SeriesKind
import com.juying.tv.data.source.MediaGrouper
import com.juying.tv.data.source.MediaNameParser
import com.juying.tv.data.source.ScannedFile
import com.juying.tv.data.source.ScannedKind
import com.juying.tv.data.source.classify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNamingTest {

    @Test
    fun parsesSeasonEpisodeAndChineseTitle() {
        val parsed = MediaNameParser.parse(
            "进击的巨人 S04E28 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4].mkv"
        )
        assertEquals(4, parsed.season)
        assertEquals(28, parsed.episode)
        assertEquals("进击的巨人", parsed.groupTitle)
        assertTrue(parsed.explicitEpisode)
    }

    @Test
    fun parsesChineseEpisodeMarker() {
        val parsed = MediaNameParser.parse("葬送的芙莉莲 第12集.mp4")
        assertEquals(12, parsed.episode)
        assertEquals("葬送的芙莉莲", parsed.title)
        assertTrue(parsed.explicitEpisode)
    }

    @Test
    fun stripsReleaseTagsAndTrailingEpisodeNumber() {
        val parsed = MediaNameParser.parse("Sousou no Frieren - 01 [1080p][CHS].mkv")
        assertEquals(1, parsed.episode)
        assertEquals("Sousou no Frieren", parsed.groupTitle)
    }

    @Test
    fun keepsBracketedEnglishTitleWhenGroupBracketIsDropped() {
        val parsed = MediaNameParser.parse("[Nekomoe kissaten][Frieren][05][1080p][CHS].mp4")
        assertEquals(5, parsed.episode)
        assertEquals("Frieren", parsed.title)
    }

    @Test
    fun groupsFlatEpisodesInGenericFolder() {
        val groups = MediaGrouper.group(
            listOf(
                video("/Downloads/Frieren 01.mkv"),
                video("/Downloads/Frieren 02.mkv")
            )
        )
        assertEquals(1, groups.size)
        assertEquals("Frieren", groups.first().title)
        assertEquals(SeriesKind.SERIES, groups.first().kind)
        assertEquals(2, groups.first().files.size)
    }

    @Test
    fun mergesSeasonFoldersIntoOneSeries() {
        val groups = MediaGrouper.group(
            listOf(
                video("/Anime/Frieren/S01/Frieren.S01E01.mkv"),
                video("/Anime/Frieren/S02/Frieren.S02E01.mkv")
            )
        )
        assertEquals(1, groups.size)
        assertEquals("Frieren", groups.first().title)
        assertEquals(2, groups.first().files.size)
        assertEquals(listOf(1, 2), groups.first().files.map { it.parsed.season })
    }

    @Test
    fun keepsUnrelatedMoviesSeparate() {
        val groups = MediaGrouper.group(
            listOf(
                video("/Movies/Inception.2010.1080p.mkv"),
                video("/Movies/Interstellar.2014.mkv")
            )
        )
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.kind == SeriesKind.MOVIE })
        assertEquals(setOf("Inception", "Interstellar"), groups.map { it.title }.toSet())
    }

    @Test
    fun genericFolderWithoutTitleFallsBackToParsedName() {
        val groups = MediaGrouper.group(
            listOf(
                video("/下载/Frieren 01.mkv"),
                video("/下载/Frieren 02.mkv"),
                video("/下载/Spy x Family 01.mkv"),
                video("/下载/Spy x Family 02.mkv")
            )
        )
        assertEquals(2, groups.size)
        assertEquals(setOf("Frieren", "Spy x Family"), groups.map { it.title }.toSet())
    }

    private fun video(path: String): ScannedFile {
        val name = path.substringAfterLast('/')
        return ScannedFile(
            remoteId = path,
            path = path,
            parentPath = path.substringBeforeLast('/'),
            name = name,
            size = 1024L,
            lastModified = 0L,
            mime = "video/x-matroska",
            kind = classify(name),
            playRef = "file://$path"
        )
    }
}
