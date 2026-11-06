package com.juying.tv.mcp

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.juying.tv.data.AppGraph
import com.juying.tv.data.db.MediaEntity
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.data.scrape.ScrapeRateLimitedException
import com.juying.tv.util.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * MCP 工具集：让外部 Agent 读写媒体库、触发扫描/刮削/AI 整理。
 * 不提供删除文件、删除媒体源、修改 Cookie/API Key 等高危操作。
 */
class McpTools(
    private val context: Context,
    private val graph: AppGraph,
    private val tasks: McpTaskRegistry
) {
    private val db get() = graph.database
    private val repo get() = graph.repository

    private companion object {
        const val MAX_COVER_BYTES = 12 * 1024 * 1024
    }

    fun toolsJson(): JSONArray = JSONArray().apply {
        put(tool(
            "library_stats",
            "获取媒体库统计：内容数、剧集/电影数、视频数、媒体源数、合集数。",
            emptyMap(), emptyList()
        ))
        put(tool(
            "library_list_series",
            "列出媒体库内容。query 可按标题模糊过滤，include_hidden=true 包含隐藏内容。",
            mapOf(
                "query" to prop("string", "标题关键词，可选"),
                "limit" to prop("integer", "返回数量，默认 50，最大 200"),
                "include_hidden" to prop("boolean", "是否包含隐藏内容，默认 false")
            ), emptyList()
        ))
        put(tool(
            "library_get_series",
            "获取一部剧集/电影的详情和全部单集。",
            mapOf("series_id" to prop("string", "剧集 ID")), listOf("series_id")
        ))
        put(tool(
            "library_list_episodes",
            "列出某部剧集的单集，可按 season 过滤。",
            mapOf(
                "series_id" to prop("string", "剧集 ID"),
                "season" to prop("integer", "季号，可选")
            ), listOf("series_id")
        ))
        put(tool(
            "library_list_collections",
            "列出所有自定义合集及其中的内容。",
            emptyMap(), emptyList()
        ))
        put(tool(
            "library_list_sources",
            "列出媒体源及其最近扫描状态。",
            emptyMap(), emptyList()
        ))
        put(tool(
            "series_update",
            "修改剧集信息：标题、封面 URL、儿童模式允许、是否隐藏。只影响媒体库，不动原文件。",
            mapOf(
                "series_id" to prop("string", "剧集 ID"),
                "title" to prop("string", "新标题，可选"),
                "cover_url" to prop("string", "封面 URL/本地 URI，传空字符串可清除，可选"),
                "kids_allowed" to prop("boolean", "儿童模式是否允许，可选"),
                "hidden" to prop("boolean", "是否从媒体库隐藏，可选")
            ), listOf("series_id")
        ))
        put(tool(
            "series_import_cover",
            "从图片 URL 下载并缓存为剧集封面，返回新的封面地址。",
            mapOf(
                "series_id" to prop("string", "剧集 ID"),
                "image_url" to prop("string", "图片 URL")
            ), listOf("series_id", "image_url")
        ))
        put(tool(
            "series_upload_cover",
            "上传 base64 图片（支持 data URI）并设为剧集封面，供外部 Agent 找图后写入。",
            mapOf(
                "series_id" to prop("string", "剧集 ID"),
                "image_base64" to prop("string", "图片 base64 或 data:image/...;base64,...")
            ), listOf("series_id", "image_base64")
        ))
        put(tool(
            "media_update",
            "修改单个视频的标题、季、集、排序。",
            mapOf(
                "media_id" to prop("string", "视频 ID"),
                "title" to prop("string", "标题，可选"),
                "season" to prop("integer", "季号，可选"),
                "episode" to prop("integer", "集号，可选"),
                "sort_index" to prop("integer", "排序值，可选")
            ), listOf("media_id")
        ))
        put(tool(
            "collection_create",
            "创建自定义合集，返回合集 ID。",
            mapOf("name" to prop("string", "合集名称")), listOf("name")
        ))
        put(tool(
            "collection_rename",
            "重命名合集。",
            mapOf(
                "collection_id" to prop("string", "合集 ID"),
                "name" to prop("string", "新名称")
            ), listOf("collection_id", "name")
        ))
        put(tool(
            "collection_add_series",
            "把内容加入合集。",
            mapOf(
                "collection_id" to prop("string", "合集 ID"),
                "series_id" to prop("string", "剧集 ID")
            ), listOf("collection_id", "series_id")
        ))
        put(tool(
            "collection_remove_series",
            "从合集移除内容。",
            mapOf(
                "collection_id" to prop("string", "合集 ID"),
                "series_id" to prop("string", "剧集 ID")
            ), listOf("collection_id", "series_id")
        ))
        put(tool(
            "collection_delete",
            "删除合集（不会删除原文件）。",
            mapOf("collection_id" to prop("string", "合集 ID")), listOf("collection_id")
        ))
        put(tool(
            "scan_start",
            "启动媒体扫描。source_id 省略时扫描全部启用的媒体源。返回 task_id。",
            mapOf("source_id" to prop("string", "媒体源 ID，可选")), emptyList()
        ))
        put(tool(
            "scrape_start",
            "启动刮削。series_id 省略时刮削全部内容；force=true 忽略已匹配记录。返回 task_id。",
            mapOf(
                "series_id" to prop("string", "剧集 ID，可选"),
                "force" to prop("boolean", "是否重新刮削，默认 false")
            ), emptyList()
        ))
        put(tool(
            "ai_organize_series",
            "用 AI 整理一部剧集的季/集/标题，并在缺封面时找图。返回 task_id。",
            mapOf("series_id" to prop("string", "剧集 ID")), listOf("series_id")
        ))
        put(tool(
            "ai_organize_library",
            "用 AI 整理全部可见剧集。find_covers 控制是否补封面。返回 task_id。",
            mapOf("find_covers" to prop("boolean", "是否找封面，默认 true")), emptyList()
        ))
        put(tool(
            "ai_fill_covers",
            "用 AI 为缺封面的内容联网找封面。返回 task_id。",
            emptyMap(), emptyList()
        ))
        put(tool(
            "ai_status",
            "查看当前 AI 供应商、模型、Key 是否配置和最近任务。",
            emptyMap(), emptyList()
        ))
        put(tool(
            "ai_list_models",
            "读取当前供应商的在线模型列表。",
            emptyMap(), emptyList()
        ))
        put(tool(
            "ai_set_model",
            "切换当前 AI 供应商使用的模型（不涉及 API Key）。",
            mapOf("model" to prop("string", "模型 ID")), listOf("model")
        ))
        put(tool(
            "ai_set_provider",
            "切换 AI 供应商，例如 bailian / openai / deepseek / siliconflow / openrouter / moonshot / zhipu / custom。",
            mapOf("provider" to prop("string", "供应商 ID")), listOf("provider")
        ))
        put(tool(
            "ai_set_base_url",
            "设置自定义 OpenAI 兼容网关地址（选 custom 供应商时使用）。",
            mapOf("base_url" to prop("string", "例如 http://host:4000/v1")), listOf("base_url")
        ))
        put(tool(
            "task_status",
            "查询异步任务状态与结果。",
            mapOf("task_id" to prop("string", "任务 ID")), listOf("task_id")
        ))
        put(tool(
            "task_list",
            "列出最近的任务。",
            emptyMap(), emptyList()
        ))
        put(tool(
            "task_cancel",
            "取消正在运行的任务。",
            mapOf("task_id" to prop("string", "任务 ID")), listOf("task_id")
        ))
    }

    suspend fun call(name: String, args: JSONObject): JSONObject = when (name) {
        "library_stats" -> libraryStats()
        "library_list_series" -> listSeries(args)
        "library_get_series" -> getSeries(args)
        "library_list_episodes" -> listEpisodes(args)
        "library_list_collections" -> listCollections()
        "library_list_sources" -> listSources()
        "series_update" -> updateSeries(args)
        "series_import_cover" -> importCover(args)
        "series_upload_cover" -> uploadCover(args)
        "media_update" -> updateMedia(args)
        "collection_create" -> createCollection(args)
        "collection_rename" -> renameCollection(args)
        "collection_add_series" -> addToCollection(args)
        "collection_remove_series" -> removeFromCollection(args)
        "collection_delete" -> deleteCollection(args)
        "scan_start" -> startScan(args)
        "scrape_start" -> startScrape(args)
        "ai_organize_series" -> startAiSeries(args)
        "ai_organize_library" -> startAiLibrary(args)
        "ai_fill_covers" -> startFillCovers()
        "ai_status" -> aiStatus()
        "ai_list_models" -> aiModels()
        "ai_set_model" -> setAiModel(args)
        "ai_set_provider" -> setAiProvider(args)
        "ai_set_base_url" -> setAiBaseUrl(args)
        "task_status" -> taskStatus(args)
        "task_list" -> taskList()
        "task_cancel" -> taskCancel(args)
        else -> throw IllegalArgumentException("未知工具：$name")
    }

    private suspend fun libraryStats(): JSONObject {
        val series = db.seriesDao().getAll()
        val sources = db.sourceDao().getAll()
        val collections = db.collectionDao().getCollections()
        var mediaCount = 0
        series.forEach { mediaCount += db.mediaDao().countAll(it.id) }
        return JSONObject()
            .put("series", series.count { it.kind == "SERIES" })
            .put("movies", series.count { it.kind == "MOVIE" })
            .put("series_total", series.size)
            .put("videos", mediaCount)
            .put("hidden", series.count { it.hidden })
            .put("kids_allowed", series.count { it.kidsAllowed })
            .put("sources", sources.size)
            .put("collections", collections.size)
    }

    private suspend fun listSeries(args: JSONObject): JSONObject {
        val query = args.optString("query").trim()
        val includeHidden = args.optBoolean("include_hidden", false)
        val limit = args.optInt("limit", 50).coerceIn(1, 200)
        val all = db.seriesDao().getAll()
            .asSequence()
            .filter { includeHidden || !it.hidden }
            .filter { query.isBlank() || it.displayTitle.contains(query, ignoreCase = true) }
            .sortedBy { it.displayTitle.lowercase() }
            .take(limit)
            .toList()
        val array = JSONArray()
        all.forEach { series ->
            array.put(seriesJson(series, db.mediaDao().countAll(series.id)))
        }
        return JSONObject().put("count", all.size).put("items", array)
    }

    private suspend fun getSeries(args: JSONObject): JSONObject {
        val id = args.require("series_id")
        val series = db.seriesDao().find(id) ?: throw IllegalArgumentException("剧集不存在：$id")
        val media = db.mediaDao().allBySeries(id)
        val episodes = JSONArray()
        media.forEach { item -> episodes.put(mediaJson(item)) }
        val collections = db.collectionDao().itemsForSeries(id)
            .mapNotNull { db.collectionDao().find(it.collectionId)?.name }
        return JSONObject()
            .put("series", seriesJson(series, media.size))
            .put("collections", JSONArray(collections))
            .put("episodes", episodes)
    }

    private suspend fun listEpisodes(args: JSONObject): JSONObject {
        val id = args.require("series_id")
        val season = args.optInt("season", 0)
        val media = db.mediaDao().allBySeries(id)
            .filter { season <= 0 || it.season == season }
        val array = JSONArray()
        media.forEach { array.put(mediaJson(it)) }
        return JSONObject().put("count", media.size).put("items", array)
    }

    private suspend fun listCollections(): JSONObject {
        val array = JSONArray()
        db.collectionDao().getCollections().forEach { collection ->
            val items = db.collectionDao().getItems(collection.id)
            array.put(
                JSONObject()
                    .put("id", collection.id)
                    .put("name", collection.name)
                    .put("item_count", items.size)
                    .put("show_on_home", collection.showOnHome)
                    .put("cover_url", collection.coverUrl ?: "")
            )
        }
        return JSONObject().put("count", array.length()).put("items", array)
    }

    private suspend fun listSources(): JSONObject {
        val array = JSONArray()
        db.sourceDao().getAll().forEach { source ->
            array.put(
                JSONObject()
                    .put("id", source.id)
                    .put("name", source.name)
                    .put("type", source.type)
                    .put("enabled", source.enabled)
                    .put("last_scan_count", source.lastScanCount)
                    .put("last_error", source.lastError ?: "")
            )
        }
        return JSONObject().put("count", array.length()).put("items", array)
    }

    private suspend fun updateSeries(args: JSONObject): JSONObject {
        val id = args.require("series_id")
        val series = db.seriesDao().find(id) ?: throw IllegalArgumentException("剧集不存在：$id")
        if (args.has("title")) repo.renameSeries(id, args.optString("title"))
        if (args.has("cover_url")) repo.setSeriesCover(id, args.optString("cover_url"))
        if (args.has("kids_allowed")) repo.setSeriesKidsAllowed(id, args.optBoolean("kids_allowed"))
        if (args.has("hidden")) repo.setSeriesHidden(id, args.optBoolean("hidden"))
        val updated = db.seriesDao().find(id) ?: series
        return seriesJson(updated, db.mediaDao().countAll(id))
    }

    private suspend fun importCover(args: JSONObject): JSONObject {
        val id = args.require("series_id")
        val imageUrl = args.require("image_url")
        val series = db.seriesDao().find(id) ?: throw IllegalArgumentException("剧集不存在：$id")
        val bytes = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .header("Accept", "image/*")
                .build()
            runCatching {
                graph.okHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    if (body.contentLength() > MAX_COVER_BYTES) return@use null
                    body.bytes().takeIf { it.size in 1024..MAX_COVER_BYTES }
                }
            }.getOrNull()
        } ?: throw IllegalArgumentException("图片下载失败或文件过大")
        return saveCover(series, bytes)
    }

    private suspend fun uploadCover(args: JSONObject): JSONObject {
        val id = args.require("series_id")
        val series = db.seriesDao().find(id) ?: throw IllegalArgumentException("剧集不存在：$id")
        val raw = args.require("image_base64").substringAfter("base64,", args.require("image_base64"))
        val bytes = runCatching { Base64.decode(raw, Base64.DEFAULT) }.getOrNull()
            ?: throw IllegalArgumentException("图片 base64 解码失败")
        return saveCover(series, bytes)
    }

    private suspend fun saveCover(series: SeriesEntity, bytes: ByteArray): JSONObject {
        if (bytes.size !in 1024..MAX_COVER_BYTES) throw IllegalArgumentException("图片大小不合法")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth < 240 || bounds.outHeight < 240) throw IllegalArgumentException("图片尺寸过小")
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "agent-${sha256(series.id + bytes.size + System.nanoTime()).take(32)}.jpg")
        file.writeBytes(bytes)
        repo.setSeriesCover(series.id, Uri.fromFile(file).toString())
        return seriesJson(series.copy(coverUrl = Uri.fromFile(file).toString()), db.mediaDao().countAll(series.id))
    }

    private suspend fun updateMedia(args: JSONObject): JSONObject {
        val id = args.require("media_id")
        val media = db.mediaDao().find(id) ?: throw IllegalArgumentException("视频不存在：$id")
        val updated = media.copy(
            title = if (args.has("title")) args.optString("title").ifBlank { media.title } else media.title,
            season = if (args.has("season")) args.optInt("season", media.season) else media.season,
            episode = if (args.has("episode")) args.optInt("episode", media.episode) else media.episode,
            sortIndex = if (args.has("sort_index")) args.optInt("sort_index", media.sortIndex) else media.sortIndex,
            updatedAt = System.currentTimeMillis()
        )
        db.mediaDao().upsertAll(listOf(updated))
        return mediaJson(updated)
    }

    private suspend fun createCollection(args: JSONObject): JSONObject {
        val name = args.require("name")
        val collection = repo.createCollection(name)
        return JSONObject().put("id", collection.id).put("name", collection.name)
    }

    private suspend fun renameCollection(args: JSONObject): JSONObject {
        val id = args.require("collection_id")
        repo.renameCollection(id, args.require("name"))
        return JSONObject().put("ok", true).put("collection_id", id)
    }

    private suspend fun addToCollection(args: JSONObject): JSONObject {
        repo.addSeriesToCollection(args.require("collection_id"), args.require("series_id"))
        return JSONObject().put("ok", true)
    }

    private suspend fun removeFromCollection(args: JSONObject): JSONObject {
        repo.removeSeriesFromCollection(args.require("collection_id"), args.require("series_id"))
        return JSONObject().put("ok", true)
    }

    private suspend fun deleteCollection(args: JSONObject): JSONObject {
        repo.deleteCollection(args.require("collection_id"))
        return JSONObject().put("ok", true)
    }

    private suspend fun startScan(args: JSONObject): JSONObject {
        val sourceId = args.optString("source_id").ifBlank { null }
        val sources = db.sourceDao().getAll()
            .filter { it.enabled }
            .filter { sourceId == null || it.id == sourceId }
        if (sources.isEmpty()) throw IllegalArgumentException("没有可扫描的媒体源")
        val task = tasks.start("scan", sources.size) { report ->
            var totalFiles = 0
            sources.forEachIndexed { index, source ->
                report.report("扫描 ${source.name} (${index + 1}/${sources.size})…", index, sources.size)
                val result = repo.scanSource(source) { count, folder ->
                    report.report("${source.name} · $folder", index, sources.size)
                }
                totalFiles += result.files.size
            }
            JSONObject().put("sources", sources.size).put("files", totalFiles).toString()
        }
        return taskJson(task)
    }

    private suspend fun startScrape(args: JSONObject): JSONObject {
        val seriesId = args.optString("series_id").ifBlank { null }
        val force = args.optBoolean("force", false)
        val targets = if (seriesId != null) {
            listOf(db.seriesDao().find(seriesId) ?: throw IllegalArgumentException("剧集不存在：$seriesId"))
        } else {
            db.seriesDao().getAll().filter { !it.hidden }
        }
        val existing = if (!force && seriesId == null) {
            db.scrapeMetaDao().observeAll().first().map { it.seriesId }.toSet()
        } else {
            emptySet()
        }
        val queue = targets.filter { it.id !in existing }
        val task = tasks.start("scrape", queue.size) { report ->
            var matched = 0
            queue.forEachIndexed { index, series ->
                report.report("刮削 ${series.displayTitle} (${index + 1}/${queue.size})", index, queue.size)
                val best = try {
                    graph.scrape.findMatch(series, force)
                } catch (e: ScrapeRateLimitedException) {
                    report.report("豆瓣限流，等待 ${e.retryAfterMs / 1000} 秒…", index, queue.size)
                    delay(e.retryAfterMs)
                    graph.scrape.findMatch(series, force)
                }
                if (best != null) {
                    val posterRef = if (series.coverUrl.isNullOrBlank() && !best.posterUrl.isNullOrBlank()) {
                        cacheScrapePoster(series, best)
                    } else {
                        best.posterUrl
                    }
                    repo.applyScrape(series, best.copy(posterUrl = posterRef ?: best.posterUrl))
                    matched++
                }
                delay(250)
            }
            JSONObject().put("matched", matched).put("total", queue.size).toString()
        }
        return taskJson(task)
    }

    private suspend fun startAiSeries(args: JSONObject): JSONObject {
        val id = args.require("series_id")
        val series = db.seriesDao().find(id) ?: throw IllegalArgumentException("剧集不存在：$id")
        val task = tasks.start("ai_organize_series", 1) { report ->
            val result = graph.aiAgent.organizeSeries(series) { stage -> report.report(stage) }
            JSONObject()
                .put("episodes", result.episodes)
                .put("cover", result.coverApplied)
                .put("message", result.message)
                .toString()
        }
        return taskJson(task)
    }

    private suspend fun startAiLibrary(args: JSONObject): JSONObject {
        val findCovers = args.optBoolean("find_covers", true)
        val targets = db.seriesDao().getAll().filter { !it.hidden }
        val task = tasks.start("ai_organize_library", targets.size) { report ->
            var applied = 0
            var covers = 0
            targets.forEachIndexed { index, series ->
                report.report("AI 整理 ${series.displayTitle} (${index + 1}/${targets.size})", index, targets.size)
                runCatching {
                    graph.aiAgent.organizeSeries(series, findCovers) { stage -> report.report(stage) }
                }.onSuccess { result ->
                    if (result.episodes > 0) applied++
                    if (result.coverApplied) covers++
                }
                delay(200)
            }
            JSONObject().put("applied", applied).put("covers", covers).put("total", targets.size).toString()
        }
        return taskJson(task)
    }

    private suspend fun startFillCovers(): JSONObject {
        val targets = db.seriesDao().getAll().filter { !it.hidden && it.coverUrl.isNullOrBlank() }
        val task = tasks.start("ai_fill_covers", targets.size) { report ->
            var covers = 0
            targets.forEachIndexed { index, series ->
                report.report("找封面 ${series.displayTitle} (${index + 1}/${targets.size})", index, targets.size)
                if (graph.aiAgent.fillCover(series) != null) covers++
            }
            JSONObject().put("covers", covers).put("total", targets.size).toString()
        }
        return taskJson(task)
    }

    private suspend fun aiStatus(): JSONObject {
        val provider = graph.settings.aiProvider.first()
        val keys = graph.settings.aiApiKeys.first()
        val cfg = runCatching { graph.ai.currentConfig() }.getOrNull()
        return JSONObject()
            .put("provider", provider)
            .put("provider_name", com.juying.tv.data.ai.AiProviders.byId(provider).name)
            .put("model", graph.settings.aiModel.first())
            .put("key_configured", keys[provider].orEmpty().isNotBlank())
            .put("supports_web_search", cfg?.supportsSearch ?: false)
            .put("tasks", tasks.list().size)
    }

    private suspend fun aiModels(): JSONObject {
        val models = graph.ai.listModels()
        return JSONObject().put("count", models.size).put("models", JSONArray(models))
    }

    private suspend fun setAiModel(args: JSONObject): JSONObject {
        val model = args.require("model")
        graph.settings.setAiModel(model)
        return JSONObject().put("ok", true).put("model", model)
    }

    private suspend fun setAiProvider(args: JSONObject): JSONObject {
        val id = args.require("provider")
        val preset = com.juying.tv.data.ai.AiProviders.presets.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("未知供应商：$id")
        graph.settings.setAiProvider(id)
        preset.models.firstOrNull()?.let { graph.settings.setAiModel(it) }
        return JSONObject().put("ok", true).put("provider", id).put("name", preset.name)
    }

    private suspend fun setAiBaseUrl(args: JSONObject): JSONObject {
        val url = args.require("base_url")
        graph.settings.setAiBaseUrl(url)
        return JSONObject().put("ok", true).put("base_url", url)
    }

    private suspend fun taskStatus(args: JSONObject): JSONObject {
        val id = args.require("task_id")
        val task = tasks.get(id) ?: throw IllegalArgumentException("任务不存在：$id")
        return taskJson(task)
    }

    private fun taskList(): JSONObject {
        val array = JSONArray()
        tasks.list().sortedByDescending { it.createdAt }.take(50).forEach { array.put(taskJson(it)) }
        return JSONObject().put("count", array.length()).put("items", array)
    }

    private fun taskCancel(args: JSONObject): JSONObject {
        val id = args.require("task_id")
        val ok = tasks.cancel(id)
        return JSONObject().put("ok", ok).put("task_id", id)
    }

    private suspend fun cacheScrapePoster(series: SeriesEntity, candidate: com.juying.tv.data.scrape.ScrapeCandidate): String? {
        val bytes = graph.scrape.downloadPoster(candidate) ?: return null
        return runCatching {
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val file = File(dir, "mcp-${sha256(candidate.posterUrl ?: candidate.externalId).take(32)}.jpg")
            file.writeBytes(bytes)
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    private fun seriesJson(series: SeriesEntity, episodeCount: Int): JSONObject = JSONObject()
        .put("id", series.id)
        .put("title", series.displayTitle)
        .put("auto_title", series.autoTitle)
        .put("kind", series.kind)
        .put("year", series.year ?: 0)
        .put("hidden", series.hidden)
        .put("kids_allowed", series.kidsAllowed)
        .put("cover_url", series.coverUrl ?: "")
        .put("remote_path", series.remotePath)
        .put("episode_count", episodeCount)

    private fun mediaJson(media: MediaEntity): JSONObject = JSONObject()
        .put("id", media.id)
        .put("series_id", media.seriesId)
        .put("file_name", media.fileName)
        .put("title", media.title)
        .put("season", media.season)
        .put("episode", media.episode)
        .put("sort_index", media.sortIndex)
        .put("duration_ms", media.durationMs)
        .put("hidden", media.hidden)
        .put("kids_allowed", media.kidsAllowed)

    private fun taskJson(task: McpTask): JSONObject = JSONObject()
        .put("task_id", task.id)
        .put("type", task.type)
        .put("status", task.status)
        .put("message", task.message)
        .put("progress", task.progress)
        .put("total", task.total)
        .put("result", task.result ?: "")
        .put("error", task.error ?: "")
        .put("created_at", task.createdAt)
        .put("updated_at", task.updatedAt)

    private fun tool(
        name: String,
        description: String,
        properties: Map<String, JSONObject>,
        required: List<String>
    ): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", description)
        put("inputSchema", JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().also { props ->
                properties.forEach { (key, value) -> props.put(key, value) }
            })
            put("required", JSONArray(required))
        })
    }

    private fun prop(type: String, description: String): JSONObject = JSONObject()
        .put("type", type)
        .put("description", description)

    private fun JSONObject.require(name: String): String {
        val value = optString(name).trim()
        if (value.isBlank()) throw IllegalArgumentException("缺少参数：$name")
        return value
    }
}
