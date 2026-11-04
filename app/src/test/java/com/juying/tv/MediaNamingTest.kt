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


    @Test
    fun keepsCompleteTitleWhenFileNameUsesDotAfterEpisodeNumber() {
        val parsed = MediaNameParser.parse("001.Wow!Yellow Unit 1 hello video_story.mp4")
        assertEquals(1, parsed.episode)
        assertEquals("Wow!Yellow Unit 1 hello video story", parsed.groupTitle)
    }

    @Test
    fun groupsNumericEpisodeFilesInsideNamedSeriesFolder() {
        val groups = MediaGrouper.group(
            listOf(
                video("/旺仔/第一级 黄色 33集全/001.Wow!Yellow Unit 1 hello video_story.mp4"),
                video("/旺仔/第一级 黄色 33集全/002.Wow!Yellow Unit 2 story.mp4")
            )
        )
        assertEquals(1, groups.size)
        assertEquals(SeriesKind.SERIES, groups.first().kind)
        assertEquals(2, groups.first().files.size)
        assertEquals("第一级 黄色 33集全", groups.first().title)
    }

    @Test
    fun doesNotTruncateFolderTitleAtDots() {
        val cleaned = MediaNameParser.cleanFolderName("01.完整版（1-8级241集）无字幕，外挂srt")
        assertTrue(cleaned != null && cleaned.startsWith("01 完整版"))
        assertTrue(cleaned!!.contains("241集"))
    }


    @Test
    fun parsesEpisodeAfterBracketedShowName() {
        val first = MediaNameParser.parse("【01 初唐诗坛】01. 唐朝的诗歌概况.mp4")
        val last = MediaNameParser.parse("【01 初唐诗坛】08. 陈子昂《登幽州台歌》赏析.mp4")
        assertEquals(1, first.episode)
        assertEquals(8, last.episode)
        assertTrue(first.explicitEpisode)
    }

    @Test
    fun groupsBracketedShowEpisodesIntoSeries() {
        val groups = MediaGrouper.group(
            listOf(
                video("/旺仔/历代文学课堂/01-初唐诗坛【01-08】/【01 初唐诗坛】01. 唐朝的诗歌概况.mp4"),
                video("/旺仔/历代文学课堂/01-初唐诗坛【01-08】/【01 初唐诗坛】02. 王勃的创作风格、代表作品.mp4")
            )
        )
        assertEquals(1, groups.size)
        assertEquals(SeriesKind.SERIES, groups.first().kind)
        assertEquals(2, groups.first().files.size)
    }


    @Test
    fun parsesLeadingNumberWithoutSeparator() {
        val parsed = MediaNameParser.parse("01Numberblocks Series One.mp4")
        assertEquals(1, parsed.episode)
        assertTrue(parsed.hasEpisodeMarker)
    }

    @Test
    fun groupsNumberedNumberblocksFolder() {
        val groups = MediaGrouper.group(
            listOf(
                video("/旺仔/Numberblocks/01Numberblocks Series One.mp4"),
                video("/旺仔/Numberblocks/02Numberblocks Series One.mp4"),
                video("/旺仔/Numberblocks/03Numberblocks Series One.mp4"),
                video("/旺仔/Numberblocks/04Numberblocks Series One.mp4"),
                video("/旺仔/Numberblocks/05Numberblocks Series One.mp4")
            )
        )
        assertEquals(1, groups.size)
        assertEquals(SeriesKind.SERIES, groups.first().kind)
        assertEquals(5, groups.first().files.size)
    }


    @Test
    fun parsesChineseLessonMarker() {
        val parsed = MediaNameParser.parse("第10节：怎么识记和运用声母z c s.mp4")
        assertEquals(10, parsed.episode)
        assertTrue(parsed.explicitEpisode)
    }


    @Test
    fun groupsCourseLevelsAsSeasons() {
        val groups = MediaGrouper.group(
            listOf(
                video("/课程/完整版/01.第一级/001.mp4"),
                video("/课程/完整版/02.第二级/001.mp4"),
                video("/课程/完整版/02.第二级/002.mp4")
            )
        )
        assertEquals(1, groups.size)
        assertEquals(SeriesKind.SERIES, groups.first().kind)
        assertEquals(3, groups.first().files.size)
        assertEquals(listOf(1, 2, 2), groups.first().files.map { it.parsed.season })
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
