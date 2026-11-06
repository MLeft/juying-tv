package com.juying.tv.data.ai

import org.json.JSONArray
import org.json.JSONObject

data class AiFile(
    val name: String,
    val folder: String
)

data class AiEpisodePlan(
    val index: Int,
    val file: String?,
    val season: Int,
    val episode: Int,
    val title: String
)

data class AiSeriesPlan(
    val seriesTitle: String?,
    val episodes: List<AiEpisodePlan>
)

data class AiPoster(
    val imageUrl: String?,
    val pageUrl: String?,
    val confidence: Double = 0.0
)

data class AiConfig(
    val apiKey: String,
    val model: String,
    val baseUrl: String = "https://dashscope.aliyuncs.com"
)

/** 大模型返回的 JSON 经常带 ```json 包裹或解释文字，这里做容错提取。 */
object AiJson {
    fun extractObject(text: String): JSONObject? {
        val cleaned = text
            .replace(Regex("^\\s*```(?:json)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*$"), "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(cleaned.substring(start, end + 1)) }.getOrNull()
    }

    fun parseSeriesPlan(text: String, fileCount: Int): AiSeriesPlan? {
        val root = extractObject(text) ?: return null
        val title = root.optString("series_title")
            .ifBlank { root.optString("title") }
            .take(80)
            .ifBlank { null }
        val array = root.optJSONArray("episodes")
            ?: root.optJSONArray("files")
            ?: root.optJSONArray("data")
            ?: return null
        val episodes = ArrayList<AiEpisodePlan>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val rawIndex = item.optInt("index", item.optInt("i", 0))
            val file = item.optString("file").ifBlank { item.optString("filename") }.ifBlank { null }
            val season = item.optInt("season", item.optInt("s", 0))
            val episode = item.optInt("episode", item.optInt("e", 0))
            val epTitle = item.optString("title")
                .replace('\n', ' ')
                .trim()
                .take(80)
            if (season !in 1..99 || episode !in 1..9999) continue
            if (rawIndex !in 1..fileCount && file == null) continue
            episodes += AiEpisodePlan(
                index = rawIndex,
                file = file,
                season = season,
                episode = episode,
                title = epTitle.ifBlank { file?.substringBeforeLast('.') ?: "第 ${episode} 集" }
            )
        }
        if (episodes.isEmpty()) return null
        return AiSeriesPlan(title, episodes)
    }

    fun parsePoster(text: String): AiPoster? {
        val root = extractObject(text) ?: return null
        val image = root.optString("image_url")
            .ifBlank { root.optString("image") }
            .ifBlank { root.optString("poster_url") }
            .ifBlank { null }
        val page = root.optString("page_url")
            .ifBlank { root.optString("source_url") }
            .ifBlank { root.optString("url") }
            .ifBlank { null }
        val confidence = root.optDouble("confidence", 0.0)
        if (image == null && page == null) return null
        return AiPoster(image, page, confidence)
    }

    fun parseSearchResultUrls(searchInfo: JSONObject?): List<String> {
        if (searchInfo == null) return emptyList()
        val array = searchInfo.optJSONArray("search_results") ?: return emptyList()
        val urls = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = item.optString("url").ifBlank { item.optString("link") }
            if (url.startsWith("http")) urls += url
        }
        return urls
    }

    fun parseContent(content: Any?): String = when (content) {
        is String -> content
        is JSONArray -> buildString {
            for (i in 0 until content.length()) {
                val part = content.optJSONObject(i) ?: continue
                append(part.optString("text"))
            }
        }
        else -> ""
    }
}
