package com.juying.tv.ui.screens

import com.juying.tv.ui.RegisterScreenFocus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import com.juying.tv.AppViewModel
import com.juying.tv.BuildConfig
import com.juying.tv.ui.components.Badge
import com.juying.tv.ui.components.ConfirmDialog
import com.juying.tv.ui.components.PinDialogBody
import com.juying.tv.ui.components.SetPinDialog
import com.juying.tv.ui.components.SettingRow
import com.juying.tv.ui.components.TogglePill
import com.juying.tv.ui.theme.JuyingColors
import com.juying.tv.util.localIpv4Address
import kotlinx.coroutines.delay

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

    var showSetPin by remember { mutableStateOf(false) }
    var showVerifyForChange by remember { mutableStateOf(false) }
    var showClearPin by remember { mutableStateOf(false) }
    var showEnableKids by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    RegisterScreenFocus(firstFocus)
    val ip = remember { localIpv4Address() ?: "未连接局域网" }

    LaunchedEffect(Unit) {
        delay(180)
        firstFocus.requestFocus()
    }

    TvLazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
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
        item { SectionLabel("家长与儿童") }
        item {
            SettingRow(
                title = if (pinSet) "家长锁已启用" else "设置家长锁",
                subtitle = if (pinSet) "进入儿童模式、修改允许内容时需要输入密码" else "建议先设置 4–6 位数字密码，防止孩子退出儿童模式",
                icon = Icons.Rounded.Lock,
                focusRequester = firstFocus,
                onClick = {
                    if (pinSet) showVerifyForChange = true else showSetPin = true
                },
                trailing = {
                    Badge(if (pinSet) "已设置" else "未设置", if (pinSet) JuyingColors.Green else JuyingColors.TextSecondary)
                }
            )
        }
        item {
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
        item {
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
            item {
                SettingRow(
                    title = "关闭并清除家长锁",
                    subtitle = "会同时退出儿童模式并清空家长密码",
                    icon = Icons.Rounded.DeleteForever,
                    onClick = { showClearPin = true }
                )
            }
        }
        item { SectionLabel("播放") }
        item {
            SettingRow(
                title = "自动播放下一集",
                subtitle = "剧集播放结束后自动进入下一集",
                icon = Icons.Rounded.PlayCircle,
                onClick = { viewModel.setAutoNext(!autoNext) },
                trailing = { TogglePill(autoNext) }
            )
        }
        item { SectionLabel("媒体库") }
        item {
            SettingRow(
                title = "扫描全部媒体源",
                subtitle = if (scanState?.running == true) "正在扫描 ${scanState?.sourceName} · ${scanState?.files ?: 0} 个文件" else "共 ${sources.size} 个媒体源 · ${series.size} 部内容",
                icon = Icons.Rounded.Refresh,
                onClick = { viewModel.scanAll() }
            )
        }
        item {
            SettingRow(
                title = "管理媒体源",
                subtitle = "添加本地目录、U 盘、WebDAV 或夸克网盘",
                icon = Icons.Rounded.Cloud,
                onClick = onOpenSources
            )
        }
        item {
            SettingRow(
                title = "已隐藏的内容",
                subtitle = "恢复此前从媒体库隐藏的剧集",
                icon = Icons.Rounded.VisibilityOff,
                onClick = onOpenHidden
            )
        }
        item { SectionLabel("手机辅助填写与隐私") }
        item {
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
        item { SectionLabel("关于") }
        item {
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
private fun SectionLabel(text: String) {
    Text(
        text,
        color = JuyingColors.TextMuted,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}
