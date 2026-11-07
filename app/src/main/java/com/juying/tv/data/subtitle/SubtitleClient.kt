package com.juying.tv.data.subtitle

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

data class OnlineSubtitle(
    val id: String,
    val label: String,
    val language: String,
    val format: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val season: Int,
    val episode: Int
)

/** OpenSubtitles 旧版 REST 接口，无需 API Key；按剧名 + 季/集过滤。 */
class SubtitleClient(client: OkHttpClient) {
    private val http = client.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        query: String,
        season: Int,
        episode: Int,
        exactEpisode: Boolean = true
    ): List<OnlineSubtitle> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        var url = "https://rest.opensubtitles.org/search/query-" + URLEncoder.encode(q, "UTF-8")
        if (exactEpisode && season > 0) url += "/season-$season"
        if (exactEpisode && episode > 0) url += "/episode-$episode"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JuyingTV/1.4")
            .header("Accept", "application/json")
            .build()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("字幕搜索失败（HTTP ${response.code}）")
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use emptyList()
                parse(body)
            }
        }
    }

    private fun parse(body: String): List<OnlineSubtitle> {
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val result = ArrayList<OnlineSubtitle>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val download = item.optString("SubDownloadLink")
            if (download.isBlank()) continue
            val lang = languageName(item.optString("SubLanguageID"))
            val name = item.optString("SubFileName")
            val format = item.optString("SubFormat").ifBlank { name.substringAfterLast('.', "srt") }
            result += OnlineSubtitle(
                id = item.optString("IDSubtitleFile", "$i"),
                label = "$lang · $name",
                language = item.optString("SubLanguageID"),
                format = format,
                downloadUrl = download,
                sizeBytes = item.optString("SubSize").toLongOrNull() ?: 0L,
                season = item.optString("SeriesSeason").toIntOrNull() ?: 0,
                episode = item.optString("SeriesEpisode").toIntOrNull() ?: 0
            )
        }
        return result
            .distinctBy { it.id }
            .sortedWith(compareBy({ languageRank(it.language) }, { it.label }))
            .take(20)
    }

    private fun languageRank(code: String): Int = when (code.lowercase()) {
        "chi", "zht", "chs", "zh" -> 0
        "cht" -> 1
        "eng", "en" -> 2
        else -> 3
    }

    private fun languageName(code: String): String = when (code.lowercase()) {
        "chi", "zht", "chs", "zh" -> "简体中文"
        "cht" -> "繁体中文"
        "eng", "en" -> "English"
        "jpn" -> "日本語"
        "kor" -> "한국어"
        else -> code.ifBlank { "未知语言" }
    }

    suspend fun download(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JuyingTV/1.4")
            .build()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("字幕下载失败（HTTP ${response.code}）")
                val bytes = response.body?.bytes() ?: throw IOException("字幕内容为空")
                when {
                    bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte() ->
                        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                    bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() ->
                        unzipFirst(bytes)
                    else -> bytes
                }
            }
        }
    }

    private fun unzipFirst(bytes: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (name.endsWith(".srt") || name.endsWith(".ass") || name.endsWith(".ssa") || name.endsWith(".vtt")) {
                    val out = ByteArrayOutputStream()
                    zip.copyTo(out)
                    return out.toByteArray()
                }
                entry = zip.nextEntry
            }
        }
        throw IOException("压缩包中没有字幕文件")
    }
}
