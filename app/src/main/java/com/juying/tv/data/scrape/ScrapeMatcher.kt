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
    val type: String? = null,
    val isSeries: Boolean? = null
)

object ScrapeQuery {
    private val yearRegex = Regex("""(?:19|20)\d{2}""")
    private val episodeRegex = Regex("""(?:全|共)?\s*\d{1,4}\s*[集话話]""")
    private val seasonRegex = Regex("""第\s*[0-9一二三四五六七八九十百]+\s*[季部级]\s*""")
    private val englishSeasonRegex = Regex("""(?i)\b(?:season|part|s)\s*\d{1,2}(?:e\d{1,4})?\b""")
    private val bracketRegex = Regex("""[\(（\[【].*?[\)）\]】]""")
    private val punctRegex = Regex("""[\s\p{Punct}，。！？、；：·「」『』【】（）《》—…]+""")
    private val trailingNumberRegex = Regex("""\s+\d{1,3}$""")

    private val noiseTags = listOf(
        "高清", "超清", "蓝光", "4K", "1080P", "720P", "国语", "粤语",
        "中字", "双语", "中英", "英文", "中文", "儿歌", "动画", "合集",
        "全集", "完整版", "课程", "音频", "视频", "启蒙", "早教", "珍藏版"
    )

    private val levelRegex = Regex("""第\s*\d+\s*级|\d{1,2}\s*级""")
    private val lessonRegex = Regex("""第\s*\d+\s*课""")
    private val courseMarkers = listOf(
        "课程", "单元", "字母", "拼读", "词汇", "语法", "教程", "教材",
        "音频", "儿歌", "歌曲", "启蒙", "早教"
    )

    /** 清洗干扰词，保留用于检索的核心片名。 */
    fun cleaned(value: String): String {
        var text = value.trim()
        if (text.isBlank()) return text
        text = text.replace(bracketRegex, " ")
        text = text.replace(englishSeasonRegex, " ")
        text = text.replace(episodeRegex, " ")
        text = text.replace(seasonRegex, " ")
        text = text.replace(yearRegex, " ")
        noiseTags.forEach { text = text.replace(it, " ", ignoreCase = true) }
        text = text.replace(punctRegex, " ").replace(Regex("""\s+"""), " ").trim()
        text = text.replace(trailingNumberRegex, " ").trim()
        return text.replace(Regex("""\s+"""), " ").trim()
    }

    /** 最多两个检索词，避免一次刮削打太多请求。 */
    fun variants(title: String): List<String> {
        val raw = title.trim()
        val cleaned = cleaned(raw)
        val result = LinkedHashSet<String>()
        if (cleaned.length in 2..80) result += cleaned
        if (raw.length in 2..80 && raw != cleaned) result += raw
        return result.take(2)
    }

    /** 提取英文片名，供 TVmaze 检索。 */
    fun latin(title: String): String? {
        val raw = title.trim()
        val latinOnly = Regex("""^[A-Za-z0-9'&.\-:!? ]+$""")
        val candidate = if (latinOnly.matches(raw)) raw else {
            Regex("""[A-Za-z][A-Za-z0-9'&.\- ]{2,}""")
                .findAll(raw)
                .map { it.value.trim() }
                .maxByOrNull { it.length }
        } ?: return null
        var text = cleaned(candidate)
        if (text.length > 60) text = text.split(' ').take(5).joinToString(" ")
        return text.takeIf { it.length >= 2 && it.any { c -> c.isLetter() } }
    }

    fun isCourse(title: String): Boolean {
        val lower = title.lowercase()
        return courseMarkers.any { lower.contains(it) } ||
            levelRegex.containsMatchIn(lower) ||
            lessonRegex.containsMatchIn(lower)
    }

