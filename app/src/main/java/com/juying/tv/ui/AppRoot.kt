package com.juying.tv.ui

import androidx.activity.compose.BackHandler
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juying.tv.AppViewModel
import com.juying.tv.data.db.SourceType
import com.juying.tv.ui.components.ConfirmDialog
import com.juying.tv.ui.components.PinDialogBody
import com.juying.tv.ui.screens.AddSourceScreen
import com.juying.tv.ui.screens.CollectionDetailScreen
import com.juying.tv.ui.screens.CollectionEditScreen
import com.juying.tv.ui.screens.CollectionPickerScreen
import com.juying.tv.ui.screens.HiddenManageScreen
import com.juying.tv.ui.screens.HomeScreen
import com.juying.tv.ui.screens.KidsManageScreen
import com.juying.tv.ui.screens.LibraryScreen
import com.juying.tv.ui.screens.PlayerScreen
import com.juying.tv.ui.screens.SeriesDetailScreen
import com.juying.tv.ui.screens.SettingsScreen
import com.juying.tv.ui.screens.SourcesScreen
import com.juying.tv.ui.theme.JuyingColors
import kotlinx.coroutines.delay

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val kidsMode by viewModel.kidsMode.collectAsStateWithLifecycle()
    val pinSet by viewModel.pinSet.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    val screens = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = screens.lastOrNull() ?: Screen.Home
    var showEnterKids by remember { mutableStateOf(false) }
    var showExitKids by remember { mutableStateOf(false) }
    var showKidsPin by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var previousKids by remember { mutableStateOf(kidsMode) }

    fun go(screen: Screen) {
        screens.clear()
        screens.add(screen)
    }

    fun push(screen: Screen) {
        if (screens.lastOrNull() != screen) screens.add(screen)
    }

    fun pop() {
        if (screens.size > 1) screens.removeAt(screens.lastIndex)
    }

    LaunchedEffect(kidsMode) {
        if (previousKids != kidsMode) {
            previousKids = kidsMode
            screens.clear()
            screens.add(Screen.Home)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message = it }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3_200)
            message = null
        }
    }
    LaunchedEffect(scanState?.running, scanState?.summary) {
        if (scanState?.running == false) {
            val summary = scanState?.summary
            if (!summary.isNullOrBlank()) {
                message = summary
                if (scanState?.error == null) viewModel.dismissScanBanner()
            }
        }
    }

    BackHandler(enabled = screens.size > 1 && !DialogTracker.isOpen) { pop() }
    BackHandler(enabled = screens.size == 1 && current != Screen.Home && !DialogTracker.isOpen) { go(Screen.Home) }
    BackHandler(enabled = screens.size == 1 && current == Screen.Home && kidsMode && !DialogTracker.isOpen) { showExitKids = true }

    val navKey = when (current) {
        Screen.Home -> NavKey.HOME
        Screen.Library, is Screen.SeriesDetail, is Screen.CollectionDetail,
        is Screen.CollectionEdit, is Screen.CollectionPicker -> NavKey.LIBRARY
        Screen.Sources, is Screen.AddSource -> NavKey.SOURCES
        Screen.Settings, Screen.HiddenManage -> NavKey.SETTINGS
        Screen.KidsManage -> if (kidsMode) NavKey.PARENT else NavKey.KIDS
        else -> NavKey.HOME
    }

    val showRail = current !is Screen.Player

    val coordinator = remember { FocusCoordinator() }
    val screenStateHolder = rememberSaveableStateHolder()
    CompositionLocalProvider(LocalFocusCoordinator provides coordinator) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(JuyingColors.Background, Color(0xFF0A111D), JuyingColors.Background)
                    )
                )
                .onFocusChanged { coordinator.hasFocus = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && !coordinator.hasFocus) {
                        when (event.nativeKeyEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_UP,
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                return@onPreviewKeyEvent coordinator.restore()
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            Row(Modifier.fillMaxSize()) {
            if (showRail) {
                NavRail(
                    current = navKey,
                    kidsMode = kidsMode,
                    onNavigate = { key ->
                        when (key) {
                            NavKey.HOME -> {
                                // 主动点“首页”时回到默认主按钮，不恢复上次浏览位置。
                                NavFocusMemory.homeTarget = null
                                NavFocusMemory.homeSeriesId = null
                                NavFocusMemory.homeMediaId = null
                                NavFocusMemory.homeShelfKey = null
                                go(Screen.Home)
                            }
                            NavKey.LIBRARY -> go(Screen.Library)
                            NavKey.SOURCES -> go(Screen.Sources)
                            NavKey.SETTINGS -> go(Screen.Settings)
                            NavKey.KIDS -> {
                                if (!pinSet) go(Screen.KidsManage) else showEnterKids = true
                            }
                            NavKey.PARENT -> push(Screen.KidsManage)
                            NavKey.EXIT_KIDS -> showExitKids = true
                        }
                    }
                )
            }

            Box(Modifier.weight(1f).fillMaxSize()) {
                val screen = current
                var contentReady by remember(screen) { mutableStateOf(false) }
                LaunchedEffect(screen) { contentReady = true }
                val contentAlpha by animateFloatAsState(
                    targetValue = if (contentReady) 1f else 0f,
                    animationSpec = tween(180),
                    label = "screenContent"
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = contentAlpha
                            translationY = (1f - contentAlpha) * 12f
                        }
                ) {
                screenStateHolder.SaveableStateProvider(screen.saveKey) {
                    when (screen) {
                    Screen.Home -> HomeScreen(
                        viewModel = viewModel,
                        kidsMode = kidsMode,
                        onOpenSeries = { push(Screen.SeriesDetail(it)) },
                        onPlay = { mediaId, seriesId -> push(Screen.Player(mediaId, seriesId)) },
                        onOpenSources = { go(Screen.Sources) },
                        onOpenCollection = { push(Screen.CollectionDetail(it)) },
                        onOpenKidsSettings = { push(Screen.KidsManage) }
                    )
                    Screen.Library -> LibraryScreen(
                        viewModel = viewModel,
                        kidsMode = kidsMode,
                        onOpenSeries = { push(Screen.SeriesDetail(it)) },
                        onOpenCollection = { push(Screen.CollectionDetail(it)) },
                        onEditCollection = { push(Screen.CollectionEdit(it)) },
                        onOpenHidden = { push(Screen.HiddenManage) },
                        onOpenKidsManage = { push(Screen.KidsManage) },
                        onOpenSources = { go(Screen.Sources) }
                    )
                    Screen.Sources -> SourcesScreen(
                        viewModel = viewModel,
                        onOpenAdd = { type, id -> push(Screen.AddSource(type, id)) }
                    )
                    Screen.Settings -> SettingsScreen(
                        viewModel = viewModel,
                        onOpenKidsManage = { push(Screen.KidsManage) },
                        onOpenHidden = { push(Screen.HiddenManage) },
                        onOpenSources = { go(Screen.Sources) }
                    )
                    Screen.HiddenManage -> HiddenManageScreen(
                        viewModel = viewModel,
                        onOpenSeries = { push(Screen.SeriesDetail(it)) },
                        onBack = { pop() }
                    )
                    Screen.KidsManage -> KidsManageScreen(
                        viewModel = viewModel,
                        onBack = { pop() }
                    )
                    is Screen.SeriesDetail -> SeriesDetailScreen(
                        viewModel = viewModel,
                        seriesId = screen.seriesId,
                        kidsMode = kidsMode,
                        onPlay = { mediaId, seriesId, fromStart ->
                            push(Screen.Player(mediaId, seriesId, fromStart))
                        },
                        onBack = { pop() }
                    )
                    is Screen.CollectionDetail -> CollectionDetailScreen(
                        viewModel = viewModel,
                        collectionId = screen.collectionId,
                        kidsMode = kidsMode,
                        onOpenSeries = { push(Screen.SeriesDetail(it)) },
                        onEdit = { push(Screen.CollectionEdit(screen.collectionId)) },
                        onAddContent = { push(Screen.CollectionPicker(screen.collectionId)) },
                        onBack = { pop() }
                    )
                    is Screen.CollectionEdit -> CollectionEditScreen(
                        viewModel = viewModel,
                        collectionId = screen.collectionId,
                        onOpenPicker = { push(Screen.CollectionPicker(screen.collectionId)) },
                        onBack = { pop() }
                    )
                    is Screen.CollectionPicker -> CollectionPickerScreen(
                        viewModel = viewModel,
                        collectionId = screen.collectionId,
                        onDone = { pop() }
                    )
                    is Screen.AddSource -> AddSourceScreen(
                        viewModel = viewModel,
                        type = screen.type,
                        sourceId = screen.sourceId,
                        onBack = { pop() }
                    )
                    is Screen.Player -> PlayerScreen(
                        viewModel = viewModel,
                        mediaId = screen.mediaId,
                        seriesId = screen.seriesId,
                        kidsMode = kidsMode,
                        fromStart = screen.fromStart,
                        onBack = { pop() }
                    )
                }
                }
                }

                if (current !is Screen.Player && scanState?.running == true) {
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 18.dp, end = 24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(JuyingColors.Gold)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "扫描中 · ${scanState?.files ?: 0}",
                            color = JuyingColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                val visibleMessage = message
                if (visibleMessage != null && current !is Screen.Player) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 22.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xEE132033))
                            .padding(horizontal = 22.dp, vertical = 11.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.ChildCare,
                                contentDescription = null,
                                tint = JuyingColors.Gold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                visibleMessage,
                                color = JuyingColors.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                }
            }
        }
    }

    if (showEnterKids) {
        ConfirmDialog(
            title = "进入儿童模式",
            message = "只显示家长允许的内容，并隐藏媒体源与设置。退出时需要家长密码。",
            confirmText = "进入",
            onConfirm = {
                showEnterKids = false
                viewModel.enterKidsMode()
            },
            onDismiss = { showEnterKids = false }
        )
    }

    if (showExitKids) {
        PinDialogBody(
            title = "退出儿童模式",
            subtitle = "请输入家长锁密码",
            verify = { pin -> viewModel.verifyPin(pin) },
            onDismiss = { showExitKids = false },
            onSuccess = {
                showExitKids = false
                viewModel.exitKidsMode()
            }
        )
    }
}
