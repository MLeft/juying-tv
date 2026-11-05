package com.juying.tv.data.scrape

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * TMDB 主接口。中国大陆部分网络无法访问 api.themoviedb.org，
 * 但 api.tmdb.org 是可达的 CloudFront 域名，因此默认走后者。
 *
 * 海报走 image.tmdb.org 背后的 Bunny CDN 域名（国内可直连）。
 * Key 使用公开教程里广泛使用的演示 Key；可被 TMDB 撤销。
 * 失效时会自动回退豆瓣/TVmaze，也可在源码里替换为自己的 Key。
 */
class TmdbClient(client: OkHttpClient) {
    private val client = client.newBuilder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val imageClient = client.newBuilder()
        .callTimeout(6, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val gate = Mutex()
    private var nextRequestAt = 0L

    companion object {
        private const val BASE = "https://api.tmdb.org/3"
        private const val DEMO_API_KEY = "4e44d9029b1270a757cddc766a1bcb63"
        private const val USER_AGENT = "JuyingTV/1.1 (personal Android TV media center)"
    }

    suspend fun search(query: String): List<ScrapeCandidate> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        gate.withLock {
            val wait = nextRequestAt - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try {
                request(trimmed)
            } finally {
                nextRequestAt = System.currentTimeMillis() + 250L
            }
        }
    }

    private fun request(trimmed: String): List<ScrapeCandidate> {
        val url = "$BASE/search/multi".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", DEMO_API_KEY)
            .addQueryParameter("query", trimmed.take(80))
            .addQueryParameter("language", "zh-CN")
            .addQueryParameter("include_adult", "false")
            .addQueryParameter("page", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 429 || response.code >= 500) {
                throw ScrapeRateLimitedException(
                    "tmdb",
                    30_000,
                    "TMDB 接口限流（HTTP ${response.code}）"
                )
            }
            if (response.code == 401 || response.code == 403) {
                throw IOException("TMDB API Key 无效或已失效（HTTP ${response.code}）")
            }
            if (!response.isSuccessful) throw IOException("TMDB 接口 HTTP ${response.code}")
            val root = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrElse {
                throw IOException("TMDB 返回内容无法解析")
            }
            val array = root.optJSONArray("results") ?: return emptyList()
            val results = ArrayList<ScrapeCandidate>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val mediaType = item.optString("media_type")
                if (mediaType != "movie" && mediaType != "tv") continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val name = if (mediaType == "tv") item.optString("name") else item.optString("title")
                if (name.isBlank()) continue
                val original = if (mediaType == "tv") {
                    item.optString("original_name")
                } else {
                    item.optString("original_title")
                }
                val date = if (mediaType == "tv") {
                    item.optString("first_air_date")
                } else {
                    item.optString("release_date")
                }
                val year = date.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
                val posterPath = item.optString("poster_path").ifBlank { null }
                results += ScrapeCandidate(
                    provider = "tmdb",
                    externalId = "$mediaType:$id",
                    title = name,
                    originalTitle = original.ifBlank { null },
                    year = year,
                    overview = item.optString("overview").ifBlank { null },
                    posterUrl = posterPath?.let { "https://tmdb-image-prod.b-cdn.net/t/p/w500$it" },
                    type = mediaType,
                    isSeries = mediaType == "tv"
                )
            }
            return results
        }
    }

    suspend fun downloadPoster(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .build()
            imageClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                if (bytes.size < 512) null else bytes
            }
        }.getOrNull()
    }
}
