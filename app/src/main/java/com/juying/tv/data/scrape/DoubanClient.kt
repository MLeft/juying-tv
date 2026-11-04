package com.juying.tv.data.scrape

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 豆瓣影视/剧集联想搜索。公开 JSON 接口，无需 API Key。
 * 图片有防盗链，需要带 Referer 下载后本地缓存。
 */
class DoubanClient(client: OkHttpClient) {
    private val client = client.newBuilder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private val bid: String = buildString {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        repeat(11) { append(chars.random()) }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        private const val REFERER = "https://movie.douban.com/"
    }

    suspend fun search(query: String): List<ScrapeCandidate> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        val url = "https://movie.douban.com/j/subject_suggest"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", trimmed.take(80))
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Cookie", "bid=$bid")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("豆瓣接口 HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            val array = runCatching { JSONArray(text) }.getOrElse {
                throw IOException("豆瓣返回内容无法解析，可能触发风控")
            }
            val results = ArrayList<ScrapeCandidate>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val title = item.optString("title")
                if (title.isBlank() || id.isBlank()) continue
                val year = item.optString("year").takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
                val image = item.optString("img").ifBlank { null }
                    ?.replace("http://", "https://")
                results += ScrapeCandidate(
                    provider = "douban",
                    externalId = id,
                    title = title,
                    originalTitle = item.optString("sub_title").ifBlank { null },
                    year = year,
                    overview = null,
                    posterUrl = image,
                    type = item.optString("type").ifBlank { null }
                )
            }
            results
        }
    }

    suspend fun downloadPoster(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Cookie", "bid=$bid")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                if (bytes.size < 512) null else bytes
            }
        }.getOrNull()
    }
}
