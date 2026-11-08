package com.juying.tv.ui.screens

import com.juying.tv.ui.RegisterScreenFocus
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import com.juying.tv.AppViewModel
import com.juying.tv.data.db.SourceEntity
import com.juying.tv.data.db.SourceType
import com.juying.tv.data.source.QuarkConfig
import com.juying.tv.data.source.QuarkRoot
import com.juying.tv.data.source.SourceConfigs
import com.juying.tv.data.source.WebDavConfig
import com.juying.tv.ui.components.Badge
import com.juying.tv.ui.components.ConfirmDialog
import com.juying.tv.ui.components.EmptyState
import com.juying.tv.ui.components.FocusableSurface
import com.juying.tv.ui.components.PhoneAssistDialog
import com.juying.tv.ui.components.TvButton
import com.juying.tv.ui.components.TvChip
import com.juying.tv.ui.components.TvDialog
import com.juying.tv.ui.components.TvFieldLabel
import com.juying.tv.ui.components.TvPickerField
import com.juying.tv.ui.components.TvTextField
import com.juying.tv.ui.components.focusGlow
import com.juying.tv.ui.theme.JuyingColors
import com.juying.tv.ui.util.PhoneField
import com.juying.tv.util.formatDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SourcesScreen(
    viewModel: AppViewModel,
    onOpenAdd: (String, String?) -> Unit
) {
    val context = LocalContext.current
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    var showTypePicker by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SourceEntity?>(null) }
    val addFocus = remember { FocusRequester() }
    RegisterScreenFocus(addFocus)

    val mediaPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.addMediaStoreSource()
        } else {
            viewModel.notify("需要存储读取权限才能扫描本机与 U 盘视频")
        }
    }
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.addLocalFolder(uri)
        } else {
            viewModel.notify("未选择目录")
        }
    }

    LaunchedEffect(Unit) {
        delay(160)
        runCatching { addFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "媒体源",
                    color = JuyingColors.TextPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "本地目录、U 盘、WebDAV 与夸克网盘都可以接入",
                    color = JuyingColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TvChip("扫描全部", onClick = { viewModel.scanAll() }, icon = Icons.Rounded.Refresh)
            Spacer(Modifier.width(10.dp))
            TvButton(
                "添加媒体源",
                { showTypePicker = true },
                icon = Icons.Rounded.Add,
                focusRequester = addFocus
            )
        }

        val scan = scanState
        if (scan != null && (scan.running || scan.summary != null || scan.error != null)) {
            Spacer(Modifier.height(16.dp))
            ScanBanner(
                scan = scan,
                onCancel = { viewModel.cancelScan() },
                onDismiss = { viewModel.dismissScanBanner() }
            )
        }

        Spacer(Modifier.height(20.dp))

        if (sources.isEmpty()) {
            EmptyState(
                title = "还没有媒体源",
                description = "先接入一个目录。夸克网盘建议使用手机扫码填写 Cookie；也可以把夸克挂载到 AList/OpenList 后用 WebDAV 接入，更稳定。",
                actionText = "添加媒体源",
                action = { showTypePicker = true },
                focusRequester = addFocus
            )
        } else {
            TvLazyColumn(
                state = rememberTvLazyListState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sources, key = { it.id }) { source ->
                    SourceCard(
                        source = source,
                        onScan = { viewModel.scanAll(source.id) },
                        onEdit = { onOpenAdd(source.type, source.id) },
                        onDelete = { deleteTarget = source },
                        onRelink = {
                            if (source.type == SourceType.LOCAL) treeLauncher.launch(null)
                            else onOpenAdd(source.type, source.id)
                        }
                    )
                }
            }
        }
    }

    if (showTypePicker) {
        SourceTypePicker(
            onDismiss = { showTypePicker = false },
            onPick = { type ->
                showTypePicker = false
                when (type) {
                    SourceType.LOCAL -> treeLauncher.launch(null)
                    SourceType.MEDIASTORE -> {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            mediaPermission
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.addMediaStoreSource()
                        else permissionLauncher.launch(mediaPermission)
                    }
                    else -> onOpenAdd(type, null)
                }
            }
        )
    }

    deleteTarget?.let { source ->
        ConfirmDialog(
            title = "移除媒体源",
            message = "确定移除「${source.name}」吗？该源下归集的内容和播放记录会一起从媒体库删除，但不会删除网盘或本地的原文件。",
            confirmText = "移除",
            destructive = true,
            onConfirm = {
                viewModel.deleteSource(source.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun ScanBanner(
    scan: com.juying.tv.ScanUiState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(JuyingColors.Surface.copy(alpha = 0.42f))
            .border(1.dp, JuyingColors.Outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(50))
                .background(if (scan.error != null) JuyingColors.Red else JuyingColors.Gold)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    scan.running -> "正在扫描 ${scan.sourceName}（${scan.index}/${scan.total}）"
                    scan.error != null -> "扫描遇到问题"
                    else -> "扫描已完成"
                },
                color = if (scan.error != null) JuyingColors.Red else JuyingColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                when {
                    scan.running -> "已发现 ${scan.files} 个文件 · ${scan.folder}"
                    scan.error != null -> scan.error
                    else -> scan.summary ?: ""
                },
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (scan.running) {
            TvChip("取消扫描", onClick = onCancel)
        } else {
            TvChip("关闭", onClick = onDismiss)
        }
    }
}

@Composable
private fun SourceCard(
    source: SourceEntity,
    onScan: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRelink: () -> Unit
) {
    val icon = when (source.type) {
        SourceType.LOCAL -> Icons.Rounded.Folder
        SourceType.MEDIASTORE -> Icons.Rounded.Usb
        SourceType.WEBDAV -> Icons.Rounded.Storage
        else -> Icons.Rounded.Cloud
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(JuyingColors.Surface.copy(alpha = 0.34f))
            .border(1.dp, JuyingColors.Outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(JuyingColors.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = JuyingColors.Gold, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        source.name,
                        color = JuyingColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(10.dp))
                    Badge(SourceType.label(source.type), JuyingColors.Blue)
                    if (source.lastError != null) {
                        Spacer(Modifier.width(8.dp))
                        Badge("需要处理", JuyingColors.Red)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "上次扫描：${formatDate(source.lastScanAt)} · 收录 ${source.lastScanCount} 个视频",
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                source.lastError?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        it,
                        color = JuyingColors.Red,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvChip("扫描", onClick = onScan, icon = Icons.Rounded.Refresh)
                if (source.type == SourceType.WEBDAV || source.type == SourceType.QUARK) {
                    TvChip("编辑", onClick = onEdit, icon = Icons.Rounded.Edit)
                }
                if (source.type == SourceType.LOCAL) {
                    TvChip("重新授权", onClick = onRelink)
                }
                TvChip("移除", onClick = onDelete, icon = Icons.Rounded.Delete)
            }
        }
    }
}

@Composable
private fun SourceTypePicker(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        firstFocus.requestFocus()
    }
    val options = listOf(
        SourceOption(SourceType.LOCAL, "本地文件夹", "通过系统目录选择器授权电视内置存储或 U 盘中的文件夹", Icons.Rounded.Folder),
        SourceOption(SourceType.MEDIASTORE, "本机与 U 盘视频", "自动扫描系统媒体库中的视频，无需逐个选择目录", Icons.Rounded.Usb),
        SourceOption(SourceType.WEBDAV, "WebDAV", "连接 NAS、AList/OpenList、群晖、坚果云等支持 WebDAV 的服务", Icons.Rounded.Storage),
        SourceOption(SourceType.QUARK, "夸克网盘", "使用 Cookie 直连夸克网盘（非官方接口，可扫码在手机上填写）", Icons.Rounded.Cloud)
    )
    TvDialog(onDismiss = onDismiss, modifier = Modifier.width(820.dp)) {
        Text(
            "添加媒体源",
            color = JuyingColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "选择资源所在的位置。添加后可随时重新扫描。",
            color = JuyingColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(22.dp))
        options.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { colIndex, option ->
                    FocusableSurface(
                        onClick = { onPick(option.type) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 118.dp)
                            .then(
                                if (rowIndex == 0 && colIndex == 0) Modifier.focusRequester(firstFocus)
                                else Modifier
                            ),
                        shape = RoundedCornerShape(16.dp),
                        focusedScale = 1.02f
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(JuyingColors.SurfaceHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(option.icon, contentDescription = null, tint = JuyingColors.Gold, modifier = Modifier.size(23.dp))
                            }
                            Spacer(Modifier.width(13.dp))
                            Column {
                                Text(option.title, color = JuyingColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    option.description,
                                    color = JuyingColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TvButton("取消", onDismiss, primary = false)
        }
    }
}

private data class SourceOption(
    val type: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
fun AddSourceScreen(
    viewModel: AppViewModel,
    type: String,
    sourceId: String?,
    onBack: () -> Unit
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val existing = remember(sources, sourceId) { sources.firstOrNull { it.id == sourceId } }
    val isEdit = existing != null && sourceId != null
    val isWebDav = type == SourceType.WEBDAV

    var initialized by remember(sourceId) { mutableStateOf(false) }
    var name by remember(sourceId) { mutableStateOf("") }
    var url by remember(sourceId) { mutableStateOf("") }
    var username by remember(sourceId) { mutableStateOf("") }
    var password by remember(sourceId) { mutableStateOf("") }
    var rootPath by remember(sourceId) { mutableStateOf("") }
    var cookie by remember(sourceId) { mutableStateOf("") }
    var selectedRoots by remember(sourceId) { mutableStateOf<List<QuarkRoot>>(emptyList()) }
    var showPhone by remember { mutableStateOf(false) }
    var showQuarkPicker by remember { mutableStateOf(false) }
    var showCookieEditor by remember { mutableStateOf(false) }
    var showQrLogin by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    RegisterScreenFocus(firstFocus)

    LaunchedEffect(existing?.id, type) {
        if (!initialized) {
            if (existing != null) {
                name = existing.name
                if (type == SourceType.WEBDAV) {
                    val config = SourceConfigs.decodeWebDav(existing.config)
                    url = config.url
                    username = config.username
                    password = config.password
                    rootPath = config.rootPath
                } else {
                    val config = SourceConfigs.decodeQuark(existing.config)
                    cookie = config.cookie
                    selectedRoots = if (config.roots.isNotEmpty()) {
                        config.roots
                    } else if (config.rootFid != "0") {
                        listOf(QuarkRoot(config.rootFid, config.rootName.ifBlank { "根目录" }))
                    } else {
                        emptyList()
                    }
                }
            }
            initialized = true
            delay(180)
            firstFocus.requestFocus()
        }
    }

    fun buildWebDav() = WebDavConfig(url.trim(), username.trim(), password, rootPath.trim())

    fun buildQuark(): QuarkConfig {
        val first = selectedRoots.firstOrNull()
        return QuarkConfig(
            cookie = cookie.trim(),
            rootFid = first?.fid ?: "0",
            rootName = first?.name ?: "夸克网盘",
            roots = selectedRoots
        )
    }

    fun openQuarkPicker() {
        if (cookie.isBlank()) {
            testResult = false to "请先填写或扫码填入夸克 Cookie，再浏览网盘目录"
            return
        }
        showQuarkPicker = true
    }

    val scopeSummary = when {
        selectedRoots.isEmpty() -> "整个网盘"
        selectedRoots.size <= 2 -> selectedRoots.joinToString("、") { it.name }
        else -> selectedRoots.take(2).joinToString("、") { it.name } + " 等 ${selectedRoots.size} 个目录"
    }

    fun validate(): String? {
        if (isWebDav) {
            if (!url.trim().startsWith("http://", true) && !url.trim().startsWith("https://", true)) {
                return "WebDAV 地址需要以 http:// 或 https:// 开头"
            }
        } else {
            if (cookie.isBlank()) return "请填写夸克 Cookie，或使用手机扫码填写"
        }
        return null
    }

    fun save() {
        val error = validate()
        if (error != null) {
            testResult = false to error
            return
        }
        if (isWebDav) {
            viewModel.saveWebDavSource(sourceId, name.trim(), buildWebDav())
        } else {
            viewModel.saveQuarkSource(sourceId, name.trim(), buildQuark())
        }
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (isEdit) "编辑" else "添加") + if (isWebDav) " WebDAV 媒体源" else "夸克网盘媒体源",
                    color = JuyingColors.TextPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isWebDav) {
                        "支持 NAS、AList/OpenList、群晖、坚果云等 WebDAV 服务，使用 Basic 认证"
                    } else {
                        "通过 Cookie 直连夸克网盘。Cookie 仅保存在本机数据库中，请勿分享给他人"
                    },
                    color = JuyingColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TvChip("返回", onClick = onBack, icon = Icons.Rounded.ArrowBack)
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp)
        ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "媒体源名称",
                        placeholder = if (isWebDav) "例如：家里的 NAS" else "例如：夸克动画",
                        focusRequester = firstFocus,
                        modifier = Modifier.weight(1f)
                    )
                    if (isWebDav) {
                        TvTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = "WebDAV 地址",
                            placeholder = "http://192.168.1.10:5005/dav",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                            modifier = Modifier.weight(1.4f)
                        )
                    } else {
                        TvPickerField(
                            label = "扫描范围（可多选）",
                            value = scopeSummary,
                            onClick = { openQuarkPicker() },
                            icon = Icons.Rounded.Folder,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (isWebDav) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = "用户名",
                            placeholder = "可留空",
                            modifier = Modifier.weight(1f)
                        )
                        TvTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "密码",
                            placeholder = "Basic Auth 密码",
                            password = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TvTextField(
                        value = rootPath,
                        onValueChange = { rootPath = it },
                        label = "根目录（可选）",
                        placeholder = "例如：/动画 或留空",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
                    )
                } else {
                    Column {
                        TvFieldLabel("夸克 Cookie", focused = false)
                        Spacer(Modifier.height(7.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 58.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(JuyingColors.BackgroundAlt)
                                .border(1.dp, JuyingColors.Outline, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (cookie.isBlank()) "未填写" else "已填写 · ${cookie.length} 个字符",
                                    color = if (cookie.isBlank()) JuyingColors.TextMuted else JuyingColors.TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (cookie.isBlank()) "推荐用手机扫码填写" else "内容已隐藏，只保存在本机",
                                    color = JuyingColors.TextMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (cookie.isNotBlank()) {
                                TvChip("清除", onClick = { cookie = "" })
                                Spacer(Modifier.width(8.dp))
                            }
                            TvChip("手动编辑", onClick = { showCookieEditor = true })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "没有电脑也可以点下方「扫码登录」，用夸克 App 扫描电视上的二维码，Cookie 会自动填入。",
                        color = JuyingColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Column(Modifier.width(330.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusableSurface(
                    onClick = { showPhone = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp),
                    shape = RoundedCornerShape(16.dp),
                    focusedScale = 1.02f,
                    backgroundColor = JuyingColors.Surface.copy(alpha = 0.35f),
                    focusedBackgroundColor = JuyingColors.SurfaceFocused,
                    borderColor = JuyingColors.Outline.copy(alpha = 0.65f),
                    focusedBorderColor = JuyingColors.Gold
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = JuyingColors.Gold, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("用手机填写", color = JuyingColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "长地址、Cookie 用遥控器输入很麻烦。扫码后在手机上填写，电视会立即接收。",
                            color = JuyingColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4
                        )
                    }
                }
                val result = testResult
                if (result != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                (if (result.first) JuyingColors.Green else JuyingColors.Red).copy(alpha = 0.12f)
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            result.second,
                            color = if (result.first) JuyingColors.Green else JuyingColors.Red,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Text(
                    if (isWebDav) {
                        "测试会向服务器发送 PROPFIND 请求验证账号与路径。若使用 AList/OpenList，请先在服务端添加夸克网盘挂载。"
                    } else {
                        "夸克接口为非官方接口，可能随官方调整失效。Cookie 失效时重新扫码填写即可。为获得更稳定的体验，也可以使用 AList/OpenList 的 WebDAV 接入。"
                    },
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("保存并扫描", { save() })
            if (!isWebDav) {
                TvButton(
                    "选择扫描目录",
                    { openQuarkPicker() },
                    primary = false,
                    icon = Icons.Rounded.Folder
                )
                TvButton(
                    "扫码登录",
                    { showQrLogin = true },
                    primary = false,
                    icon = Icons.Rounded.QrCode2
                )
            }
            TvButton(
                if (testing) "测试中…" else "测试连接",
                onClick = {
                    val error = validate()
                    if (error != null) {
                        testResult = false to error
                        return@TvButton
                    }
                    scope.launch {
                        testing = true
                        testResult = null
                        val result = if (isWebDav) viewModel.testWebDav(buildWebDav())
                        else viewModel.testQuark(buildQuark())
                        testResult = result.fold(
                            onSuccess = { true to it },
                            onFailure = { false to (it.message ?: "连接失败") }
                        )
                        testing = false
                    }
                },
                primary = false,
                enabled = !testing
            )
            Spacer(Modifier.weight(1f))
            TvButton("取消", onBack, primary = false)
        }
    }

    if (showPhone) {
        val fields = if (isWebDav) {
            listOf(
                PhoneField("name", "媒体源名称", "例如：家里的 NAS", name),
                PhoneField("url", "WebDAV 地址", "http://192.168.1.10:5005/dav", url),
                PhoneField("username", "用户名", "可留空", username),
                PhoneField("password", "密码", "Basic Auth 密码", "", password = true),
                PhoneField("rootPath", "根目录（可选）", "/动画，或留空", rootPath)
            )
        } else {
            listOf(
                PhoneField("name", "媒体源名称", "例如：夸克动画", name),
                PhoneField(
                    "cookie",
                    "夸克 Cookie",
                    "粘贴完整 Cookie",
                    cookie,
                    multiline = true,
                    hint = "推荐直接点电视上的「扫码登录」自动获取；如需手动填写，可登录夸克网页版后复制 Cookie。Cookie 等同于账号密码，请勿分享。"
                )
            )
        }
        PhoneAssistDialog(
            title = if (isWebDav) "填写 WebDAV 信息" else "填写夸克 Cookie",
            fields = fields,
            onDismiss = { showPhone = false },
            onResult = { values ->
                name = values["name"].orEmpty()
                if (isWebDav) {
                    url = values["url"].orEmpty()
                    username = values["username"].orEmpty()
                    password = values["password"].orEmpty()
                    rootPath = values["rootPath"].orEmpty()
                } else {
                    cookie = values["cookie"].orEmpty()
                }
                showPhone = false
                viewModel.notify("已从手机接收填写内容")
            }
        )
    }

    if (showCookieEditor) {
        CookieEditorDialog(
            initial = cookie,
            onDismiss = { showCookieEditor = false },
            onSave = {
                cookie = it
                showCookieEditor = false
            }
        )
    }

    if (showQrLogin) {
        QuarkQrLoginDialog(
            onDismiss = { showQrLogin = false },
            onCookie = { raw ->
                cookie = raw
                showQrLogin = false
                testResult = true to "已获取夸克登录信息，请点击“保存并扫描”"
            }
        )
    }

    if (showQuarkPicker) {
        QuarkFolderPickerDialog(
            config = buildQuark(),
            initialSelection = selectedRoots,
            onDismiss = { showQuarkPicker = false },
            onConfirm = { roots ->
                selectedRoots = roots
                showQuarkPicker = false
                testResult = null
                viewModel.notify(
                    if (roots.isEmpty()) "未选择目录，将扫描整个夸克网盘"
                    else "已选择 ${roots.size} 个扫描目录"
                )
            }
        )
    }
}




@Composable
private fun CookieEditorDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var textValue by remember {
        androidx.compose.runtime.mutableStateOf(
            TextFieldValue(initial, selection = TextRange(initial.length))
        )
    }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        runCatching { fieldFocus.requestFocus() }
    }

    TvDialog(onDismiss = onDismiss, modifier = Modifier.width(760.dp)) {
        com.juying.tv.ui.components.DialogTitle(
            "编辑夸克 Cookie",
            "Cookie 只在电视本机保存；推荐优先使用手机扫码填写"
        )
        Spacer(Modifier.height(14.dp))
        TvFieldLabel("Cookie 内容", focused)
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .focusGlow(focused, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(JuyingColors.BackgroundAlt)
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) JuyingColors.Gold else JuyingColors.Outline,
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            if (textValue.text.isEmpty()) {
                Text(
                    "粘贴完整的 Cookie…",
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            BasicTextField(
                value = textValue,
                onValueChange = { textValue = it },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = JuyingColors.TextPrimary),
                cursorBrush = SolidColor(JuyingColors.Gold),
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) keyboard?.show()
                    }
                    .focusRequester(fieldFocus)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "已输入 ${textValue.text.length} 个字符。文本框可在内部滚动，不再撑高页面。",
            color = JuyingColors.TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            TvButton("取消", onDismiss, primary = false)
            TvButton("保存 Cookie", { onSave(textValue.text.trim()) })
        }
    }
}
