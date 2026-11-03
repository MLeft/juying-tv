package com.juying.tv.data.source

/**
 * 从文件名中提取剧名、季、集、年份。
 * 覆盖常见中文/日文动画与影视命名：
 *  - S01E02 / s01.e02
 *  - 第 01 集 / 第01话 / 第1期
 *  - EP01 / E01
 *  - [01] / 【01】
 *  - Frieren - 01 [1080p][CHS]
 */
object MediaNameParser {
    private val sxxeRegex = Regex("""(?i)\bS(\d{1,2})[\s._-]*E(\d{1,3})\b""")
    private val cnEpisodeRegex = Regex("""第\s*(\d{1,4})\s*[集话話期]""")
    private val epPrefixRegex = Regex("""(?i)(?:^|[\s._\-\[【(])(?:EP?|E)(\d{1,3})(?=[\s._\-\]】)]|$)""")
    private val bracketEpisodeRegex = Regex("""[\[【(]\s*(\d{1,3})\s*(?:v\d)?\s*[\]】)]""")
    private val standaloneEpisodeRegex = Regex("""(?:^|[\s._-])(\d{1,3})(?:v\d)?(?:[\s._-]|$)""")
    private val seasonCnRegex = Regex("""第\s*(\d{1,2})\s*季""")
    private val seasonEnRegex = Regex("""(?i)\bSeason\s*(\d{1,2})\b""")
    private val seasonSRegex = Regex("""(?i)\bS(\d{1,2})\b""")
    private val yearRegex = Regex("""(?<!\d)(19\d{2}|20\d{2})(?!\d)""")
    private val bracketRegex = Regex("""[\[\(【（]([^\]\)】）]*)[\]\)】）]""")
    private val bracketYearRegex = Regex("""[\[\(【（]\s*(19|20)\d{2}\s*[\]\)】）]""")
    private val separatorRegex = Regex("""[\s\-–—_]+""")

    private val resolutionNumbers = setOf(360, 480, 576, 720, 1080, 2160, 4320)
    private val codecNumbers = setOf(264, 265, 266)

    private val noiseKeywords = listOf(
        "1080p", "720p", "2160p", "480p", "4k", "8k", "hdr10", "hdr", "sdr",
        "hevc", "avc", "x265", "x264", "h.264", "h.265", "h264", "h265",
        "10bit", "8bit", "aac", "ac3", "eac3", "dts", "flac", "truehd", "atmos",
        "web-dl", "webdl", "webrip", "bluray", "blu-ray", "bdrip",
        "remux", "hdtv", "dvdrip", "mkv", "mp4", "chs", "cht", "big5",
        "简繁", "簡繁", "简中", "簡中", "繁中", "中字", "国语", "國語", "粤语", "粵語",
        "日语", "日語", "双语", "雙語", "内嵌", "內嵌", "外挂", "外掛",
        "字幕组", "字幕組", "字幕", "压制", "壓制", "汉化", "漢化", "fansub", "raws", "raw",
        "baha", "bilibili", "netflix", "amzn", "disney+", "at-x"
    )

    private val groupWords = listOf(
        "字幕组", "字幕組", "压制", "壓制", "汉化", "漢化", "fansub", "raws", "raw",
        "baha", "bilibili", "lilith", "nekomoe", "kissaten", "喵萌奶茶屋", "桜都字幕组",
        "北宇治字幕组", "lolihouse", "dmhy"
    )

    private val asciiNoiseRegex by lazy {
        val ascii = noiseKeywords.filter { keyword -> keyword.all { it.code < 128 } }
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        Regex("(?i)(?<![\\p{L}\\p{N}])($ascii)(?![\\p{L}\\p{N}])")
    }

