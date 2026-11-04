package com.juying.tv.data.scrape

data class ScrapeCandidate(
    val provider: String,
    val externalId: String,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
    val score: Int = 0,
    val type: String? = null
)

object ScrapeMatcher {
    fun bestMatch(query: String, queryYear: Int?, candidates: List<ScrapeCandidate>): ScrapeCandidate? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.length < 2) return null
        return candidates.map { candidate ->
            val names = listOfNotNull(candidate.title, candidate.originalTitle)
                .map { normalize(it) }
                .filter { it.isNotBlank() }
            var score = 0
            names.forEach { name ->
                score = maxOf(score, scoreName(normalizedQuery, name))
            }
            if (queryYear != null && candidate.year != null) {
                score += when {
                    candidate.year == queryYear -> 6
                    kotlin.math.abs(candidate.year - queryYear) == 1 -> 2
                    else -> -8
                }
            }
            candidate.copy(score = score)
        }.filter { it.score >= 70 }
            .maxByOrNull { it.score }
    }

    fun normalize(value: String): String {
        var text = value.lowercase()
        text = text.replace(Regex("""(?:全|共)?\s*\d{1,4}\s*[集话話]"""), "")
        text = text.replace(Regex("""第\s*[0-9一二三四五六七八九十]+\s*[季部]"""), "")
        text = text.replace(Regex("""(?i)\b(?:season|part|s)\s*\d+\b"""), "")
        listOf("高清", "超清", "蓝光", "完整版", "国语", "中字", "双语").forEach {
            text = text.replace(it, "")
        }
        text = text.replace(Regex("""[\s\p{Punct}，。！？、；：·「」『』【】（）《》—…]+"""), "")
        return text.trim()
    }

    private fun scoreName(query: String, name: String): Int {
        if (query.isBlank() || name.isBlank()) return 0
        if (query == name) return 100
        val minLength = minOf(query.length, name.length)
        if (minLength < 4) return 0
        if (query.contains(name) || name.contains(query)) {
            val ratio = minLength.toDouble() / maxOf(query.length, name.length)
            return if (ratio >= 0.6) 82 else 60
        }
        return 0
    }
}