    /** 课程/儿歌类标题的“核心标记”，用于拒绝把 SS儿歌 匹配成 SS。 */
    fun courseMarker(title: String): String? {
        val lower = title.lowercase()
        return courseMarkers.firstOrNull { lower.contains(it) }
            ?: if (levelRegex.containsMatchIn(lower)) "级" else null
            ?: if (lessonRegex.containsMatchIn(lower)) "课" else null
    }
}

object ScrapeMatcher {
    fun bestMatch(
        query: String,
        queryYear: Int?,
        candidates: List<ScrapeCandidate>,
        localKind: String? = null
    ): ScrapeCandidate? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.length < 2) return null
        val course = ScrapeQuery.isCourse(query)
        val courseMarker = if (course) ScrapeQuery.courseMarker(query) else null
        return candidates.map { candidate ->
            val names = listOfNotNull(candidate.title, candidate.originalTitle)
                .map { normalize(it) }
                .filter { it.isNotBlank() }
            var score = 0
            names.forEach { name ->
                score = maxOf(score, scoreName(normalizedQuery, name, query, candidate.title))
            }
            if (queryYear != null && candidate.year != null) {
                score += when {
                    candidate.year == queryYear -> 6
                    kotlin.math.abs(candidate.year - queryYear) == 1 -> 2
                    else -> -10
                }
            }
            val localIsSeries = localKind == null || localKind == "SERIES"
            if (candidate.isSeries == true) {
                score += if (localIsSeries) 4 else -4
            } else if (candidate.isSeries == false && localIsSeries) {
                score -= 4
            }
            // “小猪佩奇”不该优先匹配到“小猪佩奇之完美假期（大电影）”。
            if (!query.contains("电影") && candidate.title.contains("大电影")) score -= 8
            if (!query.contains("之") && candidate.title.contains("之")) score -= 3
            if (courseMarker != null && !candidate.title.contains(courseMarker)) score = 0
            candidate.copy(score = score)
        }.filter { it.score >= 72 && !(course && it.score < 95) }
            .maxByOrNull { it.score }
    }

    fun normalize(value: String): String {
        var text = value.lowercase()
        text = text.replace(Regex("""(?:全|共)?\s*\d{1,4}\s*[集话話]"""), "")
        text = text.replace(Regex("""第\s*[0-9一二三四五六七八九十]+\s*[季部]"""), "")
        text = text.replace(Regex("""(?i)\b(?:season|part|s)\s*\d+\b"""), "")
        listOf("高清", "超清", "蓝光", "完整版", "国语", "中字", "双语", "英文", "动画", "儿歌", "音频", "视频").forEach {
            text = text.replace(it, "")
        }
        text = text.replace(Regex("""[\s\p{Punct}，。！？、；：·「」『』【】（）《》—…]+"""), "")
        return text.trim()
    }

    private fun scoreName(query: String, name: String, rawQuery: String, rawName: String): Int {
        if (query.isBlank() || name.isBlank()) return 0
        if (query == name) return 100
        val minLength = minOf(query.length, name.length)
        if (minLength < 2) return 0
        if (query.startsWith(name) || name.startsWith(query)) {
            val ratio = minLength.toDouble() / maxOf(query.length, name.length)
            return if (ratio >= 0.5) 86 else 64
        }
        if (query.contains(name) || name.contains(query)) {
            val ratio = minLength.toDouble() / maxOf(query.length, name.length)
            return if (ratio >= 0.6) 80 else 58
        }
        // 英文单词集合匹配：Numberblocks / Numberblocks 03
        val queryWords = rawQuery.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.length >= 2 }
        val nameWords = rawName.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.length >= 2 }
        if (queryWords.isNotEmpty() && nameWords.isNotEmpty() &&
            queryWords.all { it in nameWords } &&
            queryWords.size.toDouble() / nameWords.size >= 0.5
        ) {
            return 82
        }
        val similarity = similarity(query, name)
        return when {
            similarity >= 0.85 -> 84
            similarity >= 0.75 -> 74
            else -> 0
        }
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val distance = levenshtein(a, b)
        return 1.0 - distance.toDouble() / maxOf(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
