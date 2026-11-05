package com.juying.tv.data.scrape

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 刮削结果本地缓存，避免重复请求触发豆瓣限流。
 * 正常结果缓存 7 天，空结果缓存 12 小时。
 */
class ScrapeCache(context: Context) {

    private val dir = File(context.filesDir, "scrape_cache")
    private val gate = Mutex()

    private companion object {
        const val POSITIVE_TTL = 7L * 24 * 60 * 60 * 1000
        const val NEGATIVE_TTL = 12L * 60 * 60 * 1000
    }

    suspend fun get(key: String): List<ScrapeCandidate>? = withContext(Dispatchers.IO) {
        gate.withLock {
            val file = File(dir, "$key.json")
            if (!file.exists()) return@withLock null
            val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: run {
                file.delete()
                return@withLock null
            }
            val ts = root.optLong("ts", 0L)
            val age = System.currentTimeMillis() - ts
            val array = root.optJSONArray("items") ?: JSONArray()
            val ttl = if (array.length() == 0) NEGATIVE_TTL else POSITIVE_TTL
            if (age !in 0..ttl) {
                file.delete()
                return@withLock null
            }
            val result = ArrayList<ScrapeCandidate>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                result += ScrapeCandidate(
                    provider = item.optString("provider"),
                    externalId = item.optString("externalId"),
                    title = item.optString("title"),
                    originalTitle = item.optString("originalTitle").ifBlank { null },
                    year = item.optInt("year", 0).takeIf { it > 0 },
                    overview = item.optString("overview").ifBlank { null },
                    posterUrl = item.optString("posterUrl").ifBlank { null },
                    type = item.optString("type").ifBlank { null },
                    isSeries = if (item.has("isSeries")) item.optBoolean("isSeries") else null
                )
            }
            result
        }
    }

    suspend fun put(key: String, candidates: List<ScrapeCandidate>) = withContext(Dispatchers.IO) {
        gate.withLock {
            dir.mkdirs()
            val array = JSONArray()
            candidates.forEach { candidate ->
                array.put(
                    JSONObject().apply {
                        put("provider", candidate.provider)
                        put("externalId", candidate.externalId)
                        put("title", candidate.title)
                        put("originalTitle", candidate.originalTitle ?: "")
                        put("year", candidate.year ?: 0)
                        put("overview", candidate.overview ?: "")
                        put("posterUrl", candidate.posterUrl ?: "")
                        put("type", candidate.type ?: "")
                        candidate.isSeries?.let { put("isSeries", it) }
                    }
                )
            }
            val temp = File(dir, "$key.tmp")
            temp.writeText(
                JSONObject()
                    .put("ts", System.currentTimeMillis())
                    .put("items", array)
                    .toString()
            )
            val target = File(dir, "$key.json")
            if (target.exists()) target.delete()
            temp.renameTo(target)
        }
    }
}
