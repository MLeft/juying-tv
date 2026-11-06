package com.juying.tv.data.ai

import android.graphics.BitmapFactory
import android.util.Log
import com.juying.tv.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

data class AiChatResult(
    val content: String,
    val searchUrls: List<String>,
    val requestId: String?
)

data class AiPosterImage(
    val bytes: ByteArray,
    val sourceUrl: String
)

class BailianClient(
    client: OkHttpClient,
    private val settings: SettingsStore
) {
    private val client = client.newBuilder()
        .callTimeout(90, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val pageClient = client.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BASE_URL = "https://dashscope.aliyuncs.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        private const val MAX_FILES = 220
    }

    private suspend fun config(): AiConfig {
        val key = settings.aiApiKey.first().trim()
        if (key.isBlank()) throw IOException("请先在设置中填写阿里云百炼 API Key")
        val model = settings.aiModel.first().ifBlank { "qwen-plus" }
        return AiConfig(key, model, BASE_URL)
    }

    suspend fun test(): String = withContext(Dispatchers.IO) {
        val cfg = config()
        val result = chat(
            cfg,
            listOf(
                "system" to "你是一个简洁的助手。",
                "user" to "只回复：连接成功"
            ),
            search = false,
            jsonMode = false
        )
        "${cfg.model} · ${result.content.take(30)}"
    }

    suspend fun normalize(
        seriesTitle: String,
        year: Int?,
        files: List<AiFile>
    ): AiSeriesPlan? = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext null
        val cfg = config()
        val limited = files.take(MAX_FILES)
        val list = limited.mapIndexed { index, file ->
            "${index + 1}. ${file.name}  |  ${file.folder.ifBlank { "/" }}"
        }.joinToString("\n")
        val prompt = buildString {
            appendLine("你是儿童动画媒体库整理助手。只输出 JSON，不要 markdown、不要解释。")
            appendLine("剧集名称：$seriesTitle")
            year?.let { appendLine("年份：$it") }
            appendLine("文件列表（序号. 文件名 | 所在目录）：")
            appendLine(list)
            appendLine()
            appendLine("请输出：")
            appendLine("""{"series_title":"规范后的剧集名","episodes":[{"index":1,"season":1,"episode":1,"title":"适合儿童看的单集标题"}]}""")
            appendLine("规则：")
            appendLine("1. index 必须来自上面的序号，每个文件最多一次，不要编造文件。")
            appendLine("2. season/episode 从文件名和目录推断；课程级别当作季；无法判断时按列表顺序编号。")
            appendLine("3. title 只做文件名清洗：去掉扩展名、开头编号、清晰度、语言标签和集数后缀，保留文件名里有意义的词；不要根据剧情编造标题。")
            appendLine("   无法清洗出有意义内容时，直接输出清洗后的文件名。")
            appendLine("4. 只整理文件，不要输出不存在的内容。")
        }
        val result = chat(
            cfg,
            listOf(
                "system" to "你是严谨的媒体库整理助手，只输出合法 JSON。",
                "user" to prompt
            ),
            search = false,
            jsonMode = true
        )
        val plan = AiJson.parseSeriesPlan(result.content, limited.size)
        if (plan == null) {
            Log.w("JuyingAi", "AI 整理返回无法解析")
        }
        plan
    }

    suspend fun findPoster(seriesTitle: String, year: Int?): AiPosterImage? =
        withContext(Dispatchers.IO) {
            val cfg = config()
            val prompt = buildString {
                appendLine("你是儿童动画海报检索助手。请联网搜索这部剧集的官方竖版海报/封面。")
                appendLine("剧集：$seriesTitle")
                year?.let { appendLine("年份：$it") }
                appendLine("优先官方、豆瓣、TMDB、维基或电视台的竖版海报；不要截图、拼接图、含大量文字或不良内容的图片。")
                appendLine("只输出 JSON：")
                appendLine("""{"image_url":"https://...","page_url":"https://...","confidence":0.0}""")
            }
            val result = chat(
                cfg,
                listOf(
                    "system" to "你是海报检索助手，只输出合法 JSON。",
                    "user" to prompt
                ),
                search = true,
                jsonMode = true
            )
            val poster = AiJson.parsePoster(result.content)
            android.util.Log.i(
                "JuyingAi",
                "海报模型返回：${result.content.take(160)}；来源 ${result.searchUrls.size} 个"
            )
            val candidates = buildList {
                poster?.imageUrl?.let { add(it to poster.pageUrl) }
                poster?.pageUrl?.let { add(null to it) }
                extractImageUrls(result.content).forEach { add(it to poster?.pageUrl) }
                result.searchUrls.take(8).forEach { add(null to it) }
            }
            if (candidates.isEmpty()) return@withContext null

            android.util.Log.i("JuyingAi", "海报候选：${candidates.size} 个")
            for ((directImage, pageUrl) in candidates) {
                if (directImage != null) {
                    downloadImage(directImage, pageUrl)?.let { bytes ->
                        return@withContext AiPosterImage(bytes, directImage)
                    }
                }
                if (pageUrl != null) {
                    val imageUrl = fetchOgImage(pageUrl) ?: continue
                    downloadImage(imageUrl, pageUrl)?.let { bytes ->
                        return@withContext AiPosterImage(bytes, imageUrl)
                    }
                }
            }
            null
        }

    private suspend fun chat(
        cfg: AiConfig,
        messages: List<Pair<String, String>>,
        search: Boolean,
        jsonMode: Boolean
    ): AiChatResult = withContext(Dispatchers.IO) {
        var useJsonMode = jsonMode
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val body = JSONObject().apply {
                    put("model", cfg.model)
                    put("input", JSONObject().put("messages", JSONArray().also { array ->
                        messages.forEach { (role, content) ->
                            array.put(
                                JSONObject()
                                    .put("role", role)
                                    .put(
                                        "content",
                                        JSONArray().put(JSONObject().put("text", content))
                                    )
                            )
                        }
                    }))
                    put("parameters", JSONObject().apply {
                        put("result_format", "message")
                        if (useJsonMode) put("response_format", JSONObject().put("type", "json_object"))
                        if (search) {
                            put("enable_search", true)
                            put("search_options", JSONObject().apply {
                                put("forced_search", true)
                                put("enable_source", true)
                                put("search_strategy", "max")
                            })
                        }
                    })
                }
                val request = Request.Builder()
                    .url("${cfg.baseUrl.trimEnd('/')}/api/v1/services/aigc/text-generation/generation")
                    .post(body.toString().toRequestBody("application/json;charset=UTF-8".toMediaType()))
                    .header("Authorization", "Bearer ${cfg.apiKey}")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "JuyingTV/1.2")
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.code == 401 || response.code == 403) {
                        throw IOException("百炼 API Key 无效或已过期")
                    }
                    if (response.code == 400 && useJsonMode && text.contains("response_format", true)) {
                        useJsonMode = false
                        throw RetryWithoutJson()
                    }
                    if (response.code == 429) {
                        throw IOException("百炼接口限流，请稍后重试")
                    }
                    if (!response.isSuccessful) {
                        val msg = runCatching { JSONObject(text).optString("message") }.getOrNull()
                        throw IOException("百炼接口 HTTP ${response.code}${if (msg.isNullOrBlank()) "" else "：$msg"}")
                    }
                    val root = runCatching { JSONObject(text) }.getOrElse {
                        throw IOException("百炼返回内容无法解析")
                    }
                    val output = root.optJSONObject("output")
                    val choice = output?.optJSONArray("choices")?.optJSONObject(0)
                    val content = AiJson.parseContent(choice?.optJSONObject("message")?.opt("content"))
                    if (content.isBlank()) throw IOException("百炼没有返回内容")
                    val searchUrls = AiJson.parseSearchResultUrls(output.optJSONObject("search_info"))
                    return@withContext AiChatResult(content, searchUrls, root.optString("request_id"))
                }
            } catch (e: RetryWithoutJson) {
                lastError = e
            } catch (e: IOException) {
                lastError = e
                if (attempt < 2 && (e.message?.contains("限流") == true || e.message?.contains("HTTP 5") == true)) {
                    delay(if (attempt == 0) 2_000 else 5_000)
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: IOException("百炼请求失败")
    }

    private class RetryWithoutJson : Exception()

    private fun fetchOgImage(pageUrl: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(pageUrl)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            pageClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val html = response.body?.string().orEmpty().take(600_000)
                val patterns = listOf(
                    Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)""", RegexOption.IGNORE_CASE),
                    Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE),
                    Regex("""<meta[^>]+name=["']twitter:image["'][^>]+content=["']([^"']+)""", RegexOption.IGNORE_CASE),
                    Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                )
                val raw = patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }
                    ?: return@use null
                val decoded = raw.replace("&amp;", "&")
                runCatching { URI(pageUrl).resolve(decoded).toString() }.getOrDefault(decoded)
            }
        }.getOrNull()
    }

    private fun downloadImage(url: String, referer: String?): ByteArray? {
        val normalized = normalizeImageUrl(url) ?: return null
        return runCatching {
            val request = Request.Builder()
                .url(normalized)
                .get()
                .header("User-Agent", USER_AGENT)
                .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
                .build()
            pageClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                if (bytes.size !in 5_000..8_000_000) return@use null
                if (!isImage(bytes)) return@use null
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth < 200 || options.outHeight < 200) null else bytes
            }
        }.getOrNull()
    }

    private fun extractImageUrls(content: String): List<String> =
        Regex("""https?://[^\s"'<>]+?\.(?:jpg|jpeg|png|webp)(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
            .findAll(content)
            .map { it.value }
            .take(6)
            .toList()

    private fun normalizeImageUrl(url: String): String? {
        var value = url.trim().replace("&amp;", "&")
        if (value.startsWith("//")) value = "https:$value"
        if (!value.startsWith("http")) return null
        // TMDB 官方图片域名在当前网络不可直连，改走可访问的 Bunny CDN。
        value = value.replace("https://image.tmdb.org/", "https://tmdb-image-prod.b-cdn.net/")
        return value
    }

    private fun isImage(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val png = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
        val webp = bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte()
        return jpeg || png || webp
    }
}
