package com.juying.tv.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import com.juying.tv.AppViewModel
import com.juying.tv.JuyingApp
import coil.compose.AsyncImage
import com.juying.tv.data.db.MediaEntity
import com.juying.tv.data.db.SeriesKind
import com.juying.tv.data.repo.PlaybackBundle
import com.juying.tv.data.repo.ResolvedEpisode
import com.juying.tv.data.repo.SubtitleSpec
import com.juying.tv.data.subtitle.OnlineSubtitle
import com.juying.tv.ui.components.Badge
import com.juying.tv.ui.components.FocusableSurface
import com.juying.tv.ui.components.ProgressLine
import com.juying.tv.ui.components.TvButton
import com.juying.tv.ui.components.TvChip
import com.juying.tv.ui.components.TvDialog
import com.juying.tv.ui.theme.JuyingColors
import com.juying.tv.util.formatDuration
import com.juying.tv.util.formatEpisode
import com.juying.tv.util.formatSpeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class SubtitleOption(
    val label: String,
    val language: String?,
    val group: Tracks.Group,
    val trackIndex: Int
)

private data class MenuOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
fun PlayerScreen(
    viewModel: AppViewModel,
    mediaId: String,
    seriesId: String,
    kidsMode: Boolean,
    fromStart: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val graph = remember { JuyingApp.graphOf(context) }
    val scope = rememberCoroutineScope()
    val autoNext by viewModel.autoNext.collectAsStateWithLifecycle()
    val subtitleSize by viewModel.subtitleSize.collectAsStateWithLifecycle()
    val subtitleBottom by viewModel.subtitleBottom.collectAsStateWithLifecycle()
    val subtitleFont by viewModel.subtitleFont.collectAsStateWithLifecycle()
    val autoNextLatest by rememberUpdatedState(autoNext)

    var bundle by remember { mutableStateOf<PlaybackBundle?>(null) }
    var resolved by remember { mutableStateOf<ResolvedEpisode?>(null) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var overlayVisible by remember { mutableStateOf(true) }
    var showEpisodes by remember { mutableStateOf(false) }
    var showSubtitles by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showAspect by remember { mutableStateOf(false) }
    var showSubtitleStyle by remember { mutableStateOf(false) }
    var showOnlineSearch by remember { mutableStateOf(false) }
    var showOnlineResults by remember { mutableStateOf(false) }
    var onlineQuery by remember { mutableStateOf("") }
    var onlineLoading by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    var onlineResults by remember { mutableStateOf<List<OnlineSubtitle>>(emptyList()) }
    var manualSubtitle by remember { mutableStateOf<SubtitleSpec?>(null) }
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    var playerResizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var speed by remember { mutableFloatStateOf(1f) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var subtitleOptions by remember { mutableStateOf<List<SubtitleOption>>(emptyList()) }
    var selectedSubtitle by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val subtitleFocus = remember { FocusRequester() }
    val progressFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    var showLocalBrowser by remember { mutableStateOf(false) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showLocalBrowser = true
        } else {
            toast = "没有存储权限，无法读取电视或 U 盘里的字幕"
        }
    }

    fun openLocalSubtitleBrowser() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showLocalBrowser = true
        } else {
            storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun returnToSubtitleControl() {
        scope.launch {
            delay(250)
            runCatching { subtitleFocus.requestFocus() }
        }
    }

    fun pickLocalSubtitle(file: File) {
        pendingSeekMs = positionMs
        manualSubtitle = SubtitleSpec(
            Uri.fromFile(file),
            "本地字幕 · ${file.name}",
            subtitleMime(file.name),
            "zh"
        )
        showLocalBrowser = false
        returnToSubtitleControl()
        toast = "已加载 ${file.name}"
    }

    LaunchedEffect(mediaId, reload) {
        loading = true
        error = null
        try {
            val prepared = viewModel.preparePlayback(
                mediaId = mediaId,
                kidsOnly = kidsMode,
                fromStart = fromStart,
                forceRefresh = reload > 0
            )
            bundle = prepared
            currentIndex = prepared.index
            resolved = prepared.resolved
        } catch (e: Exception) {
            Log.w("JuyingPlayer", "打开视频失败: ${e.message}", e)
            error = e.message ?: "打开视频失败"
        } finally {
            loading = false
        }
    }

    val exoPlayer = remember(resolved?.url, reload, manualSubtitle) {
        resolved?.let { r ->
            val allSubtitles = r.subtitles + listOfNotNull(manualSubtitle)
            val httpFactory = OkHttpDataSource.Factory(graph.okHttp)
                .setDefaultRequestProperties(r.headers)
            val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
            // Media3 默认提取器之外，追加 HLS/DASH 与 TS 时间戳容错；
            // FFmpeg 扩展负责硬件解码器不支持的特殊音轨（AC3/DTS/TrueHD 等）。
            val extractorsFactory = DefaultExtractorsFactory()
                .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            val renderersFactory = DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            val trackSelector = DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setPreferredAudioLanguage("zh")
                        .setPreferredTextLanguage("zh")
                )
            }
            ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    setHandleAudioBecomingNoisy(true)
                    val builder = MediaItem.Builder().setUri(r.url)
                    val mime = r.mime?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                    mime?.let { builder.setMimeType(it) }
                    if (allSubtitles.isNotEmpty()) {
                        builder.setSubtitleConfigurations(
                            allSubtitles.map { sub ->
                                MediaItem.SubtitleConfiguration.Builder(sub.uri)
                                    .setMimeType(sub.mime)
                                    .setLanguage(sub.language)
                                    .setLabel(sub.label)
                                    .build()
                            }
                        )
                    }
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        true
                    )
                    setMediaItem(builder.build())
                    prepare()
                    val start = pendingSeekMs ?: r.startPositionMs
                    if (start > 30_000) {
                        seekTo(start)
                        if (pendingSeekMs == null) toast = "已从上次观看位置继续"
                    } else if (start == 0L) {
                        seekTo(0)
                    }
                    playWhenReady = true
                    setPlaybackSpeed(speed)
                }
        }
    }

    LaunchedEffect(exoPlayer) {
        if (exoPlayer != null) {
            pendingSeekMs = null
        }
    }

    fun saveCurrent() {
        val player = exoPlayer ?: return
        val r = resolved ?: return
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: r.durationMs
        viewModel.saveProgress(r.mediaId, seriesId, player.currentPosition, duration)
    }

    fun seekBy(deltaMs: Long) {
        val player = exoPlayer ?: return
        val max = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, max)
        player.seekTo(target)
        positionMs = target
    }

    fun togglePlay() {
        val player = exoPlayer ?: return
        if (player.playbackState == Player.STATE_ENDED || ended) {
            player.seekTo(0)
            ended = false
            player.play()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        interaction++
    }

    fun switchTo(targetIndex: Int) {
        val b = bundle ?: return
        val episode = b.episodes.getOrNull(targetIndex) ?: return
        saveCurrent()
        exoPlayer?.pause()
        scope.launch {
            loading = true
            error = null
            showEpisodes = false
            try {
                val next = viewModel.resolveEpisode(episode.id)
                currentIndex = targetIndex
                resolved = next
                ended = false
                selectedSubtitle = null
                manualSubtitle = null
                pendingSeekMs = null
                overlayVisible = true
                interaction++
            } catch (e: Exception) {
                error = e.message ?: "切换集数失败"
            } finally {
                loading = false
            }
        }
    }

    fun showControls() {
        overlayVisible = true
        interaction++
    }

    fun runSubtitleSearch() {
        val ep = bundle?.episodes?.getOrNull(currentIndex)
        val seriesTitle = bundle?.series?.displayTitle.orEmpty()
        val primary = onlineQuery.trim().ifBlank { ep?.title.orEmpty() }
        scope.launch {
            onlineLoading = true
            onlineError = null
            try {
                val queries = listOf(primary, subtitleSeriesAlias(seriesTitle))
                    .filter { it.isNotBlank() }
                    .distinct()
                var found = emptyList<OnlineSubtitle>()
                for (query in queries) {
                    found = graph.subtitles.search(query, ep?.season ?: 0, ep?.episode ?: 0)
                    if (found.isNotEmpty()) break
                }
                onlineResults = found
                showOnlineSearch = false
                showOnlineResults = true
                if (found.isEmpty()) {
                    onlineError = "没有找到本集字幕；已自动尝试剧名。可以手动输入英文原名再搜。"
                }
            } catch (e: Exception) {
                onlineError = e.message ?: "在线字幕搜索失败"
                showOnlineResults = true
            } finally {
                onlineLoading = false
            }
        }
    }

    fun loadOnlineSubtitle(sub: OnlineSubtitle) {
        scope.launch {
            onlineLoading = true
            onlineError = null
            try {
                val bytes = graph.subtitles.download(sub.downloadUrl)
                val dir = File(context.filesDir, "subtitles").apply { mkdirs() }
                val ext = sub.format.lowercase().let { if (it in setOf("srt", "ass", "ssa", "vtt")) it else "srt" }
                val file = File(dir, "online-${sub.id}.$ext")
                file.writeBytes(bytes)
                pendingSeekMs = positionMs
                manualSubtitle = SubtitleSpec(Uri.fromFile(file), sub.label, subtitleMime(file.name), languageCode(sub.language))
                selectedSubtitle = sub.label
                showOnlineResults = false
                returnToSubtitleControl()
                toast = "已加载 ${sub.label}"
            } catch (e: Exception) {
                onlineError = e.message ?: "字幕下载失败"
                toast = onlineError
            } finally {
                onlineLoading = false
            }
        }
    }

    fun hideControls() {
        overlayVisible = false
        showEpisodes = false
        showSubtitles = false
        showSpeed = false
        showAspect = false
        showSubtitleStyle = false
        showOnlineSearch = false
        showOnlineResults = false
        showLocalBrowser = false
        rootFocus.requestFocus()
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer
        if (player == null) {
            onDispose { }
        } else {
            var autoSubtitleApplied = false
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
                            val r = resolved
                            if (r != null && durationMs > 0) {
                                viewModel.updateDuration(r.mediaId, durationMs)
                            }
                        }
                        Player.STATE_ENDED -> {
                            ended = true
                            overlayVisible = true
                            interaction++
                            val currentResolved = resolved
                            if (currentResolved != null && durationMs > 0) {
                                viewModel.saveProgress(
                                    currentResolved.mediaId,
                                    seriesId,
                                    durationMs,
                                    durationMs
                                )
                            }
                            val b = bundle
                            val canNext = autoNextLatest && b != null && currentIndex < b.episodes.lastIndex
                            if (canNext) {
                                switchTo(currentIndex + 1)
                            } else {
                                rootFocus.requestFocus()
                            }
                        }
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (!playing) saveCurrent()
                }

                override fun onPlayerError(e: PlaybackException) {
                    Log.w(
                        "JuyingPlayer",
                        "播放器错误 ${e.errorCodeName}: ${e.message}; host=${resolved?.url?.substringAfter("://")?.substringBefore('/')}",
                        e
                    )
                    error = friendlyPlaybackError(e)
                    loading = false
                }

                override fun onTracksChanged(tracks: Tracks) {
                    val options = buildSubtitleOptions(tracks)
                    subtitleOptions = options
                    val r = resolved
                    if (!autoSubtitleApplied && options.isNotEmpty() &&
                        (r?.subtitles.isNullOrEmpty().not() || manualSubtitle != null)
                    ) {
                        autoSubtitleApplied = true
                        val manual = manualSubtitle
                        val preferred = manual?.let { m -> options.firstOrNull { it.label == m.label } }
                            ?: options.firstOrNull {
                                it.language?.startsWith("zh") == true ||
                                    it.label.contains("简") ||
                                    it.label.contains("繁")
                            }
                            ?: options.first()
                        applySubtitle(player, preferred)
                        selectedSubtitle = preferred.label
                    }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                saveCurrent()
                player.release()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val player = exoPlayer
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveCurrent()
                player?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(exoPlayer, resolved?.mediaId, overlayVisible, isPlaying) {
        val player = exoPlayer ?: return@LaunchedEffect
        positionMs = player.currentPosition
        durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: durationMs
        if (overlayVisible && isPlaying) {
            while (isActive) {
                positionMs = player.currentPosition
                delay(500)
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        if (exoPlayer != null) {
            while (isActive) {
                delay(5_000)
                saveCurrent()
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        if (exoPlayer != null) {
            exoPlayer.setPlaybackSpeed(speed)
            delay(220)
            runCatching { playFocus.requestFocus() }
        }
    }

    LaunchedEffect(overlayVisible, showEpisodes, showSubtitles, showSpeed, showAspect) {
        delay(100)
        when {
            !overlayVisible -> runCatching { rootFocus.requestFocus() }
            !showEpisodes && !showSubtitles && !showSpeed && !showAspect ->
                runCatching { playFocus.requestFocus() }
        }
    }

    LaunchedEffect(overlayVisible, interaction, showEpisodes, showSubtitles, showSpeed, showAspect, showSubtitleStyle, showOnlineSearch, showOnlineResults, showLocalBrowser, isPlaying) {
        if (overlayVisible && isPlaying && !showEpisodes && !showSubtitles && !showSpeed && !showAspect &&
            !showSubtitleStyle && !showOnlineSearch && !showOnlineResults && !showLocalBrowser
        ) {
            delay(6_000)
            hideControls()
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_400)
            toast = null
        }
    }

    BackHandler {
        when {
            resolved == null || error != null -> {
                saveCurrent()
                onBack()
            }
            showEpisodes -> showEpisodes = false
            showSubtitles -> showSubtitles = false
            showSpeed -> showSpeed = false
            showAspect -> showAspect = false
            showSubtitleStyle -> { showSubtitleStyle = false; returnToSubtitleControl() }
            showOnlineSearch -> { showOnlineSearch = false; returnToSubtitleControl() }
            showOnlineResults -> { showOnlineResults = false; returnToSubtitleControl() }
            showLocalBrowser -> { showLocalBrowser = false; returnToSubtitleControl() }
            overlayVisible -> hideControls()
            else -> {
                saveCurrent()
                onBack()
            }
        }
    }

    val onKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            interaction++
            val code = event.nativeKeyEvent.keyCode
            when (code) {
                AndroidKeyEvent.KEYCODE_BACK -> {
                    when {
                        showEpisodes -> showEpisodes = false
                        showSubtitles -> showSubtitles = false
                        showSpeed -> showSpeed = false
                        showAspect -> showAspect = false
                        showSubtitleStyle -> { showSubtitleStyle = false; returnToSubtitleControl() }
                        showOnlineSearch -> { showOnlineSearch = false; returnToSubtitleControl() }
                        showOnlineResults -> { showOnlineResults = false; returnToSubtitleControl() }
                        showLocalBrowser -> { showLocalBrowser = false; returnToSubtitleControl() }
                        overlayVisible -> hideControls()
                        else -> {
                            saveCurrent()
                            onBack()
                        }
                    }
                    true
                }
                AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                AndroidKeyEvent.KEYCODE_HEADSETHOOK -> {
                    togglePlay()
                    true
                }
                AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> {
                    if (currentIndex < (bundle?.episodes?.lastIndex ?: -1)) switchTo(currentIndex + 1)
                    true
                }
                AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    if (currentIndex > 0) switchTo(currentIndex - 1)
                    true
                }
                AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    seekBy(10_000)
                    true
                }
                AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                    seekBy(-10_000)
                    true
                }
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!overlayVisible) {
                        seekBy(-10_000)
                        true
                    } else false
                }
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!overlayVisible) {
                        seekBy(10_000)
                        true
                    } else false
                }
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (!overlayVisible) {
                        showControls()
                        true
                    } else false
                }
                AndroidKeyEvent.KEYCODE_DPAD_UP,
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!overlayVisible) {
                        showControls()
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent(onKey)
            .focusable()
    ) {
        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        controllerAutoShow = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        this.player = exoPlayer
                        resizeMode = playerResizeMode
                        keepScreenOn = true
                        isFocusable = false
                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        applySubtitleStyle(this, subtitleSize, subtitleBottom, subtitleFont)
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) view.player = exoPlayer
                    view.resizeMode = playerResizeMode
                    view.keepScreenOn = true
                    applySubtitleStyle(view, subtitleSize, subtitleBottom, subtitleFont)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (loading) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = JuyingColors.Gold,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("正在打开…", color = JuyingColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        error?.let { message ->
            PlayerErrorPane(
                message = message,
                onRetry = {
                    reload++
                    error = null
                },
                onBack = {
                    saveCurrent()
                    onBack()
                }
            )
        }

        if (error == null && resolved != null) {
            AnimatedVisibility(
                visible = overlayVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PlayerControlsOverlay(
                    bundle = bundle,
                    currentIndex = currentIndex,
                    isPlaying = isPlaying,
                    ended = ended,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    progressFraction = progressFraction,
                    kidsMode = kidsMode,
                    playFocus = playFocus,
                    subtitleFocus = subtitleFocus,
                    onTogglePlay = { togglePlay() },
                    onSeekBy = { seekBy(it) },
                    onPrev = { if (currentIndex > 0) switchTo(currentIndex - 1) },
                    onNext = { if (currentIndex < (bundle?.episodes?.lastIndex ?: -1)) switchTo(currentIndex + 1) },
                    onEpisodes = { showEpisodes = true; showSubtitles = false; showSpeed = false; showAspect = false },
                    onSubtitles = { showSubtitles = true; showEpisodes = false; showSpeed = false; showAspect = false },
                    onSpeed = { showSpeed = true; showEpisodes = false; showSubtitles = false; showAspect = false },
                    onAspect = { showAspect = true; showEpisodes = false; showSubtitles = false; showSpeed = false },
                    onBack = {
                        saveCurrent()
                        onBack()
                    }
                )
            }

            if (showEpisodes) {
                EpisodePanel(
                    bundle = bundle,
                    currentIndex = currentIndex,
                    loadThumbnail = { mediaId -> viewModel.episodeThumbnail(mediaId) },
                    onSelect = { switchTo(it) },
                    onClose = { showEpisodes = false }
                )
            }

            if (showSubtitles) {
                PlayerMenu(
                    title = "字幕",
                    options = buildList {
                        add(
                            MenuOption(
                                label = "关闭字幕",
                                selected = selectedSubtitle == null,
                                onClick = {
                                    exoPlayer?.let {
                                        it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build()
                                    }
                                    selectedSubtitle = null
                                    showSubtitles = false
                                    returnToSubtitleControl()
                                }
                            )
                        )
                        subtitleOptions.forEach { option ->
                            add(
                                MenuOption(
                                    label = option.label,
                                    selected = selectedSubtitle == option.label,
                                    onClick = {
                                        exoPlayer?.let { applySubtitle(it, option) }
                                        selectedSubtitle = option.label
                                        showSubtitles = false
                                        returnToSubtitleControl()
                                    }
                                )
                            )
                        }
                        if (manualSubtitle != null) {
                            add(
                                MenuOption(
                                    label = "移除本地字幕",
                                    selected = false,
                                    onClick = {
                                        pendingSeekMs = positionMs
                                        manualSubtitle = null
                                        selectedSubtitle = null
                                        showSubtitles = false
                                        returnToSubtitleControl()
                                        toast = "已移除本地字幕"
                                    }
                                )
                            )
                        }
                        add(
                            MenuOption("加载本地字幕", false) {
                                showSubtitles = false
                                openLocalSubtitleBrowser()
                            }
                        )
                        add(
                            MenuOption("搜索在线字幕", false) {
                                val ep = bundle?.episodes?.getOrNull(currentIndex)
                                onlineQuery = subtitleSearchQuery(
                                    bundle?.series?.displayTitle.orEmpty(),
                                    ep?.title.orEmpty()
                                )
                                onlineResults = emptyList()
                                onlineError = null
                                showSubtitles = false
                                showOnlineSearch = true
                            }
                        )
                        add(
                            MenuOption("字幕设置", false) {
                                showSubtitles = false
                                showSubtitleStyle = true
                            }
                        )
                    },
                    onClose = {
                        showSubtitles = false
                        returnToSubtitleControl()
                    }
                )
            }

            if (showSubtitleStyle) {
                SubtitleStylePanel(
                    sizeSp = subtitleSize,
                    bottomPercent = subtitleBottom,
                    font = subtitleFont,
                    onSize = viewModel::setSubtitleSize,
                    onBottom = viewModel::setSubtitleBottom,
                    onFont = viewModel::setSubtitleFont,
                    onClose = {
                        showSubtitleStyle = false
                        returnToSubtitleControl()
                    }
                )
            }

            if (showOnlineSearch) {
                OnlineSubtitleSearchDialog(
                    query = onlineQuery,
                    loading = onlineLoading,
                    onQueryChange = { onlineQuery = it },
                    onSearch = { runSubtitleSearch() },
                    onDismiss = {
                        showOnlineSearch = false
                        returnToSubtitleControl()
                    }
                )
            }

            if (showOnlineResults) {
                OnlineSubtitleResultPanel(
                    results = onlineResults,
                    loading = onlineLoading,
                    error = onlineError,
                    onPick = { loadOnlineSubtitle(it) },
                    onClose = {
                        showOnlineResults = false
                        returnToSubtitleControl()
                    },
                    onSearchAgain = {
                        showOnlineResults = false
                        showOnlineSearch = true
                    }
                )
            }

            if (showLocalBrowser) {
                LocalSubtitleBrowserPanel(
                    onPick = ::pickLocalSubtitle,
                    onClose = {
                        showLocalBrowser = false
                        returnToSubtitleControl()
                    }
                )
            }

            if (showSpeed) {
                val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                PlayerMenu(
                    title = "播放速度",
                    options = speeds.map { value ->
                        MenuOption(
                            label = if (value == 1f) "正常" else formatSpeed(value),
                            selected = speed == value,
                            onClick = {
                                speed = value
                                exoPlayer?.setPlaybackSpeed(value)
                                showSpeed = false
                            }
                        )
                    },
                    onClose = { showSpeed = false }
                )
            }

            if (showAspect) {
                val modes = listOf(
                    Triple("适应屏幕", AspectRatioFrameLayout.RESIZE_MODE_FIT, "保持比例"),
                    Triple("裁切填充", AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "去黑边"),
                    Triple("拉伸铺满", AspectRatioFrameLayout.RESIZE_MODE_FILL, "拉伸画面"),
                    Triple("原始尺寸", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, "按视频像素")
                )
                PlayerMenu(
                    title = "画面比例",
                    options = modes.map { (label, mode, _) ->
                        MenuOption(
                            label = label,
                            selected = playerResizeMode == mode,
                            onClick = {
                                playerResizeMode = mode
                                showAspect = false
                            }
                        )
                    },
                    onClose = { showAspect = false }
                )
            }
        }

        toast?.let { text ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(text, color = JuyingColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PlayerControlsOverlay(
    bundle: PlaybackBundle?,
    currentIndex: Int,
    isPlaying: Boolean,
    ended: Boolean,
    positionMs: Long,
    durationMs: Long,
    progressFraction: Float,
    kidsMode: Boolean,
    playFocus: FocusRequester,
    subtitleFocus: FocusRequester,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEpisodes: () -> Unit,
    onSubtitles: () -> Unit,
    onSpeed: () -> Unit,
    onAspect: () -> Unit,
    onBack: () -> Unit
) {
    val series = bundle?.series
    val episode = bundle?.episodes?.getOrNull(currentIndex)
    val multiSeason = remember(bundle) {
        bundle?.episodes.orEmpty().map { it.season }.distinct().size > 1
    }
    val episodeLabel = when {
        series == null || episode == null -> ""
        series.kind == SeriesKind.MOVIE -> "电影"
        else -> episodeLabel(episode, false, multiSeason)
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 36.dp, vertical = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    series?.displayTitle ?: "正在播放",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (kidsMode) {
                    Spacer(Modifier.width(10.dp))
                    Badge("儿童模式", JuyingColors.KidsAccent)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(
                    episodeLabel.takeIf { it.isNotBlank() },
                    episode?.title?.takeIf { it.isNotBlank() && it != series?.displayTitle }
                ).joinToString(" · "),
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (positionMs <= 0) "00:00" else formatDuration(positionMs),
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(64.dp)
                )
                ProgressLine(progressFraction, Modifier.weight(1f))
                Text(
                    formatDuration(durationMs),
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 14.dp).width(64.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerControl(
                    icon = Icons.Rounded.PlaylistPlay,
                    label = "选集",
                    onClick = onEpisodes
                )
                Spacer(Modifier.width(18.dp))
                PlayerControl(
                    icon = Icons.Rounded.SkipPrevious,
                    label = "上一集",
                    onClick = onPrev
                )
                Spacer(Modifier.width(18.dp))
                PlayerControl(
                    icon = Icons.Rounded.Replay10,
                    label = "后退 10 秒",
                    onClick = { onSeekBy(-10_000) }
                )
                Spacer(Modifier.width(22.dp))
                PlayerControl(
                    icon = if (isPlaying && !ended) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    label = if (isPlaying && !ended) "暂停" else "播放",
                    onClick = onTogglePlay,
                    large = true,
                    focusRequester = playFocus
                )
                Spacer(Modifier.width(22.dp))
                PlayerControl(
                    icon = Icons.Rounded.Forward10,
                    label = "前进 10 秒",
                    onClick = { onSeekBy(10_000) }
                )
                Spacer(Modifier.width(18.dp))
                PlayerControl(
                    icon = Icons.Rounded.SkipNext,
                    label = "下一集",
                    onClick = onNext
                )
                Spacer(Modifier.width(18.dp))
                PlayerControl(
                    icon = Icons.Rounded.Subtitles,
                    label = "字幕",
                    onClick = onSubtitles,
                    focusRequester = subtitleFocus
                )
                Spacer(Modifier.width(18.dp))
                PlayerControl(
                    icon = Icons.Rounded.Speed,
                    label = "倍速",
                    onClick = onSpeed
                )
                Spacer(Modifier.width(18.dp))
                PlayerControl(
                    icon = Icons.Rounded.AspectRatio,
                    label = "画面",
                    onClick = onAspect
                )
            }
        }
    }
}

@Composable
private fun PlayerControl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    large: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    val size = if (large) 62.dp else 46.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            shape = CircleShape,
            focusedScale = 1.09f,
            backgroundColor = if (large) JuyingColors.Gold.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.42f),
            focusedBackgroundColor = if (large) JuyingColors.GoldBright else JuyingColors.Gold,
            borderColor = if (large) Color.Transparent else Color.White.copy(alpha = 0.22f),
            focusedBorderColor = Color.White
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (large) JuyingColors.OnGold else Color.White,
                modifier = Modifier.align(Alignment.Center).size(if (large) 28.dp else 20.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun EpisodePanel(
    bundle: PlaybackBundle?,
    currentIndex: Int,
    loadThumbnail: suspend (String) -> File?,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    val episodes = bundle?.episodes.orEmpty()
    val multiSeason = remember(episodes) { episodes.map { it.season }.distinct().size > 1 }
    val firstFocus = remember { FocusRequester() }
    val state = rememberTvLazyGridState()

    LaunchedEffect(currentIndex) {
        var waited = 0
        while (state.layoutInfo.totalItemsCount == 0 && waited < 1500) {
            delay(50)
            waited += 50
        }
        runCatching { state.scrollToItem((currentIndex / 2) * 2) }
        delay(80)
        runCatching { firstFocus.requestFocus() }
    }

    BackHandler { onClose() }

    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.42f))
        )
        Column(
            Modifier
                .width(510.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xF00B121D), Color(0xFF070B12))
                    )
                )
                .padding(horizontal = 18.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("选集", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${episodes.size} 集 · 选一集继续看",
                        color = JuyingColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TvButton("关闭", onClose, primary = false)
            }
            Spacer(Modifier.height(12.dp))
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(2),
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(episodes, key = { _, it -> it.id }) { index, item ->
                    val label = episodeLabel(
                        item,
                        bundle?.series?.kind == SeriesKind.MOVIE,
                        multiSeason
                    )
                    EpisodePanelCard(
                        media = item,
                        label = label,
                        current = index == currentIndex,
                        loadThumbnail = loadThumbnail,
                        onClick = { onSelect(index) },
                        modifier = if (index == currentIndex) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodePanelCard(
    media: MediaEntity,
    label: String,
    current: Boolean,
    loadThumbnail: suspend (String) -> File?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thumb by remember(media.id) { mutableStateOf<File?>(null) }
    LaunchedEffect(media.id) {
        thumb = runCatching { loadThumbnail(media.id) }.getOrNull()
    }
    Column(modifier.fillMaxWidth()) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = RoundedCornerShape(12.dp),
            focusedScale = 1.045f,
            backgroundColor = if (current) JuyingColors.Gold.copy(alpha = 0.14f) else JuyingColors.Surface.copy(alpha = 0.4f),
            focusedBackgroundColor = JuyingColors.SurfaceFocused,
            borderColor = if (current) JuyingColors.Gold.copy(alpha = 0.72f) else JuyingColors.Outline.copy(alpha = 0.45f)
        ) {
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF22344F), Color(0xFF101B2A))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            if (current) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(JuyingColors.Gold.copy(alpha = 0.92f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        "播放中",
                        color = JuyingColors.OnGold,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            media.title,
            color = if (current) JuyingColors.Gold else JuyingColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun subtitleTypeface(font: String): Typeface = when (font) {
    "sans" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
    "serif" -> Typeface.SERIF
    "mono" -> Typeface.MONOSPACE
    "bold" -> Typeface.DEFAULT_BOLD
    else -> Typeface.DEFAULT
}

private fun applySubtitleStyle(view: PlayerView, sizeSp: Int, bottomPercent: Int, font: String) {
    view.subtitleView?.setStyle(
        CaptionStyleCompat(
            AndroidColor.WHITE,
            AndroidColor.TRANSPARENT,
            AndroidColor.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            AndroidColor.BLACK,
            subtitleTypeface(font)
        )
    )
    view.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
    view.subtitleView?.setBottomPaddingFraction(bottomPercent / 100f)
}

@Composable
private fun SubtitleStylePanel(
    sizeSp: Int,
    bottomPercent: Int,
    font: String,
    onSize: (Int) -> Unit,
    onBottom: (Int) -> Unit,
    onFont: (String) -> Unit,
    onClose: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 36.dp, bottom = 110.dp)
                .width(640.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF0101A28))
                .border(1.dp, JuyingColors.Outline, RoundedCornerShape(18.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("字幕设置", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.weight(1f))
                TvButton("关闭", onClose, primary = false)
            }
            Spacer(Modifier.height(16.dp))
            Text("字号", color = JuyingColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val sizes = listOf(20, 24, 28, 32, 36)
                val firstSize = if (sizeSp in sizes) sizeSp else sizes.first()
                sizes.forEach { size ->
                    TvChip(
                        text = "${size}",
                        selected = sizeSp == size,
                        onClick = { onSize(size) },
                        focusRequester = if (size == firstSize) firstFocus else null
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("位置", color = JuyingColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8 to "低", 14 to "偏低", 20 to "标准", 28 to "偏高", 36 to "高").forEach { (value, label) ->
                    TvChip(text = label, selected = bottomPercent == value, onClick = { onBottom(value) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("字体", color = JuyingColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "default" to "默认",
                    "sans" to "黑体",
                    "serif" to "宋体",
                    "mono" to "等宽",
                    "bold" to "粗黑"
                ).forEach { (value, label) ->
                    TvChip(text = label, selected = font == value, onClick = { onFont(value) })
                }
            }
        }
    }
}

@Composable
private fun OnlineSubtitleSearchDialog(
    query: String,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    var field by remember {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }
    LaunchedEffect(query) {
        if (field.text != query) {
            field = TextFieldValue(query, selection = TextRange(query.length))
        }
    }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        // 先把焦点放在“搜索”上，避免一进弹窗就弹键盘；需要改词时向上进入输入框。
        runCatching { searchFocus.requestFocus() }
    }
    TvDialog(onDismiss = onDismiss, modifier = Modifier.width(760.dp)) {
        Text("搜索在线字幕", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "默认用本集标题；搜不到会自动尝试剧名英文名。可以按“清空”后输入英文原名。",
            color = JuyingColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(18.dp))
        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                onQueryChange(it.text)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            cursorBrush = SolidColor(JuyingColors.Gold),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JuyingColors.Surface)
                .border(1.dp, JuyingColors.Outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TvButton(
                "清空",
                {
                    field = TextFieldValue("")
                    onQueryChange("")
                },
                primary = false
            )
            Spacer(Modifier.width(10.dp))
            TvButton("取消", onDismiss, primary = false)
            Spacer(Modifier.width(10.dp))
            TvButton(
                if (loading) "搜索中…" else "搜索",
                onSearch,
                primary = true,
                focusRequester = searchFocus,
                enabled = !loading
            )
        }
    }
}

@Composable
private fun LocalSubtitleBrowserPanel(
    onPick: (File) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    val usbRoot = File("/storage")
    val roots = remember {
        listOf(
            "下载" to download,
            "影片" to movies,
            "文档" to documents,
            "U盘" to usbRoot
        ).filter { it.second.exists() }
    }
    var currentDir by remember { mutableStateOf(roots.firstOrNull()?.second ?: usbRoot) }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    val firstFocus = remember { FocusRequester() }
    val state = rememberTvLazyListState()

    LaunchedEffect(currentDir) {
        entries = withContext(Dispatchers.IO) {
            runCatching {
                currentDir.listFiles()
                    ?.asSequence()
                    ?.filter { it.isDirectory || it.extension.lowercase() in setOf("srt", "ass", "ssa", "vtt") }
                    ?.filter { it.name != "emulated" && it.name != "self" }
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    ?.take(500)
                    ?.toList()
                    .orEmpty()
            }.getOrDefault(emptyList())
        }
        delay(80)
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler { onClose() }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)))
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 36.dp, bottom = 80.dp)
                .width(760.dp)
                .fillMaxHeight(0.78f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF0101A28))
                .border(1.dp, JuyingColors.Outline, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("本地字幕", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        currentDir.absolutePath,
                        color = JuyingColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TvButton(
                    "关闭",
                    onClose,
                    primary = false,
                    focusRequester = if (entries.isEmpty()) firstFocus else null
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                roots.forEach { (label, dir) ->
                    TvChip(text = label, selected = currentDir == dir, onClick = { currentDir = dir })
                }
            }
            Spacer(Modifier.height(10.dp))
            val parent = currentDir.parentFile
            if (parent != null && currentDir != usbRoot) {
                FocusableSurface(
                    onClick = { currentDir = parent },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(9.dp),
                    backgroundColor = JuyingColors.Surface.copy(alpha = 0.35f),
                    focusedBackgroundColor = JuyingColors.SurfaceFocused,
                    borderColor = JuyingColors.Outline.copy(alpha = 0.5f)
                ) {
                    Text(
                        "返回上一级",
                        color = JuyingColors.Gold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            if (entries.isEmpty()) {
                Text(
                    "这个文件夹里没有字幕文件。可以把 .srt/.ass 放到 U 盘或“下载”文件夹。",
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                TvLazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(entries, key = { it.absolutePath }) { file ->
                        FocusableSurface(
                            onClick = {
                                if (file.isDirectory) currentDir = file else onPick(file)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (file.absolutePath == entries.firstOrNull()?.absolutePath) {
                                        Modifier.focusRequester(firstFocus)
                                    } else {
                                        Modifier
                                    }
                                ),
                            shape = RoundedCornerShape(9.dp),
                            backgroundColor = if (file.isDirectory) JuyingColors.Surface.copy(alpha = 0.3f) else JuyingColors.Surface.copy(alpha = 0.45f),
                            focusedBackgroundColor = JuyingColors.SurfaceFocused,
                            borderColor = JuyingColors.Outline.copy(alpha = 0.45f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (file.isDirectory) "目录" else file.extension.uppercase(),
                                    color = if (file.isDirectory) JuyingColors.Gold else JuyingColors.Blue,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(52.dp)
                                )
                                Text(
                                    file.name + if (file.isDirectory) "/" else "",
                                    color = JuyingColors.TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!file.isDirectory) {
                                    Text(
                                        "${(file.length() / 1024).coerceAtLeast(1)} KB",
                                        color = JuyingColors.TextMuted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineSubtitleResultPanel(
    results: List<OnlineSubtitle>,
    loading: Boolean,
    error: String?,
    onPick: (OnlineSubtitle) -> Unit,
    onClose: () -> Unit,
    onSearchAgain: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    val state = rememberTvLazyListState()
    LaunchedEffect(results) {
        delay(100)
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 36.dp, bottom = 110.dp)
                .width(620.dp)
                .fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF0101A28))
                .border(1.dp, JuyingColors.Outline, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("在线字幕", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (loading) "正在处理…" else "选择字幕后会自动加载",
                        color = JuyingColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TvButton("关闭", onClose, primary = false)
            }
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = JuyingColors.Red, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }
            if (results.isEmpty() && !loading) {
                Spacer(Modifier.height(18.dp))
                TvButton("换个关键词", onSearchAgain, primary = true)
            } else {
                Spacer(Modifier.height(12.dp))
                TvLazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(results, key = { it.id }) { sub ->
                        FocusableSurface(
                            onClick = { onPick(sub) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (sub === results.firstOrNull()) Modifier.focusRequester(firstFocus) else Modifier),
                            shape = RoundedCornerShape(10.dp),
                            backgroundColor = JuyingColors.Surface.copy(alpha = 0.35f),
                            focusedBackgroundColor = JuyingColors.SurfaceFocused,
                            borderColor = JuyingColors.Outline.copy(alpha = 0.5f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    sub.label,
                                    color = JuyingColors.TextPrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (sub.sizeBytes > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${sub.sizeBytes / 1024} KB",
                                        color = JuyingColors.TextMuted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun subtitleSearchQuery(seriesTitle: String, episodeTitle: String): String =
    episodeTitle.trim().ifBlank { subtitleSeriesAlias(seriesTitle) }

private fun subtitleSeriesAlias(seriesTitle: String): String {
    val lower = seriesTitle.lowercase()
    return when {
        "字母积木" in seriesTitle -> "Alphablocks"
        "数字积木" in seriesTitle -> "Numberblocks"
        "sss" in lower -> "Super Simple Songs"
        "yakka" in lower -> "Yakka Dee"
        "wow" in lower -> "Wow English"
        "steve" in lower -> "Steve and Maggie"
        "小蜘蛛" in seriesTitle -> "Lucas the Spider"
        "didi" in lower -> "DiDi's Day"
        else -> seriesTitle
            .replace(Regex("""\s*[（(]?S\d+(\s*-\s*\d+)?[)）]?\s*$"""), "")
            .trim()
    }
}

private fun languageCode(code: String): String = when (code.lowercase()) {
    "chi", "zho", "zht", "chs", "zh" -> "zh"
    "cht" -> "zh"
    "eng", "en" -> "en"
    "jpn", "ja" -> "ja"
    "kor", "ko" -> "ko"
    else -> "und"
}

private fun subtitleMime(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "ass", "ssa" -> "text/x-ssa"
    "vtt" -> "text/vtt"
    "srt" -> "application/x-subrip"
    else -> "text/plain"
}

private suspend fun copySubtitleToCache(context: android.content.Context, uri: Uri): File? =
    withContext(Dispatchers.IO) {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull().orEmpty()
        val ext = name.substringAfterLast('.', "srt").lowercase()
            .takeIf { it in setOf("srt", "ass", "ssa", "vtt") } ?: "srt"
        val dir = File(context.filesDir, "subtitles").apply { mkdirs() }
        val file = File(dir, "picked-${System.currentTimeMillis()}.$ext")
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!ok || file.length() < 10) {
            file.delete()
            null
        } else {
            file
        }
    }

private fun episodeLabel(media: MediaEntity, movie: Boolean, multiSeason: Boolean = false): String =
    when {
        movie -> "电影"
        multiSeason -> "S${media.season.toString().padStart(2, '0')}E${media.episode.toString().padStart(2, '0')}"
        media.season > 1 -> "S${media.season}E${media.episode}"
        else -> "第 ${media.episode} 集"
    }

@Composable
private fun PlayerMenu(
    title: String,
    options: List<MenuOption>,
    onClose: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 36.dp, bottom = 120.dp)
                .width(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF0101A28))
                .border(1.dp, JuyingColors.Outline, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = JuyingColors.TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭",
                    tint = JuyingColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            options.forEachIndexed { index, option ->
                FocusableSurface(
                    onClick = option.onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    shape = RoundedCornerShape(9.dp),
                    focusedScale = 1.01f,
                    backgroundColor = if (option.selected) JuyingColors.Gold.copy(alpha = 0.12f) else Color.Transparent,
                    focusedBackgroundColor = JuyingColors.SurfaceFocused,
                    borderColor = Color.Transparent,
                    focusedBorderColor = JuyingColors.Gold
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            option.label,
                            color = if (option.selected) JuyingColors.Gold else JuyingColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (option.selected) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(JuyingColors.Gold)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun PlayerErrorPane(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { retryFocus.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(JuyingColors.Red.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = JuyingColors.Red, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("无法播放", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "如果是夸克网盘资源，可能是播放链接过期、夸克对大文件直链的限制，或 Cookie 已失效。点“重试”会重新获取播放地址；仍失败可在设置中检查 Cookie。",
                color = JuyingColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("返回", onBack, primary = false)
                TvButton("重试", onRetry, focusRequester = retryFocus)
            }
        }
    }
}

private fun buildSubtitleOptions(tracks: Tracks): List<SubtitleOption> {
    val result = ArrayList<SubtitleOption>()
    tracks.groups.forEach { group ->
        if (group.type != C.TRACK_TYPE_TEXT) return@forEach
        for (index in 0 until group.length) {
            val format = group.getTrackFormat(index)
            val label = format.label
                ?: format.language
                ?: "字幕 ${result.size + 1}"
            result += SubtitleOption(
                label = label,
                language = format.language,
                group = group,
                trackIndex = index
            )
        }
    }
    return result
}

private fun applySubtitle(player: ExoPlayer, option: SubtitleOption) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
        .build()
}

private fun friendlyPlaybackError(error: PlaybackException): String {
    val detail = error.errorCodeName
    val message = error.message ?: ""
    val httpStatus = generateSequence(error.cause) { it.cause }
        .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()?.responseCode
    return when {
        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (httpStatus) {
            401, 403, 412 ->
                "媒体服务器拒绝访问（HTTP $httpStatus），播放链接或夸克登录状态已过期。请重新进入播放或重新填写 Cookie。"
            404 -> "视频文件不存在（HTTP 404），可能已被移动或删除，请重新扫描媒体源。"
            else -> "媒体服务器返回了错误状态${httpStatus?.let { "（HTTP $it）" } ?: ""}。请检查媒体源地址、账号权限，或重新登录。"
        }
        error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "找不到视频文件，可能已被移动或删除。请重新扫描媒体源。"
        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
            "电视硬件解码器不支持该视频格式，可以尝试更换片源或使用 H.264/AAC 版本。"
        error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT ->
            "连接超时，请检查网络后重试。"
        message.isNotBlank() -> "$message（$detail）"
        else -> "播放器错误：$detail"
    }
}
