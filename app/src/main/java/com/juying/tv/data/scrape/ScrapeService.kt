package com.juying.tv.data.scrape

import android.util.Log
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.util.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScrapeService(
    context: android.content.Context,
    private val douban: DoubanClient,
    private val tvmaze: TvmazeClient,
    private val tmdb: TmdbClient
) {
    private val cache = ScrapeCache(context)

    /**
     * 多源刮削：英文片名优先 TVmaze，中文片名优先豆瓣；
     * 每个查询词最多两个变体，结果写入本地缓存。
     */
    suspend fun findMatch(
        series: SeriesEntity,
        force: Boolean = false,
        skipDouban: Boolean = false
    ): ScrapeCandidate? {
        val display = series.displayTitle.trim()
        val auto = series.autoTitle.trim()
        // 课程、儿歌、字母拼读等内容没有稳定的影视条目，直接跳过，避免浪费请求和误匹配。
        if (ScrapeQuery.isCourse(display) || ScrapeQuery.isCourse(auto)) return null
        val queries = LinkedHashSet<String>()
        (ScrapeQuery.variants(display) + ScrapeQuery.variants(auto))
            .filter { it.length >= 2 }
            .forEach { queries += it }
        val queryList = queries.take(2)
        if (queryList.isEmpty()) return null

        val latin = ScrapeQuery.latin(auto) ?: ScrapeQuery.latin(display)
        val cjkCount = display.count { it.code in 0x4E00..0x9FFF }
        val latinFirst = latin != null && cjkCount <= 2

        if (latinFirst) {
            for (query in queryList) {
                tryProvider("tmdb", query, series, force)?.let { return it }
            }
            tryProvider("tvmaze", latin!!, series, force)?.let { return it }
            if (!skipDouban) {
                for (query in queryList) {
                    tryProvider("douban", query, series, force)?.let { return it }
                }
            }
        } else {
            if (!skipDouban) {
                for (query in queryList) {
                    tryProvider("douban", query, series, force)?.let { return it }
                }
            }
            tryProvider("tmdb", queryList.first(), series, force)?.let { return it }
            if (latin != null) {
                tryProvider("tvmaze", latin, series, force)?.let { return it }
            }
        }
        return null
    }

    private suspend fun tryProvider(
        provider: String,
        query: String,
        series: SeriesEntity,
        force: Boolean
    ): ScrapeCandidate? = runCatching {
        matchProvider(provider, query, series, force)
    }.getOrElse { e ->
        if (e is ScrapeRateLimitedException) throw e
        Log.w("JuyingScrape", "$provider 查询失败: ${e.message}")
        null
    }

    private suspend fun matchProvider(
        provider: String,
        query: String,
        series: SeriesEntity,
        force: Boolean
    ): ScrapeCandidate? {
        val key = sha256("$provider|$query|${series.year ?: 0}")
        val cached = if (!force) cache.get(key) else null
        val candidates = cached ?: run {
            val loaded = when (provider) {
                "douban" -> douban.search(query)
                "tvmaze" -> tvmaze.search(query)
                "tmdb" -> tmdb.search(query)
                else -> emptyList()
            }
            cache.put(key, loaded)
            loaded
        }
        return ScrapeMatcher.bestMatch(query, series.year, candidates, series.kind)
    }

    /** 豆瓣限流时用固定片名探活；返回 false 表示接口在返回空结果。 */
    suspend fun canary(): Boolean = runCatching {
        douban.search("西游记").isNotEmpty()
    }.getOrElse { e ->
        if (e is ScrapeRateLimitedException) throw e
        false
    }

    suspend fun testSources(): List<ScrapeSourceTest> = withContext(Dispatchers.IO) {
        val results = ArrayList<ScrapeSourceTest>()
        val doubanResult = runCatching { douban.search("西游记") }
        results += doubanResult.fold(
            onSuccess = { list ->
                ScrapeSourceTest(
                    provider = "douban",
                    ok = list.isNotEmpty(),
                    count = list.size,
                    detail = if (list.isEmpty()) "返回 0 条，可能被匿名限流" else "正常，首条 ${list.first().title}"
                )
            },
            onFailure = { e ->
                ScrapeSourceTest("douban", false, 0, e.message ?: "请求失败")
            }
        )
        val tvmazeResult = runCatching { tvmaze.search("Peppa Pig") }
        results += tvmazeResult.fold(
            onSuccess = { list ->
                ScrapeSourceTest(
                    provider = "tvmaze",
                    ok = list.isNotEmpty(),
                    count = list.size,
                    detail = if (list.isEmpty()) "返回 0 条" else "正常，首条 ${list.first().title}"
                )
            },
            onFailure = { e ->
                ScrapeSourceTest("tvmaze", false, 0, e.message ?: "请求失败")
            }
        )
        val tmdbResult = runCatching { tmdb.search("Numberblocks") }
        results += tmdbResult.fold(
            onSuccess = { list ->
                val posterBytes = list.firstOrNull { !it.posterUrl.isNullOrBlank() }
                    ?.let { candidate -> runCatching { tmdb.downloadPoster(candidate.posterUrl!!) }.getOrNull() }
                android.util.Log.i(
                    "JuyingScrape",
                    "TMDB 测试：${list.size} 条，海报=${if (posterBytes != null) "可下载" else "不可直连"}"
                )
                val posterDetail = when {
                    list.none { !it.posterUrl.isNullOrBlank() } -> "无海报"
                    posterBytes != null -> "海报可下载"
                    else -> "海报不可直连（元数据仍可用）"
                }
                ScrapeSourceTest(
                    provider = "tmdb",
                    ok = list.isNotEmpty(),
                    count = list.size,
                    detail = if (list.isEmpty()) {
                        "返回 0 条，可能 Key 受限"
                    } else {
                        "正常，首条 ${list.first().title}；$posterDetail"
                    }
                )
            },
            onFailure = { e ->
                ScrapeSourceTest("tmdb", false, 0, e.message ?: "请求失败")
            }
        )
        results
    }

    suspend fun downloadPoster(candidate: ScrapeCandidate): ByteArray? {
        val url = candidate.posterUrl ?: return null
        return when (candidate.provider) {
            "douban" -> douban.downloadPoster(url)
            "tvmaze" -> tvmaze.downloadPoster(url)
            "tmdb" -> tmdb.downloadPoster(url)
            else -> null
        }
    }
}
