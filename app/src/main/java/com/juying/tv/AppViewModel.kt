package com.juying.tv

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juying.tv.data.AppGraph
import com.juying.tv.data.db.CollectionEntity
import com.juying.tv.data.db.MediaEntity
import com.juying.tv.data.db.ProgressEntity
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.data.db.SourceEntity
import com.juying.tv.data.repo.PlaybackBundle
import com.juying.tv.data.repo.ResolvedEpisode
import com.juying.tv.data.source.QuarkConfig
import com.juying.tv.data.source.WebDavConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private var scanJob: Job? = null

    val sources = repo.sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val series = repo.visibleSeries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hiddenSeries = repo.hiddenSeries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collections = repo.collections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collectionItems = repo.collectionItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val kidsMode = settings.kidsMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val pinSet = settings.pinHash.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val autoNext = settings.autoNext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val kidsSeries = repo.kidsSeries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        fromStart: Boolean = false
    ): PlaybackBundle = graph.playbackResolver.prepare(mediaId, kidsOnly, fromStart)

    suspend fun resolveEpisode(mediaId: String): ResolvedEpisode =
        graph.playbackResolver.resolve(mediaId)

    fun saveProgress(mediaId: String, seriesId: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            repo.saveProgress(mediaId, seriesId, positionMs, durationMs)
        }
    }

    fun updateDuration(mediaId: String, durationMs: Long) {
        viewModelScope.launch { graph.playbackResolver.updateDuration(mediaId, durationMs) }
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
