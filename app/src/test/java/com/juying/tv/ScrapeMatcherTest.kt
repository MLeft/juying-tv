package com.juying.tv

import com.juying.tv.data.scrape.ScrapeCandidate
import com.juying.tv.data.scrape.ScrapeMatcher
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
