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

class AiClient(
    client: OkHttpClient,
    private val settings: SettingsStore
) {
    private val requestClient = client.newBuilder()
        .callTimeout(180, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val streamClient = client.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val pageClient = client.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    suspend fun currentConfig(): AiConfig {
        val providerId = settings.aiProvider.first().ifBlank { "bailian" }
        val preset = AiProviders.byId(providerId)
        val keys = settings.aiApiKeys.first()
        val key = keys[providerId].orEmpty().trim()
        val baseUrl = if (preset.customBaseUrl) {
            settings.aiBaseUrl.first().trim()
        } else {
            preset.baseUrl
        }
        if (baseUrl.isBlank()) throw IOException("请先在设置中填写 OpenAI 兼容网关地址")
        if (key.isBlank()) throw IOException("请先在设置中填写 ${preset.name} 的 API Key")
        val model = settings.aiModel.first().ifBlank { AiProviders.defaultModel(providerId) }
        if (model.isBlank()) throw IOException("请先在设置中选择模型")
        return AiConfig(
            provider = providerId,
            providerName = preset.name,
            baseUrl = baseUrl.trimEnd('/'),
            nativeBaseUrl = preset.nativeBaseUrl,
            apiKey = key,
            model = model,
            supportsSearch = preset.supportsSearch
        )
    }

    suspend fun test(): String = withContext(Dispatchers.IO) {
        val cfg = currentConfig()
        val result = chatStream(
            cfg,
            listOf(
                "system" to "你是一个简洁的助手。",
                "user" to "只回复：连接成功"
            ),
            jsonMode = false
        )
        "${cfg.providerName} · ${cfg.model} · ${result.content.take(30)}"
    }

    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val cfg = currentConfig()
        val request = Request.Builder()
            .url("${cfg.baseUrl}/models")
            .get()
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .header("Accept", "application/json")
            .header("User-Agent", "JuyingTV/1.2")
            .build()
        requestClient.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw IOException("${cfg.providerName} API Key 无效或已过期")
            }
            if (!response.isSuccessful) return@withContext emptyList()
            val root = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                ?: return@withContext emptyList()
            val array = root.optJSONArray("data") ?: return@withContext emptyList()
            val models = ArrayList<String>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isNotBlank()) models += id
            }
            models.distinct().sorted().take(200)
        }
    }

    suspend fun normalize(
        seriesTitle: String,
        year: Int?,
        files: List<AiFile>,
        remotePath: String? = null
    ): AiSeriesPlan? = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext null
        val cfg = currentConfig()
        val list = files.mapIndexed { index, file ->
            "${index + 1}. ${file.name}  |  ${file.folder.ifBlank { "/" }}"
        }.joinToString("\n")
        val prompt = buildString {
            appendLine("你是儿童动画媒体库整理助手。只输出 JSON，不要 markdown、不要解释。")
            appendLine("当前名称（可能不规范，仅供参考）：$seriesTitle")
            remotePath?.takeIf { it.isNotBlank() }?.let { appendLine("原始目录/分组路径：$it") }
            year?.let { appendLine("年份：$it") }
            appendLine("文件列表（序号. 文件名 | 所在目录）：")
            appendLine(list)
            appendLine()
            appendLine("请输出：")
            appendLine("""{"series_title":"规范后的剧集名","episodes":[{"index":1,"season":1,"episode":1,"title":"适合儿童看的单集标题"}]}""")
            appendLine("规则：")
            appendLine("1. series_title 必须是完整作品名，优先保留或恢复品牌名（如 Wow English、Super Simple Songs、Numberblocks 等）。")
            appendLine("2. 如果当前名称只包含“引进版 / 完整版 / 第x级 / 中英 / 英文 / 双语 / 视频 / 音频”等课程属性，不要直接照抄；")
            appendLine("   从目录和文件名中找作品名，输出“作品名 + 级别/语言”，例如“Wow English 引进版 1-2级 中英”。")
            appendLine("3. 不要只改标点或空格；无法确定作品名时保留原标题，不要编造。")
            appendLine("4. index 必须来自上面的序号，每个文件最多一次，不要编造文件。")
            appendLine("5. season/episode 从文件名和目录推断；课程级别当作季；无法判断时按列表顺序编号。")
            appendLine("6. title 只做文件名清洗：去掉扩展名、开头编号、清晰度、语言标签和集数后缀，保留文件名里有意义的词；不要根据剧情编造标题。")
            appendLine("   无法清洗出有意义内容时，直接输出清洗后的文件名。")
            appendLine("7. 只整理文件，不要输出不存在的内容。")
        }
        val result = chatStream(
            cfg,
            listOf(
                "system" to "你是严谨的媒体库整理助手，只输出合法 JSON。",
                "user" to prompt
            ),
            jsonMode = true
        )
        val plan = AiJson.parseSeriesPlan(result.content, files.size)
        if (plan == null) Log.w("JuyingAi", "AI 整理返回无法解析")
        plan
    }

    suspend fun findPoster(seriesTitle: String, year: Int?): AiPosterImage? =
        withContext(Dispatchers.IO) {
            val cfg = currentConfig()
            if (!cfg.supportsSearch) return@withContext null
            val prompt = buildString {
                appendLine("你是儿童动画海报检索助手。请联网搜索这部剧集的官方竖版海报/封面。")
                appendLine("剧集：$seriesTitle")
                year?.let { appendLine("年份：$it") }
                appendLine("优先官方、豆瓣、TMDB、维基或电视台的竖版海报；不要截图、拼接图、含大量文字或不良内容的图片。")
                appendLine("只输出 JSON：")
                appendLine("""{"image_url":"https://...","page_url":"https://...","confidence":0.0}""")
            }
            val result = nativeSearchChat(
                cfg,
                listOf(
                    "system" to "你是海报检索助手，只输出合法 JSON。",
                    "user" to prompt
                )
            )
            val poster = AiJson.parsePoster(result.content)
            Log.i("JuyingAi", "海报模型返回：${result.content.take(160)}；来源 ${result.searchUrls.size} 个")
            val candidates = buildList {
                poster?.imageUrl?.let { add(it to poster.pageUrl) }
                poster?.pageUrl?.let { add(null to it) }
                extractImageUrls(result.content).forEach { add(it to poster?.pageUrl) }
                result.searchUrls.take(8).forEach { add(null to it) }
            }
            if (candidates.isEmpty()) return@withContext null
            Log.i("JuyingAi", "海报候选：${candidates.size} 个")
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

    /** OpenAI 兼容流式 chat/completions；边收边拼接，避免长 JSON 被整体超时。 */
    private suspend fun chatStream(
        cfg: AiConfig,
        messages: List<Pair<String, String>>,
        jsonMode: Boolean
    ): AiChatResult = withContext(Dispatchers.IO) {
        var useJson = jsonMode
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val body = JSONObject().apply {
                    put("model", cfg.model)
                    put("stream", true)
                    put("messages", JSONArray().also { array ->
                        messages.forEach { (role, content) ->
                            array.put(JSONObject().put("role", role).put("content", content))
                        }
                    })
                    if (useJson) put("response_format", JSONObject().put("type", "json_object"))
                }
                val request = Request.Builder()
                    .url("${cfg.baseUrl}/chat/completions")
                    .post(body.toString().toRequestBody("application/json;charset=UTF-8".toMediaType()))
                    .header("Authorization", "Bearer ${cfg.apiKey}")
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream, application/json")
                    .header("User-Agent", "JuyingTV/1.2")
                    .build()
                streamClient.newCall(request).execute().use { response ->
                    val errorText = if (!response.isSuccessful) response.body?.string().orEmpty() else ""
                    if (response.code == 401 || response.code == 403) {
                        throw IOException("${cfg.providerName} API Key 无效或已过期")
                    }
                    if (response.code == 429) throw IOException("${cfg.providerName} 接口限流，请稍后重试")
                    if (response.code == 400 && useJson && errorText.contains("response_format", true)) {
                        useJson = false
                        throw RetryWithoutJson()
                    }
                    if (!response.isSuccessful) {
                        throw IOException("${cfg.providerName} 接口 HTTP ${response.code}${if (errorText.isBlank()) "" else "：${errorText.take(120)}"}")
                    }
                    val contentType = response.header("Content-Type").orEmpty()
                    val responseBody = response.body ?: throw IOException("模型没有返回内容")
                    if (!contentType.contains("event-stream", true)) {
                        val text = responseBody.string()
                        if (text.trimStart().startsWith("data:")) {
                            val content = parseSseText(text)
                            if (content.isBlank()) throw IOException("模型没有返回内容")
                            return@withContext AiChatResult(content, emptyList(), null)
                        }
                        val root = runCatching { JSONObject(text) }.getOrElse { throw IOException("模型返回内容无法解析") }
                        val content = AiJson.parseContent(
                            root.optJSONArray("choices")?.optJSONObject(0)
                                ?.optJSONObject("message")?.opt("content")
                        )
                        if (content.isBlank()) throw IOException("模型没有返回内容")
                        return@withContext AiChatResult(content, emptyList(), null)
                    }
                    val source = responseBody.source()
                    val content = StringBuilder()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload == "[DONE]") break
                        val chunk = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                        chunk.optJSONObject("error")?.let {
                            throw IOException(it.optString("message").ifBlank { "模型流式返回错误" })
                        }
                        val delta = chunk.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                        val piece = AiJson.parseContent(delta?.opt("content"))
                        if (piece.isNotEmpty()) content.append(piece)
                    }
                    if (content.isBlank()) throw IOException("模型没有返回内容")
                    return@withContext AiChatResult(content.toString(), emptyList(), null)
                }
            } catch (e: RetryWithoutJson) {
                lastError = e
            } catch (e: IOException) {
                lastError = e
                if (attempt == 0 && (e.message?.contains("限流") == true || e.message?.contains("HTTP 5") == true)) {
                    delay(2_000)
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: IOException("模型请求失败")
    }

    private fun parseSseText(text: String): String {
        val content = StringBuilder()
        text.lineSequence().forEach { line ->
            if (!line.startsWith("data:")) return@forEach
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") return@forEach
            val chunk = runCatching { JSONObject(payload) }.getOrNull() ?: return@forEach
            val delta = chunk.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
            content.append(AiJson.parseContent(delta?.opt("content")))
        }
        return content.toString()
    }

    /** 百炼原生接口，用于 enable_search 并返回搜索来源。 */
    private suspend fun nativeSearchChat(
        cfg: AiConfig,
        messages: List<Pair<String, String>>
    ): AiChatResult = withContext(Dispatchers.IO) {
        val base = cfg.nativeBaseUrl ?: "https://dashscope.aliyuncs.com"
        val body = JSONObject().apply {
            put("model", cfg.model)
            put("input", JSONObject().put("messages", JSONArray().also { array ->
                messages.forEach { (role, content) ->
                    array.put(
                        JSONObject().put("role", role)
                            .put("content", JSONArray().put(JSONObject().put("text", content)))
                    )
                }
            }))
            put("parameters", JSONObject().apply {
                put("result_format", "message")
                put("response_format", JSONObject().put("type", "json_object"))
                put("enable_search", true)
                put("search_options", JSONObject().apply {
                    put("forced_search", true)
                    put("enable_source", true)
                    put("search_strategy", "max")
                })
            })
        }
        val request = Request.Builder()
            .url("${base.trimEnd('/')}/api/v1/services/aigc/text-generation/generation")
            .post(body.toString().toRequestBody("application/json;charset=UTF-8".toMediaType()))
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .header("Content-Type", "application/json")
            .header("User-Agent", "JuyingTV/1.2")
            .build()
        requestClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                throw IOException("${cfg.providerName} API Key 无效或已过期")
            }
            if (!response.isSuccessful) throw IOException("${cfg.providerName} 海报搜索 HTTP ${response.code}")
            val root = runCatching { JSONObject(text) }.getOrElse { throw IOException("模型返回内容无法解析") }
            val output = root.optJSONObject("output")
            val content = AiJson.parseContent(
                output?.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.opt("content")
            )
            val urls = AiJson.parseSearchResultUrls(output?.optJSONObject("search_info"))
            AiChatResult(content, urls, root.optString("request_id"))
        }
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
