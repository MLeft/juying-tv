package com.juying.tv

import com.juying.tv.data.scrape.ScrapeCandidate
import com.juying.tv.data.scrape.ScrapeMatcher
import com.juying.tv.data.scrape.ScrapeQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScrapeMatcherTest {

    @Test
    fun matchesExactChineseTitle() {
        val best = ScrapeMatcher.bestMatch(
            "葬送的芙莉莲",
            2023,
            listOf(candidate("葬送的芙莉莲", "Sousou no Frieren", 2023))
        )
        assertNotNull(best)
        assertEquals("400602", best?.externalId)
    }

    @Test
    fun matchesTitleWithSeasonSuffix() {
        val best = ScrapeMatcher.bestMatch(
            "间谍过家家 第二季",
            null,
            listOf(candidate("间谍过家家", "SPY×FAMILY", 2022))
        )
        assertNotNull(best)
    }

    @Test
    fun doesNotMatchCourseFolder() {
        assertNull(
            ScrapeMatcher.bestMatch(
                "完整版 1 8级241集 英文",
                2024,
                listOf(candidate("英文", null, 2024), candidate("数学", null, 2024))
            )
        )
    }

    @Test
    fun matchesNumberblocksWithTrailingNumber() {
        val best = ScrapeMatcher.bestMatch(
            "Numberblocks 03",
            2017,
            listOf(candidate("Numberblocks", null, 2017).copy(isSeries = true)),
            localKind = "SERIES"
        )
        assertNotNull(best)
        assertEquals("Numberblocks", best?.title)
    }

    @Test
    fun prefersSeriesOverMovieSequel() {
        val movie = candidate("小猪佩奇·完美假期", null, 2026).copy(isSeries = false)
        val series = candidate("小猪佩奇 第一季", null, 2004).copy(isSeries = true)
        val best = ScrapeMatcher.bestMatch(
            "小猪佩奇",
            null,
            listOf(movie, series),
            localKind = "SERIES"
        )
        assertEquals("小猪佩奇 第一季", best?.title)
    }

    @Test
    fun rejectsCourseOrSongTitles() {
        assertNull(
            ScrapeMatcher.bestMatch(
                "SS儿歌",
                null,
                listOf(candidate("SS", null, 2020)),
                localKind = "SERIES"
            )
        )
    }

    @Test
    fun cleansSeasonQualityTagsAndTrailingNumbers() {
        assertEquals("超级飞侠", ScrapeQuery.cleaned("超级飞侠 第2季 1080P 国语"))
        assertEquals("Numberblocks", ScrapeQuery.latin("Numberblocks 03"))
    }

    @Test
    fun prefersYearRelevantCandidate() {
        val best = ScrapeMatcher.bestMatch(
            "Frieren",
            2023,
            listOf(
                candidate("Frieren", "Frieren", 2018),
                candidate("Frieren", "Frieren", 2023)
            )
        )
        assertEquals(2023, best?.year)
    }

    private fun candidate(title: String, original: String?, year: Int?): ScrapeCandidate =
        ScrapeCandidate(
            provider = "bangumi",
            externalId = if (year == 2023) "400602" else "100",
            title = title,
            originalTitle = original,
            year = year,
            overview = "简介",
            posterUrl = null
        )
}