    fun parse(fileName: String): ParsedName {
        val base = fileName.substringBeforeLast('.', fileName)
        var season = 1
        var episode = 1
        var hasMarker = false
        var explicit = false

        sxxeRegex.find(base)?.let {
            season = it.groupValues[1].toIntOrNull() ?: 1
            episode = it.groupValues[2].toIntOrNull() ?: 1
            hasMarker = true
            explicit = true
        }

        if (!explicit) {
            cnEpisodeRegex.find(base)?.let {
                episode = it.groupValues[1].toIntOrNull() ?: 1
                hasMarker = true
                explicit = true
            }
        }

        if (!explicit) {
            epPrefixRegex.find(base)?.let {
                episode = it.groupValues[1].toIntOrNull() ?: 1
                hasMarker = true
                explicit = true
            }
        }

        if (!explicit) {
            bracketEpisodeRegex.findAll(base).forEach { match ->
                if (!explicit) {
                    val value = match.groupValues[1].toIntOrNull() ?: 0
                    if (value in 1..300) {
                        episode = value
                        hasMarker = true
                        explicit = true
                    }
                }
            }
        }

        if (!explicit) {
            standaloneEpisodeRegex.findAll(base).forEach { match ->
                if (!hasMarker) {
                    val value = match.groupValues[1].toIntOrNull() ?: 0
                    if (value in 1..199 && value !in resolutionNumbers && value !in codecNumbers) {
                        episode = value
                        hasMarker = true
                    }
                }
            }
        }

        seasonCnRegex.find(base)?.let { season = it.groupValues[1].toIntOrNull() ?: season }
        if (season == 1) {
            seasonEnRegex.find(base)?.let { season = it.groupValues[1].toIntOrNull() ?: season }
        }
        if (season == 1) {
            seasonSRegex.find(base)?.let { season = it.groupValues[1].toIntOrNull() ?: season }
        }

        val year = yearRegex.find(base)?.value?.toIntOrNull()
        var title = cleanTitle(base)
        if (title.isBlank()) title = fallbackClean(base)
        if (title.isBlank()) title = base.trim()
        var groupTitle = title
        if (hasMarker && !explicit) {
            groupTitle = title
                .replace(Regex("""(?:^|[\s._-])\d{1,3}(?:v\d)?(?:[\s._-]|$)"""), " ")
                .replace(Regex("""\s{2,}"""), " ")
                .trim(' ', '-', '_', '.', '·')
            if (groupTitle.isBlank()) groupTitle = title
        }
        return ParsedName(
            title = title,
            groupTitle = groupTitle,
            season = season.coerceAtLeast(1),
            episode = episode.coerceAtLeast(1),
            hasEpisodeMarker = hasMarker,
            explicitEpisode = explicit,
            year = year
        )
    }

    fun cleanTitle(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw.substringBeforeLast('.', raw)
        text = text.replace('_', ' ').replace('.', ' ')

        text = bracketRegex.replace(text) { match ->
            val inner = match.groupValues[1].trim()
            if (shouldDropBracket(inner)) " " else " $inner "
        }

        text = sxxeRegex.replace(text, " ")
        text = cnEpisodeRegex.replace(text, " ")
        text = epPrefixRegex.replace(text, " ")
        text = seasonCnRegex.replace(text, " ")
        text = seasonEnRegex.replace(text, " ")
        text = bracketEpisodeRegex.replace(text, " ")
        text = bracketYearRegex.replace(text, " ")
        text = yearRegex.replace(text, " ")

        text = asciiNoiseRegex.replace(text, " ")
        noiseKeywords.filter { keyword -> keyword.any { it.code > 127 } }.forEach { keyword ->
            text = text.replace(keyword, " ")
        }
        text = text.replace(Regex("""[\[\(【（\]\)】）{}]"""), " ")
        text = separatorRegex.replace(text, " ")
        text = text.replace(Regex("""\s{2,}"""), " ")
        return text.trim(' ', '-', '_', '.', '·', '|', '+', '&')
    }

    fun cleanFolderName(raw: String): String? {
        val name = raw.trim().trim('/')
        if (name.isBlank() || isGenericFolder(name) || isSeasonFolder(name)) return null
        val cleaned = cleanTitle(name)
        if (cleaned.isBlank() || isGenericFolder(cleaned)) return null
        return cleaned
    }

    private fun fallbackClean(raw: String): String {
        var text = raw.substringBeforeLast('.', raw)
        text = text.replace('_', ' ').replace('.', ' ')
        text = bracketRegex.replace(text) { match ->
            val inner = match.groupValues[1].trim()
            if (dropBracket(inner, keepAsciiNames = true)) " " else " $inner "
        }
        text = sxxeRegex.replace(text, " ")
        text = cnEpisodeRegex.replace(text, " ")
        text = epPrefixRegex.replace(text, " ")
        text = seasonCnRegex.replace(text, " ")
        text = seasonEnRegex.replace(text, " ")
        text = bracketEpisodeRegex.replace(text, " ")
        text = bracketYearRegex.replace(text, " ")
        text = yearRegex.replace(text, " ")
        text = asciiNoiseRegex.replace(text, " ")
        noiseKeywords.filter { keyword -> keyword.any { it.code > 127 } }.forEach { keyword ->
            text = text.replace(keyword, " ")
        }
        text = separatorRegex.replace(text, " ")
        return text.trim(' ', '-', '_', '.', '·', '|', '+', '&')
    }

    private fun shouldDropBracket(inner: String): Boolean = dropBracket(inner, keepAsciiNames = false)

    private fun dropBracket(inner: String, keepAsciiNames: Boolean): Boolean {
        if (inner.isBlank()) return true
        val lower = inner.lowercase()
        if (inner.matches(Regex("""\d{1,4}(?:p|bit|fps)?"""))) return true
        if (lower.matches(Regex("""(?:19|20)\d{2}"""))) return true
        if (noiseKeywords.any { lower.contains(it) }) return true
        if (groupWords.any { lower.contains(it) }) return true
        // 纯 ASCII 的方括号多半是压制组/标签；中文方括号更可能是标题
        if (keepAsciiNames) return false
        val hasCjk = inner.any { it.code in 0x4E00..0x9FFF || it.code in 0x3040..0x30FF }
        if (!hasCjk && inner.matches(Regex("""[A-Za-z0-9 ._\-&+]+"""))) return true
        return false
    }
}
