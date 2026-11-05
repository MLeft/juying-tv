package com.juying.tv.data.scrape

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * TVmaze 公开接口，无需 Key。对英文动画/剧集覆盖较好，
 * 适合作为豆瓣在英文片名上的补充。
 */
class TvmazeClient(client: OkHttpClient) {
    private val client = client.newBuilder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gate = Mutex()
    private var nextRequestAt = 0L

    companion object {
        private const val USER_AGENT =
            "JuyingTV/1.1 (personal Android TV media center; contact: local)"
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
                nextRequestAt = System.currentTimeMillis() + 900L
            }
        }
    }

    private fun request(trimmed: String): List<ScrapeCandidate> {
        val url = "https://api.tvmaze.com/search/shows"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", trimmed.take(80))
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
                    "tvmaze",
                    30_000,
                    "TVmaze 接口限流（HTTP ${response.code}）"
                )
            }
            if (!response.isSuccessful) throw IOException("TVmaze 接口 HTTP ${response.code}")
            val array = runCatching { JSONArray(response.body?.string().orEmpty()) }.getOrElse {
                throw IOException("TVmaze 返回内容无法解析")
            }
            val results = ArrayList<ScrapeCandidate>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val show = item.optJSONObject("show") ?: continue
                val name = show.optString("name")
                val id = show.optString("id")
                if (name.isBlank() || id.isBlank()) continue
                val premiered = show.optString("premiered")
                val year = premiered.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
                val image = show.optJSONObject("image")
                val poster = image?.optString("original")?.ifBlank { null }
                    ?: image?.optString("medium")?.ifBlank { null }
                val summary = show.optString("summary")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .trim()
                    .ifBlank { null }
                results += ScrapeCandidate(
                    provider = "tvmaze",
                    externalId = id,
                    title = name,
                    originalTitle = null,
                    year = year,
                    overview = summary,
                    posterUrl = poster,
                    type = "tv",
                    isSeries = true
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
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                if (bytes.size < 512) null else bytes
            }
        }.getOrNull()
    }
}
