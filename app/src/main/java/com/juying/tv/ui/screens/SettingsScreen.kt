package com.juying.tv.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import com.juying.tv.ui.RegisterScreenFocus
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.juying.tv.ui.components.TvChip
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juying.tv.AiUiState
import com.juying.tv.AppViewModel
import com.juying.tv.data.ai.AiProviders
import com.juying.tv.BuildConfig
import com.juying.tv.ui.components.Badge
import com.juying.tv.ui.components.ConfirmDialog
import com.juying.tv.ui.components.FocusableSurface
import com.juying.tv.ui.components.PhoneAssistDialog
import com.juying.tv.ui.components.PinDialogBody
import com.juying.tv.ui.components.SetPinDialog
import com.juying.tv.ui.components.ProgressLine
import com.juying.tv.ui.components.SettingRow
import com.juying.tv.ui.components.TvButton
import com.juying.tv.ui.components.TvDialog
import com.juying.tv.ui.components.TogglePill
import com.juying.tv.ui.theme.JuyingColors
import com.juying.tv.ui.util.PhoneField
import com.juying.tv.util.localIpv4Address
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onOpenKidsManage: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenSources: () -> Unit
) {
    val pinSet by viewModel.pinSet.collectAsStateWithLifecycle()
    val kidsMode by viewModel.kidsMode.collectAsStateWithLifecycle()
    val autoNext by viewModel.autoNext.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val scrapeState by viewModel.scrapeState.collectAsStateWithLifecycle()
    val scrapeMeta by viewModel.scrapeMeta.collectAsStateWithLifecycle()
    val mergeDuplicates by viewModel.scrapeMergeDuplicates.collectAsStateWithLifecycle()
    val yearCollections by viewModel.scrapeYearCollections.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val aiKeyConfigured by viewModel.aiKeyConfigured.collectAsStateWithLifecycle()
    val aiKeyLength by viewModel.aiKeyLength.collectAsStateWithLifecycle()
    val aiModel by viewModel.aiModel.collectAsStateWithLifecycle()
    val aiProvider by viewModel.aiProvider.collectAsStateWithLifecycle()
    val aiProviderName by viewModel.aiProviderName.collectAsStateWithLifecycle()
    val aiBaseUrl by viewModel.aiBaseUrl.collectAsStateWithLifecycle()
    val aiModels by viewModel.aiModels.collectAsStateWithLifecycle()
    val aiModelsLoading by viewModel.aiModelsLoading.collectAsStateWithLifecycle()
    val aiModelsError by viewModel.aiModelsError.collectAsStateWithLifecycle()

    var showSetPin by remember { mutableStateOf(false) }
    var showVerifyForChange by remember { mutableStateOf(false) }
    var showClearPin by remember { mutableStateOf(false) }
    var showEnableKids by remember { mutableStateOf(false) }
    var showAiKey by remember { mutableStateOf(false) }
    var showAiPhone by remember { mutableStateOf(false) }
    var showAiProvider by remember { mutableStateOf(false) }
    var showAiModel by remember { mutableStateOf(false) }
    var showAiCustomModel by remember { mutableStateOf(false) }
    var showAiBaseUrl by remember { mutableStateOf(false) }
    var showAiBatch by remember { mutableStateOf(false) }
    var showAiCover by remember { mutableStateOf(false) }
    val aiDialogFocus = remember { FocusRequester() }
    val aiModelFocus = remember { FocusRequester() }
    val aiProviderFocus = remember { FocusRequester() }
    val aiRefreshFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    RegisterScreenFocus(firstFocus)
    val ip = remember { localIpv4Address() ?: "未连接局域网" }

    LaunchedEffect(Unit) {
        delay(180)
        firstFocus.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsItem {
            Column {
                Text(
                    "设置",
                    color = JuyingColors.TextPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "家长锁、播放偏好与媒体库维护",
                    color = JuyingColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        SettingsItem { SectionLabel("家长与儿童") }
        SettingsItem {
            SettingRow(
                title = if (pinSet) "家长锁已启用" else "设置家长锁",
                subtitle = if (pinSet) "进入儿童模式、修改允许内容时需要输入密码" else "建议先设置 4–6 位数字密码，防止孩子退出儿童模式",
                icon = Icons.Rounded.Lock,
                focusRequester = firstFocus,
                modifier = Modifier.focusProperties {
                    up = FocusRequester.Cancel
                },
                onClick = {
                    if (pinSet) showVerifyForChange = true else showSetPin = true
                },
                trailing = {
                    Badge(if (pinSet) "已设置" else "未设置", if (pinSet) JuyingColors.Green else JuyingColors.TextSecondary)
                }
            )
        }
        SettingsItem {
            SettingRow(
                title = "儿童模式内容管理",
                subtitle = "按剧集、按集数选择孩子可以观看的内容，支持全选",
                icon = Icons.Rounded.ChildCare,
                onClick = onOpenKidsManage,
                trailing = {
                    Text("${series.count { it.kidsAllowed }} 部已允许", color = JuyingColors.KidsAccent, style = MaterialTheme.typography.bodySmall)
                }
            )
        }
        SettingsItem {
            SettingRow(
                title = "进入儿童模式",
                subtitle = if (kidsMode) "当前已处于儿童模式" else "隐藏设置与媒体源，只显示家长允许的内容",
                icon = Icons.Rounded.LockOpen,
                onClick = {
                    if (kidsMode) {
                        viewModel.notify("退出儿童模式请使用左侧导航的“退出”")
                    } else if (!pinSet) {
                        viewModel.notify("请先设置家长锁密码")
                        showSetPin = true
                    } else {
                        showEnableKids = true
                    }
                },
                trailing = {
                    if (kidsMode) Badge("已开启", JuyingColors.KidsAccent)
                }
            )
        }
        if (pinSet) {
            SettingsItem {
                SettingRow(
                    title = "关闭并清除家长锁",
                    subtitle = "会同时退出儿童模式并清空家长密码",
                    icon = Icons.Rounded.DeleteForever,
                    onClick = { showClearPin = true }
                )
            }
        }
        SettingsItem { SectionLabel("播放") }
        SettingsItem {
            SettingRow(
                title = "自动播放下一集",
                subtitle = "剧集播放结束后自动进入下一集",
                icon = Icons.Rounded.PlayCircle,
                onClick = { viewModel.setAutoNext(!autoNext) },
                trailing = { TogglePill(autoNext) }
            )
        }
        SettingsItem { SectionLabel("媒体库") }
        SettingsItem {
            SettingRow(
                title = "扫描全部媒体源",
                subtitle = if (scanState?.running == true) "正在扫描 ${scanState?.sourceName} · ${scanState?.files ?: 0} 个文件" else "共 ${sources.size} 个媒体源 · ${series.size} 部内容",
                icon = Icons.Rounded.Refresh,
                onClick = { viewModel.scanAll() }
            )
        }
        SettingsItem {
            SettingRow(
                title = "管理媒体源",
                subtitle = "添加本地目录、U 盘、WebDAV 或夸克网盘",
                icon = Icons.Rounded.Cloud,
                onClick = onOpenSources
            )
        }
        SettingsItem {
            SettingRow(
                title = "已隐藏的内容",
                subtitle = "恢复此前从媒体库隐藏的剧集",
                icon = Icons.Rounded.VisibilityOff,
                onClick = onOpenHidden
            )
        }
        SettingsItem { SectionLabel("刮削与自动整理") }
        scrapeState?.let { state ->
            SettingsItem {
                ScrapeProgressCard(
                    state = state,
                    onCancel = { viewModel.cancelScrape() },
                    onDismiss = { viewModel.dismissScrapeBanner() }
                )
            }
        }
        SettingsItem {
            SettingRow(
                title = "开始刮削（多源）",
                subtitle = if (scrapeState?.running == true) {
                    "正在刮削 ${scrapeState?.index}/${scrapeState?.total} · ${scrapeState?.currentTitle}"
                } else {
                    "豆瓣 / TVmaze / TMDB 自动匹配；带缓存与限流保护。已匹配 ${scrapeMeta.size} 部"
                },
                icon = Icons.Rounded.AutoAwesome,
                onClick = {
                    if (scrapeState?.running == true) viewModel.notify("刮削正在进行中")
                    else viewModel.scrapeAll(false)
                }
            )
        }
        SettingsItem {
            SettingRow(
                title = "重新刮削全部",
                subtitle = "忽略已匹配记录，重新请求全部 ${series.size} 部内容",
                icon = Icons.Rounded.Refresh,
                onClick = {
                    if (scrapeState?.running == true) viewModel.notify("刮削正在进行中")
                    else viewModel.scrapeAll(true)
                }
            )
        }
        SettingsItem {
            SettingRow(
                title = "测试刮削源",
                subtitle = "检查豆瓣、TVmaze 与 TMDB（api.tmdb.org）",
                icon = Icons.Rounded.Cloud,
                onClick = { viewModel.testScrapeSources() }
            )
        }
        SettingsItem {
            SettingRow(
                title = "自动合并同作品重复条目",
                subtitle = "刮削到同一条目时合并重复剧集；建议开启",
                icon = Icons.Rounded.AutoAwesome,
                onClick = { viewModel.setScrapeMergeDuplicates(!mergeDuplicates) },
                trailing = { TogglePill(mergeDuplicates) }
            )
        }
        SettingsItem {
            SettingRow(
                title = "按年份自动建合集",
                subtitle = "刮削匹配后，按首播年份建立“2024 年”等合集",
                icon = Icons.Rounded.AutoAwesome,
                onClick = { viewModel.setScrapeYearCollections(!yearCollections) },
                trailing = { TogglePill(yearCollections) }
            )
        }
        SettingsItem {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(JuyingColors.Surface.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(18.dp)
            ) {
                Text(
                    "豆瓣覆盖中文内容，TVmaze 补充英文动画，TMDB 走国内可访问的 api.tmdb.org（内置演示 Key）。"
                        + "儿童课程、儿歌、字母拼读等会自动跳过；只更新空白标题、简介和年份，结果缓存 7 天，接口限流时自动暂停重试。",
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp
                )
            }
        }
        SettingsItem { SectionLabel("AI 智能整理") }
        SettingsItem {
            SettingRow(
                title = "模型供应商",
                subtitle = "百炼 / OpenAI / DeepSeek / 硅基流动 / OpenRouter / 自建网关",
                icon = Icons.Rounded.Cloud,
                onClick = { showAiProvider = true },
                trailing = { Text(aiProviderName, color = JuyingColors.Gold, style = MaterialTheme.typography.bodySmall) }
            )
        }
        if (aiProvider == "custom") {
            SettingsItem {
                SettingRow(
                    title = "OpenAI 兼容网关地址",
                    subtitle = if (aiBaseUrl.isBlank()) "填写 LiteLLM / New API / One API 等网关的 /v1 地址" else aiBaseUrl,
                    icon = Icons.Rounded.Cloud,
                    onClick = { showAiBaseUrl = true }
                )
            }
        }
        aiState?.let { state ->
            SettingsItem {
                AiProgressCard(
                    state = state,
                    onCancel = { viewModel.cancelAi() },
                    onDismiss = { viewModel.dismissAiBanner() }
                )
            }
        }
        SettingsItem {
            SettingRow(
                title = "百炼 API Key",
                subtitle = if (aiKeyConfigured) {
                    "$aiProviderName · 已填写 $aiKeyLength 个字符，仅保存在电视本机"
                } else {
                    "$aiProviderName · 填写后可用大模型规范文件名、季集和封面"
                },
                icon = Icons.Rounded.AutoAwesome,
                onClick = { if (aiKeyConfigured) showAiKey = true else showAiPhone = true },
                trailing = {
                    Badge(if (aiKeyConfigured) "已设置" else "未设置", if (aiKeyConfigured) JuyingColors.Green else JuyingColors.TextSecondary)
                }
            )
        }
        SettingsItem {
            SettingRow(
                title = "AI 模型",
                subtitle = "从供应商读取最新模型，也可手动输入模型名",
                icon = Icons.Rounded.Cloud,
                onClick = {
                    showAiModel = true
                    viewModel.loadAiModels()
                },
                trailing = { Text(aiModel, color = JuyingColors.Gold, style = MaterialTheme.typography.bodySmall) }
            )
        }
        SettingsItem {
            SettingRow(
                title = "测试 AI 连接",
                subtitle = "发送一条最小请求，不修改媒体库",
                icon = Icons.Rounded.Cloud,
                onClick = { viewModel.testAi() }
            )
        }
        SettingsItem {
            SettingRow(
                title = "AI 整理全部剧集",
                subtitle = "模型读取文件名和目录，重新整理季/集/标题；可能产生费用",
                icon = Icons.Rounded.AutoAwesome,
                onClick = {
                    if (!aiKeyConfigured) viewModel.notify("请先填写百炼 API Key")
                    else if (aiState?.running == true) viewModel.notify("AI 正在整理中")
                    else showAiBatch = true
                }
            )
        }
        SettingsItem {
            SettingRow(
                title = "AI 补齐缺失封面",
                subtitle = "用百炼联网搜索为没有封面的内容找官方海报，建议家长抽查",
                icon = Icons.Rounded.AutoAwesome,
                modifier = Modifier.onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown &&
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                },
                onClick = {
                    if (!aiKeyConfigured) viewModel.notify("请先填写百炼 API Key")
                    else if (aiState?.running == true) viewModel.notify("AI 正在整理中")
                    else showAiCover = true
                }
            )
        }
        SettingsItem {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(JuyingColors.Surface.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(18.dp)
            ) {
                Text(
                    "AI 会读取片名、文件名和所在目录，并发送给阿里云百炼；Key 仅保存在电视本机。" +
                        "整理结果会直接写入媒体库，联网找图可能包含第三方图片，建议家长抽查；调用模型可能产生费用。",
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp
                )
            }
        }
        SettingsItem { SectionLabel("手机辅助填写与隐私") }
        SettingsItem {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(JuyingColors.Surface.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(18.dp)
            ) {
                Row {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.QrCode2,
                        contentDescription = null,
                        tint = JuyingColors.Gold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "电视当前局域网地址：$ip",
                            color = JuyingColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "添加 WebDAV 或夸克网盘时，可以选择「用手机填写」。电视会显示一个局域网地址和二维码，手机连同一个 Wi-Fi 即可提交长地址、密码或 Cookie，不会上传到云端。",
                            color = JuyingColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
        SettingsItem { SectionLabel("关于") }
        SettingsItem {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(JuyingColors.Surface.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(18.dp)
            ) {
                Row {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = JuyingColors.Blue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "聚影 TV ${BuildConfig.VERSION_NAME}",
                            color = JuyingColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "适配 Android 9（API 28）、armeabi-v7a 电视与 1080p 界面。" +
                                "夸克网盘使用非官方接口，可能随官方调整失效；Cookie 等同账号密码，仅保存在本机数据库。" +
                                "WebDAV 若走 HTTP，Basic 认证信息为明文传输，建议只在家庭局域网内使用，或改用 HTTPS。",
                            color = JuyingColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }

    if (showAiPhone) {
        PhoneAssistDialog(
            title = "填写百炼 API Key",
            fields = listOf(
                PhoneField(
                    key = "key",
                    label = "阿里云百炼 API Key",
                    placeholder = "sk-...",
                    password = true,
                    hint = "在阿里云百炼控制台创建 API Key。Key 只保存在电视本机，不会上传到其他服务。"
                )
            ),
            onDismiss = { showAiPhone = false },
            onResult = { values ->
                viewModel.setAiKey(values["key"].orEmpty())
                showAiPhone = false
            }
        )
    }

    if (showAiKey) {
        TvDialog(
            onDismiss = { showAiKey = false },
            modifier = Modifier.width(680.dp)
        ) {
            Text(
                "百炼 API Key",
                color = JuyingColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "已填写 · $aiKeyLength 个字符。Key 仅保存在电视本机，不会上传到其他服务。",
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TvButton("清除", {
                    viewModel.clearAiKey()
                    showAiKey = false
                }, primary = false)
                TvButton("更换 Key", {
                    showAiKey = false
                    showAiPhone = true
                }, focusRequester = aiDialogFocus)
            }
            LaunchedEffect(Unit) {
                delay(120)
                runCatching { aiDialogFocus.requestFocus() }
            }
        }
    }

    if (showAiProvider) {
        TvDialog(
            onDismiss = { showAiProvider = false },
            modifier = Modifier.width(880.dp)
        ) {
            Text(
                "选择模型供应商",
                color = JuyingColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "支持 OpenAI 兼容协议；LiteLLM / New API / One API 等自建网关请选择“自建网关”。",
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AiProviders.presets.forEachIndexed { index, preset ->
                    FocusableSurface(
                        onClick = {
                            viewModel.setAiProvider(preset.id)
                            showAiProvider = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .focusRequester(if (index == 0) aiProviderFocus else FocusRequester()),
                        shape = RoundedCornerShape(10.dp),
                        focusedScale = 1.01f,
                        backgroundColor = if (aiProvider == preset.id) JuyingColors.Gold.copy(alpha = 0.1f) else JuyingColors.Surface.copy(alpha = 0.35f),
                        focusedBackgroundColor = JuyingColors.SurfaceFocused,
                        borderColor = if (aiProvider == preset.id) JuyingColors.Gold.copy(alpha = 0.5f) else JuyingColors.Outline.copy(alpha = 0.4f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(preset.name, color = JuyingColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    preset.baseUrl.ifBlank { "自定义地址" },
                                    color = JuyingColors.TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                            if (aiProvider == preset.id) Badge("当前", JuyingColors.Gold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TvButton("关闭", { showAiProvider = false }, primary = false)
            }
            LaunchedEffect(Unit) {
                delay(120)
                runCatching { aiProviderFocus.requestFocus() }
            }
        }
    }

    if (showAiModel) {
        val presetModels = AiProviders.byId(aiProvider).models
        val modelList = (aiModels.ifEmpty { presetModels }).distinct().take(80)
        TvDialog(
            onDismiss = { showAiModel = false },
            modifier = Modifier.width(860.dp)
        ) {
            Text(
                "选择模型 · $aiProviderName",
                color = JuyingColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "当前：$aiModel。在线列表来自供应商 /models；没有列表时可手动输入。",
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvChip(
                    if (aiModelsLoading) "正在读取…" else "刷新在线模型",
                    onClick = { viewModel.loadAiModels() },
                    focusRequester = if (modelList.isEmpty()) aiRefreshFocus else null
                )
                Spacer(Modifier.width(12.dp))
                if (aiModelsError != null) {
                    Text(aiModelsError!!, color = JuyingColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (modelList.isEmpty() && !aiModelsLoading) {
                Text("没有可显示的模型，请手动输入模型名。", color = JuyingColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modelList.forEachIndexed { index, model ->
                        FocusableSurface(
                            onClick = {
                                viewModel.setAiModel(model)
                                showAiModel = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .focusRequester(if (index == 0) aiModelFocus else FocusRequester()),
                            shape = RoundedCornerShape(8.dp),
                            focusedScale = 1.01f,
                            backgroundColor = if (aiModel == model) JuyingColors.Gold.copy(alpha = 0.1f) else JuyingColors.Surface.copy(alpha = 0.3f),
                            focusedBackgroundColor = JuyingColors.SurfaceFocused
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(model, color = JuyingColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                if (aiModel == model) Badge("当前", JuyingColors.Gold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                TvButton("手动输入模型", {
                    showAiModel = false
                    showAiCustomModel = true
                }, primary = false)
                TvButton("关闭", { showAiModel = false }, primary = false)
            }
            LaunchedEffect(Unit) {
                delay(120)
                runCatching {
                    if (modelList.isEmpty()) aiRefreshFocus.requestFocus() else aiModelFocus.requestFocus()
                }
            }
        }
    }

    if (showAiCustomModel) {
        PhoneAssistDialog(
            title = "自定义模型名",
            fields = listOf(
                PhoneField(
                    key = "model",
                    label = "模型 ID",
                    value = aiModel,
                    placeholder = "例如 qwen-plus 或 gpt-5",
                    hint = "填写供应商控制台里的模型 ID，保存后会用于 AI 整理。"
                )
            ),
            onDismiss = { showAiCustomModel = false },
            onResult = { values ->
                viewModel.setAiModel(values["model"].orEmpty())
                showAiCustomModel = false
            }
        )
    }

    if (showAiBaseUrl) {
        PhoneAssistDialog(
            title = "OpenAI 兼容网关地址",
            fields = listOf(
                PhoneField(
                    key = "baseUrl",
                    label = "Base URL",
                    value = aiBaseUrl,
                    placeholder = "http://192.168.1.10:4000/v1",
                    hint = "例如 LiteLLM / New API / One API 的 /v1 地址，末尾不要带 /chat/completions。"
                )
            ),
            onDismiss = { showAiBaseUrl = false },
            onResult = { values ->
                viewModel.setAiBaseUrl(values["baseUrl"].orEmpty())
                showAiBaseUrl = false
            }
        )
    }

    if (showAiBatch) {
        ConfirmDialog(
            title = "AI 整理全部剧集",
            message = "将调用百炼模型处理 ${series.size} 部内容，重新整理季/集/标题，并为缺失封面找图，可能产生费用。建议先在一个剧集上试用。确定继续吗？",
            confirmText = "开始整理",
            onConfirm = {
                showAiBatch = false
                viewModel.aiOrganizeAll(true)
            },
            onDismiss = { showAiBatch = false }
        )
    }

    if (showAiCover) {
        val missing = series.count { it.coverUrl.isNullOrBlank() }
        ConfirmDialog(
            title = "AI 补齐缺失封面",
            message = "当前有 $missing 部内容缺少封面。将调用百炼联网搜索并下载图片，建议家长抽查结果。确定继续吗？",
            confirmText = "开始找图",
            onConfirm = {
                showAiCover = false
                viewModel.aiFillCovers()
            },
            onDismiss = { showAiCover = false }
        )
    }

    if (showSetPin) {
        SetPinDialog(
            onDismiss = { showSetPin = false },
            onSave = { pin ->
                viewModel.setPin(pin)
                showSetPin = false
            }
        )
    }

    if (showVerifyForChange) {
        PinDialogBody(
            title = "验证旧密码",
            subtitle = "验证通过后可以设置新的家长密码",
            verify = { pin -> viewModel.verifyPin(pin) },
            onDismiss = { showVerifyForChange = false },
            onSuccess = {
                showVerifyForChange = false
                showSetPin = true
            }
        )
    }

    if (showClearPin) {
        ConfirmDialog(
            title = "关闭家长锁",
            message = "将清除家长密码并退出儿童模式。已选择的儿童内容允许列表会保留。确定继续吗？",
            confirmText = "关闭家长锁",
            destructive = true,
            onConfirm = {
                viewModel.clearPin()
                showClearPin = false
            },
            onDismiss = { showClearPin = false }
        )
    }

    if (showEnableKids) {
        ConfirmDialog(
            title = "进入儿童模式",
            message = "进入后只会显示家长允许的内容，媒体源和设置入口会被隐藏。退出时需要家长密码。",
            confirmText = "进入儿童模式",
            onConfirm = {
                showEnableKids = false
                viewModel.enterKidsMode()
            },
            onDismiss = { showEnableKids = false }
        )
    }
}

@Composable
private fun ScrapeProgressCard(
    state: com.juying.tv.ScrapeUiState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val fraction = if (state.total > 0) state.index.toFloat() / state.total else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(JuyingColors.Surface.copy(alpha = 0.4f))
            .border(1.dp, JuyingColors.Outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (state.running) "正在刮削 ${state.index}/${state.total}" else (state.message ?: "刮削结束"),
                color = JuyingColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            if (state.running) {
                Text(
                    state.currentTitle,
                    color = JuyingColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                ProgressLine(fraction, Modifier.fillMaxWidth())
            }
            Text(
                "匹配 ${state.matched} 部" + if (state.failed > 0) " · 失败 ${state.failed}" else "",
                color = JuyingColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.width(14.dp))
        if (state.running) TvChip("取消", onClick = onCancel)
        else TvChip("关闭", onClick = onDismiss)
    }
}

@Composable
private fun AiProgressCard(
    state: AiUiState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val fraction = if (state.total > 0) state.index.toFloat() / state.total else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(JuyingColors.Gold.copy(alpha = 0.08f))
            .border(1.dp, JuyingColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (state.running) "AI 整理中 ${state.index}/${state.total}" else (state.message ?: "AI 整理结束"),
                color = JuyingColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            if (state.running) {
                Text(
                    state.currentTitle,
                    color = JuyingColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                ProgressLine(fraction, Modifier.fillMaxWidth())
            }
            Text(
                "成功 ${state.applied} 部 · 封面 ${state.covers} 部",
                color = JuyingColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.width(14.dp))
        if (state.running) TvChip("取消", onClick = onCancel) else TvChip("关闭", onClick = onDismiss)
    }
}

@Composable
private fun SettingsItem(content: @Composable () -> Unit) {
    content()
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = JuyingColors.TextMuted,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}
