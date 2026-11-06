package com.juying.tv

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import com.juying.tv.util.sha256
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juying.tv.data.AppGraph
import com.juying.tv.data.db.CollectionEntity
import com.juying.tv.data.db.MediaEntity
import com.juying.tv.data.db.MediaPathRow
import com.juying.tv.data.db.ProgressEntity
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.data.db.SourceEntity
import com.juying.tv.data.repo.PlaybackBundle
import com.juying.tv.data.repo.ResolvedEpisode
import com.juying.tv.data.source.QuarkConfig
import com.juying.tv.data.source.WebDavConfig
import com.juying.tv.data.scrape.ScrapeCandidate
import com.juying.tv.data.scrape.ScrapeRateLimitedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScrapeUiState(
    val running: Boolean = false,
    val currentTitle: String = "",
    val index: Int = 0,
    val total: Int = 0,
    val matched: Int = 0,
    val failed: Int = 0,
    val finished: Boolean = false,
    val message: String? = null
)

data class AiUiState(
    val running: Boolean = false,
    val seriesId: String? = null,
    val currentTitle: String = "",
    val index: Int = 0,
    val total: Int = 0,
    val applied: Int = 0,
    val covers: Int = 0,
    val finished: Boolean = false,
    val message: String? = null
)

data class ScanUiState(
    val running: Boolean = false,
    val sourceName: String = "",
    val files: Int = 0,
    val folder: String = "",
    val index: Int = 0,
    val total: Int = 0,
    val finished: Boolean = false,
    val error: String? = null,
    val summary: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val graph: AppGraph = JuyingApp.graphOf(application)
    private val repo = graph.repository
    private val settings = graph.settings

    private val _scanState = MutableStateFlow<ScanUiState?>(null)
    val scanState: StateFlow<ScanUiState?> = _scanState.asStateFlow()

    private val _scrapeState = MutableStateFlow<ScrapeUiState?>(null)
    val scrapeState: StateFlow<ScrapeUiState?> = _scrapeState.asStateFlow()

    private val _aiState = MutableStateFlow<AiUiState?>(null)
    val aiState: StateFlow<AiUiState?> = _aiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private var scanJob: Job? = null
    private var scrapeJob: Job? = null
    private var aiJob: Job? = null

    val sources = repo.sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val series = repo.visibleSeries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hiddenSeries = repo.hiddenSeries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collections = repo.collections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collectionItems = repo.collectionItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mediaPaths = repo.mediaPaths.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val kidsMode = settings.kidsMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val pinSet = settings.pinHash.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val autoNext = settings.autoNext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val kidsSeries = repo.kidsSeries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scrapeMeta = repo.scrapeMeta.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scrapeMergeDuplicates = settings.scrapeMergeDuplicates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val scrapeYearCollections = settings.scrapeYearCollections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val aiKeyConfigured = settings.aiApiKey.map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val aiKeyLength = settings.aiApiKey.map { it.length }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val aiModel = settings.aiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "qwen-plus")

    val continueWatching = kidsMode.flatMapLatest { kids ->
        repo.continueWatching(kids)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun mediaFlow(seriesId: String) = kidsMode.flatMapLatest { kids ->
        repo.mediaOfSeries(seriesId, kids)
    }

    fun allMediaFlow(seriesId: String) = repo.allMediaOfSeries(seriesId)

    fun seriesFlow(seriesId: String) = repo.seriesById(seriesId)
    fun progressFlow(seriesId: String) = repo.progressOfSeries(seriesId)
    fun collectionItemsFlow(collectionId: String) = repo.collectionItemsOf(collectionId)

    fun scanAll(onlySourceId: String? = null) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            val targets = if (onlySourceId != null) {
                listOfNotNull(repo.getSource(onlySourceId)).filter { it.enabled }
            } else {
                sources.value.filter { it.enabled }
            }
            if (targets.isEmpty()) {
                _events.emit("还没有可扫描的媒体源，先添加一个吧")
                return@launch
            }
            var failed = 0
            var totalItems = 0
            targets.forEachIndexed { index, source ->
                _scanState.value = ScanUiState(
                    running = true,
                    sourceName = source.name,
                    index = index + 1,
                    total = targets.size
                )
                try {
                    val result = repo.scanSource(source) { count, folder ->
                        val current = _scanState.value
                        _scanState.value = (current ?: ScanUiState()).copy(
                            running = true,
                            sourceName = source.name,
                            files = count,
                            folder = folder,
                            index = index + 1,
                            total = targets.size
                        )
                    }
                    totalItems += result.files.count { it.kind == com.juying.tv.data.source.ScannedKind.VIDEO }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    val message = e.message ?: "扫描失败"
                    _scanState.value = ScanUiState(
                        running = false,
                        sourceName = source.name,
                        index = index + 1,
                        total = targets.size,
                        finished = true,
                        error = "$message"
                    )
                    _events.emit("${source.name}：$message")
                }
            }
            _scanState.value = ScanUiState(
                running = false,
                finished = true,
                summary = if (failed == 0) {
                    "扫描完成，共收录 $totalItems 个视频"
                } else {
                    "扫描结束，$failed 个媒体源失败"
                },
                error = if (failed > 0) "部分媒体源扫描失败" else null
            )
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanUiState(finished = true, summary = "已取消扫描")
    }

    fun dismissScanBanner() {
        _scanState.value = null
    }

    fun addLocalFolder(uri: Uri) {
        viewModelScope.launch {
            val source = repo.addLocalSource(uri)
            _events.emit("已添加本地目录：${source.name}")
            scanAll(source.id)
        }
    }

    fun addMediaStoreSource() {
        viewModelScope.launch {
            val source = repo.addMediaStoreSource()
            _events.emit("已添加本机与 U 盘媒体源")
            scanAll(source.id)
        }
    }

    fun saveWebDavSource(id: String?, name: String, config: WebDavConfig, scanAfter: Boolean = true) {
        viewModelScope.launch {
            try {
                val source = if (id == null) {
                    repo.addWebDavSource(name, config)
                } else {
                    val existing = repo.getSource(id) ?: repo.addWebDavSource(name, config)
                    existing.copy(
                        name = name.ifBlank { "WebDAV" },
                        config = com.juying.tv.data.source.SourceConfigs.encodeWebDav(config)
                    ).also { repo.updateSource(it); it }
                }
                _events.emit("WebDAV 媒体源已保存")
                if (scanAfter) scanAll(source.id)
            } catch (e: Exception) {
                _events.emit(e.message ?: "保存失败")
            }
        }
    }

    fun saveQuarkSource(id: String?, name: String, config: QuarkConfig, scanAfter: Boolean = true) {
        viewModelScope.launch {
            try {
                val source = if (id == null) {
                    repo.addQuarkSource(name, config)
                } else {
                    val existing = repo.getSource(id) ?: repo.addQuarkSource(name, config)
                    existing.copy(
                        name = name.ifBlank { "夸克网盘" },
                        config = com.juying.tv.data.source.SourceConfigs.encodeQuark(config)
                    ).also { repo.updateSource(it); it }
                }
                _events.emit("夸克网盘媒体源已保存")
                if (scanAfter) scanAll(source.id)
            } catch (e: Exception) {
                _events.emit(e.message ?: "保存失败")
            }
        }
    }

    suspend fun testWebDav(config: WebDavConfig): Result<String> =
        runCatching { graph.webDav.test(config) }

    suspend fun testQuark(config: QuarkConfig): Result<String> =
        runCatching { graph.quark.test(config) }

    fun deleteSource(id: String) {
        viewModelScope.launch {
            repo.deleteSource(id)
            _events.emit("媒体源已移除")
        }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repo.setSourceEnabled(id, enabled) }
    }

    fun renameSeries(id: String, title: String) {
        viewModelScope.launch {
            repo.renameSeries(id, title)
            _events.emit("剧集名称已更新")
        }
    }

    fun setSeriesCover(id: String, url: String?) {
        viewModelScope.launch {
            repo.setSeriesCover(id, url)
            _events.emit("封面已更新")
        }
    }

    fun hideSeries(id: String) {
        viewModelScope.launch {
            repo.setSeriesHidden(id, true)
            _events.emit("已从媒体库隐藏，可在“媒体库管理”中恢复")
        }
    }

    fun restoreSeries(id: String) {
        viewModelScope.launch {
            repo.restoreSeriesHidden(id)
            _events.emit("已恢复到媒体库")
        }
    }

    fun setSeriesKids(id: String, allow: Boolean) {
        viewModelScope.launch { repo.setSeriesKidsAllowed(id, allow) }
    }

    fun setEpisodeKids(id: String, allow: Boolean) {
        viewModelScope.launch { repo.setEpisodeKidsAllowed(id, allow) }
    }

    fun setAllKids(allow: Boolean) {
        viewModelScope.launch {
            repo.setAllKidsAllowed(allow)
            _events.emit(if (allow) "已允许全部内容进入儿童模式" else "已清空儿童模式允许列表")
        }
    }

    fun createCollection(name: String, onCreated: (CollectionEntity) -> Unit = {}) {
        viewModelScope.launch {
            val collection = repo.createCollection(name)
            _events.emit("已创建合集：${collection.name}")
            onCreated(collection)
        }
    }

    fun renameCollection(id: String, name: String) {
        viewModelScope.launch { repo.renameCollection(id, name) }
    }

    fun setCollectionCover(id: String, url: String?) {
        viewModelScope.launch { repo.setCollectionCover(id, url) }
    }

    fun setCollectionShowOnHome(id: String, show: Boolean) {
        viewModelScope.launch { repo.setCollectionShowOnHome(id, show) }
    }

    fun deleteCollection(id: String) {
        viewModelScope.launch {
            repo.deleteCollection(id)
            _events.emit("合集已删除")
        }
    }

    fun addSeriesToCollection(collectionId: String, seriesId: String) {
        viewModelScope.launch { repo.addSeriesToCollection(collectionId, seriesId) }
    }

    fun removeSeriesFromCollection(collectionId: String, seriesId: String) {
        viewModelScope.launch { repo.removeSeriesFromCollection(collectionId, seriesId) }
    }

    fun toggleFolderInCollection(
        collectionId: String,
        seriesIds: Collection<String>,
        add: Boolean
    ) {
        viewModelScope.launch {
            if (add) repo.addSeriesBatchToCollection(collectionId, seriesIds)
            else repo.removeSeriesBatchFromCollection(collectionId, seriesIds)
        }
    }

    fun moveCollectionItem(collectionId: String, seriesId: String, delta: Int) {
        viewModelScope.launch { repo.moveCollectionItem(collectionId, seriesId, delta) }
    }

    fun moveCollection(collectionId: String, delta: Int) {
        viewModelScope.launch { repo.moveCollection(collectionId, delta) }
    }

    suspend fun collectionsForSeries(seriesId: String): List<String> =
        repo.getCollectionsForSeries(seriesId)

    suspend fun allSeriesForPicker(): List<SeriesEntity> = series.value

    suspend fun episodesForManage(seriesId: String): List<MediaEntity> = repo.getAllEpisodes(seriesId)

    suspend fun preparePlayback(
        mediaId: String,
        kidsOnly: Boolean,
        fromStart: Boolean = false,
        forceRefresh: Boolean = false
    ): PlaybackBundle = graph.playbackResolver.prepare(mediaId, kidsOnly, fromStart, forceRefresh)

    suspend fun resolveEpisode(mediaId: String): ResolvedEpisode =
        graph.playbackResolver.resolve(mediaId)

    suspend fun episodeThumbnail(mediaId: String): java.io.File? =
        graph.playbackResolver.episodeThumbnail(mediaId)

    fun saveProgress(mediaId: String, seriesId: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            repo.saveProgress(mediaId, seriesId, positionMs, durationMs)
        }
    }

    fun updateDuration(mediaId: String, durationMs: Long) {
        viewModelScope.launch { graph.playbackResolver.updateDuration(mediaId, durationMs) }
    }

    fun scrapeAll(force: Boolean = false) {
        if (scrapeJob?.isActive == true) return
        if (aiJob?.isActive == true) {
            _events.tryEmit("AI 正在整理，请稍候")
            return
        }
        scrapeJob = viewModelScope.launch {
            val all = series.value
            val existing = runCatching { repo.getAllScrapeMeta().map { it.seriesId }.toSet() }
                .getOrDefault(emptySet())
            val targets = if (force) all else all.filter { it.id !in existing }
            if (targets.isEmpty()) {
                _events.emit("没有需要刮削的内容")
                return@launch
            }
            var matched = 0
            var skipped = 0
            var failed = 0
            var emptyStreak = 0
            _scrapeState.value = ScrapeUiState(
                running = true,
                total = targets.size,
                index = 0
            )

            val doubanReady = runCatching { graph.scrape.canary() }.getOrElse { false }
            if (!doubanReady) {
                _events.emit("豆瓣当前限流，本次优先 TMDB / TVmaze 与本地缓存")
            }
            val skipDouban = !doubanReady

            for ((index, item) in targets.withIndex()) {
                _scrapeState.value = _scrapeState.value?.copy(
                    running = true,
                    currentTitle = item.displayTitle,
                    index = index + 1,
                    total = targets.size,
                    matched = matched,
                    failed = failed
                )
                var attempt = 0
                while (true) {
                    try {
                        val best = graph.scrape.findMatch(item, force, skipDouban = skipDouban)
                        if (best != null) {
                            val posterRef = if (item.coverUrl.isNullOrBlank() && !best.posterUrl.isNullOrBlank()) {
                                cachePoster(best)
                            } else {
                                best.posterUrl
                            }
                            repo.applyScrape(item, best.copy(posterUrl = posterRef))
                            matched++
                            emptyStreak = 0
                        } else {
                            skipped++
                            emptyStreak++
                        }
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ScrapeRateLimitedException) {
                        attempt++
                        if (attempt > 2) {
                            val summary = "刮削中止：${e.message ?: "豆瓣限流"}。已匹配 $matched 部，跳过 $skipped 部，稍后可继续。"
                            _scrapeState.value = ScrapeUiState(
                                running = false,
                                total = targets.size,
                                index = index + 1,
                                matched = matched,
                                failed = failed,
                                finished = true,
                                message = summary
                            )
                            _events.emit(summary)
                            return@launch
                        }
                        val waitSeconds = (e.retryAfterMs / 1000).coerceAtLeast(5)
                        _scrapeState.value = _scrapeState.value?.copy(
                            message = "豆瓣限流，等待 $waitSeconds 秒后重试…"
                        )
                        _events.emit("豆瓣限流，暂停 $waitSeconds 秒")
                        kotlinx.coroutines.delay(e.retryAfterMs)
                    } catch (e: Exception) {
                        failed++
                        android.util.Log.w("JuyingScrape", "刮削失败 ${item.displayTitle}: ${e.message}")
                        break
                    }
                }

                if (!skipDouban && emptyStreak >= 4) {
                    val stillHealthy = runCatching { graph.scrape.canary() }.getOrElse { false }
                    if (!stillHealthy) {
                        _scrapeState.value = _scrapeState.value?.copy(
                            message = "豆瓣返回空结果，暂停 60 秒检查…"
                        )
                        kotlinx.coroutines.delay(60_000)
                        val retryHealthy = runCatching { graph.scrape.canary() }.getOrElse { false }
                        if (!retryHealthy) {
                            val summary = "刮削暂停：豆瓣匿名接口被限流。已匹配 $matched 部，稍后可继续或使用“测试刮削源”检查。"
                            _scrapeState.value = ScrapeUiState(
                                running = false,
                                total = targets.size,
                                index = index + 1,
                                matched = matched,
                                failed = failed,
                                finished = true,
                                message = summary
                            )
                            _events.emit(summary)
                            return@launch
                        }
                    }
                    emptyStreak = 0
                }
                kotlinx.coroutines.delay(200)
            }

            if (scrapeMergeDuplicates.value) {
                runCatching { repo.mergeScrapedDuplicates() }
            }
            if (scrapeYearCollections.value) {
                runCatching { repo.ensureYearCollections() }
            }
            val summary = if (matched == 0) {
                "刮削完成：匹配 0 部，跳过 $skipped 部。可能没有对应条目，或接口被限流；可用“测试刮削源”检查。"
            } else {
                "刮削完成：匹配 $matched 部，跳过 $skipped 部" +
                    if (failed > 0) "，失败 $failed 部" else ""
            }
            _scrapeState.value = ScrapeUiState(
                running = false,
                total = targets.size,
                index = targets.size,
                matched = matched,
                failed = failed,
                finished = true,
                message = summary
            )
            _events.emit(summary)
        }
    }

    fun scrapeSeries(seriesId: String) {
        if (scrapeJob?.isActive == true) {
            _events.tryEmit("正在刮削，请稍候")
            return
        }
        if (aiJob?.isActive == true) {
            _events.tryEmit("AI 正在整理，请稍候")
            return
        }
        scrapeJob = viewModelScope.launch {
            val target = series.value.firstOrNull { it.id == seriesId }
            if (target == null) {
                _events.emit("剧集不存在")
                return@launch
            }
            _events.emit("正在刮削「${target.displayTitle}」…")
            try {
                val best = graph.scrape.findMatch(target, force = true)
                if (best == null) {
                    _events.emit("没有找到可靠匹配，保留原标题与封面")
                    return@launch
                }
                val posterRef = if (target.coverUrl.isNullOrBlank() && !best.posterUrl.isNullOrBlank()) {
                    cachePoster(best)
                } else {
                    best.posterUrl
                }
                repo.applyScrape(target, best.copy(posterUrl = posterRef))
                val source = when (best.provider) {
                    "tvmaze" -> "TVmaze"
                    "tmdb" -> "TMDB"
                    else -> "豆瓣"
                }
                _events.emit(
                    "已匹配（$source）：${best.title}" +
                        (best.year?.let { "（$it）" } ?: "") +
                        " · 分数 ${best.score}"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ScrapeRateLimitedException) {
                _events.emit("刮削限流：${e.message ?: "请稍后再试"}")
            } catch (e: Exception) {
                _events.emit("刮削失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun cancelScrape() {
        val current = _scrapeState.value
        scrapeJob?.cancel()
        scrapeJob = null
        _scrapeState.value = (current ?: ScrapeUiState()).copy(
            running = false,
            finished = true,
            message = "已取消刮削"
        )
    }

    fun dismissScrapeBanner() {
        _scrapeState.value = null
    }

    private suspend fun cachePoster(candidate: ScrapeCandidate): String? {
        val bytes = graph.scrape.downloadPoster(candidate) ?: run {
            android.util.Log.w("JuyingScrape", "海报下载失败 ${candidate.provider}")
            return null
        }
        return runCatching {
            val directory = File(getApplication<Application>().filesDir, "covers").apply { mkdirs() }
            val ext = if (candidate.posterUrl?.contains(".png", true) == true) "png" else "jpg"
            val file = File(directory, sha256(candidate.posterUrl ?: candidate.externalId).take(32) + ".$ext")
            file.writeBytes(bytes)
            file.toUri().toString()
        }.getOrNull()
    }

    fun testScrapeSources() {
        viewModelScope.launch {
            val results = runCatching { graph.scrape.testSources() }.getOrElse { e ->
                _events.emit("刮削源测试失败：${e.message ?: "未知错误"}")
                return@launch
            }
            val text = results.joinToString("；") { result ->
                val name = when (result.provider) {
                    "tvmaze" -> "TVmaze"
                    "tmdb" -> "TMDB"
                    else -> "豆瓣"
                }
                if (result.ok) "$name 成功 ${result.count} 条" else "$name 失败：${result.detail}"
            }
            _events.emit(text)
        }
    }

    fun testScrapeConnection() = testScrapeSources()

    fun setAiKey(key: String) {
        viewModelScope.launch {
            settings.setAiApiKey(key)
            _events.emit(if (key.isBlank()) "已清除百炼 API Key" else "百炼 API Key 已保存")
        }
    }

    fun clearAiKey() = setAiKey("")

    fun setAiModel(model: String) {
        viewModelScope.launch {
            settings.setAiModel(model)
            _events.emit("AI 模型已切换为 $model")
        }
    }

    fun testAi() {
        viewModelScope.launch {
            if (!hasAiKey()) {
                _events.emit("请先在设置中填写百炼 API Key")
                return@launch
            }
            _events.emit("正在测试百炼连接…")
            runCatching { graph.bailian.test() }
                .onSuccess { _events.emit("百炼连接成功：$it") }
                .onFailure { _events.emit("百炼连接失败：${it.message ?: "未知错误"}") }
        }
    }

    fun aiOrganizeSeries(seriesId: String) {
        if (aiJob?.isActive == true) {
            _events.tryEmit("AI 正在整理，请稍候")
            return
        }
        if (scrapeJob?.isActive == true) {
            _events.tryEmit("正在刮削，请稍后再试")
            return
        }
        aiJob = viewModelScope.launch {
            if (!hasAiKey()) {
                _events.emit("请先在设置中填写百炼 API Key")
                return@launch
            }
            val target = series.value.firstOrNull { it.id == seriesId }
            if (target == null) {
                _events.emit("剧集不存在")
                return@launch
            }
            _aiState.value = AiUiState(
                running = true,
                seriesId = seriesId,
                currentTitle = target.displayTitle,
                index = 1,
                total = 1,
                message = "正在读取文件名与目录…"
            )
            _events.emit("AI 正在整理「${target.displayTitle}」…")
            try {
                val result = graph.aiAgent.organizeSeries(target) { stage ->
                    _aiState.value = _aiState.value?.copy(message = stage)
                }
                _aiState.value = AiUiState(
                    running = false,
                    seriesId = seriesId,
                    currentTitle = target.displayTitle,
                    index = 1,
                    total = 1,
                    applied = if (result.episodes > 0) 1 else 0,
                    covers = if (result.coverApplied) 1 else 0,
                    finished = true,
                    message = result.message
                )
                _events.emit(result.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("JuyingAi", "AI 整理失败: ${e.message}")
                _aiState.value = AiUiState(
                    running = false,
                    seriesId = seriesId,
                    currentTitle = target.displayTitle,
                    index = 1,
                    total = 1,
                    finished = true,
                    message = "AI 整理失败：${e.message ?: "未知错误"}"
                )
                _events.emit("AI 整理失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun aiOrganizeAll(findCovers: Boolean = true) {
        if (aiJob?.isActive == true) return
        if (scrapeJob?.isActive == true) {
            _events.tryEmit("正在刮削，请稍后再试")
            return
        }
        aiJob = viewModelScope.launch {
            if (!hasAiKey()) {
                _events.emit("请先在设置中填写百炼 API Key")
                return@launch
            }
            val targets = series.value
            if (targets.isEmpty()) {
                _events.emit("媒体库为空")
                return@launch
            }
            var applied = 0
            var covers = 0
            var failed = 0
            _aiState.value = AiUiState(running = true, total = targets.size)
            _events.emit("AI 开始整理 ${targets.size} 部内容，可能产生模型费用")
            for ((index, item) in targets.withIndex()) {
                _aiState.value = _aiState.value?.copy(
                    running = true,
                    currentTitle = item.displayTitle,
                    index = index + 1,
                    total = targets.size,
                    applied = applied,
                    covers = covers
                )
                try {
                    val result = graph.aiAgent.organizeSeries(item, findCovers)
                    if (result.episodes > 0) applied++
                    if (result.coverApplied) covers++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    android.util.Log.w("JuyingAi", "AI 整理失败 ${item.displayTitle}: ${e.message}")
                }
                kotlinx.coroutines.delay(250)
            }
            val summary = "AI 整理完成：成功 $applied 部，封面 $covers 部" +
                if (failed > 0) "，失败 $failed 部" else ""
            _aiState.value = AiUiState(
                running = false,
                total = targets.size,
                index = targets.size,
                applied = applied,
                covers = covers,
                finished = true,
                message = summary
            )
            _events.emit(summary)
        }
    }

    fun aiFillCovers() {
        if (aiJob?.isActive == true) return
        aiJob = viewModelScope.launch {
            if (!hasAiKey()) {
                _events.emit("请先在设置中填写百炼 API Key")
                return@launch
            }
            val targets = series.value.filter { it.coverUrl.isNullOrBlank() }
            if (targets.isEmpty()) {
                _events.emit("没有缺少封面的内容")
                return@launch
            }
            var covers = 0
            _aiState.value = AiUiState(running = true, total = targets.size)
            for ((index, item) in targets.withIndex()) {
                _aiState.value = _aiState.value?.copy(
                    running = true,
                    currentTitle = item.displayTitle,
                    index = index + 1,
                    total = targets.size,
                    covers = covers
                )
                try {
                    if (graph.aiAgent.fillCover(item) != null) covers++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("JuyingAi", "AI 封面失败 ${item.displayTitle}: ${e.message}")
                }
                kotlinx.coroutines.delay(250)
            }
            val summary = "AI 封面完成：成功 $covers / ${targets.size} 部"
            _aiState.value = AiUiState(
                running = false,
                total = targets.size,
                index = targets.size,
                covers = covers,
                finished = true,
                message = summary
            )
            _events.emit(summary)
        }
    }

    fun cancelAi() {
        val current = _aiState.value
        aiJob?.cancel()
        aiJob = null
        _aiState.value = (current ?: AiUiState()).copy(
            running = false,
            finished = true,
            message = "已取消 AI 整理"
        )
    }

    fun dismissAiBanner() {
        _aiState.value = null
    }

    private suspend fun hasAiKey(): Boolean = settings.aiApiKey.first().isNotBlank()

    fun setScrapeMergeDuplicates(enabled: Boolean) {
        viewModelScope.launch { settings.setScrapeMergeDuplicates(enabled) }
    }

    fun setScrapeYearCollections(enabled: Boolean) {
        viewModelScope.launch { settings.setScrapeYearCollections(enabled) }
    }

    fun setAutoNext(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoNext(enabled) }
    }

    fun setResumePlayback(enabled: Boolean) {
        viewModelScope.launch { settings.setResumePlayback(enabled) }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            settings.setPin(pin)
            _events.emit("家长锁密码已设置")
        }
    }

    suspend fun verifyPin(pin: String): Boolean = settings.verifyPin(pin)

    fun clearPin() {
        viewModelScope.launch {
            settings.clearPin()
            _events.emit("家长锁已关闭")
        }
    }

    fun enterKidsMode() {
        viewModelScope.launch {
            if (!pinSet.value) {
                _events.emit("请先设置家长锁密码")
                return@launch
            }
            settings.setKidsMode(true)
            _events.emit("已进入儿童模式")
        }
    }

    fun exitKidsMode() {
        viewModelScope.launch {
            settings.setKidsMode(false)
            _events.emit("已退出儿童模式")
        }
    }

    fun notify(message: String) {
        viewModelScope.launch { _events.emit(message) }
    }
}
