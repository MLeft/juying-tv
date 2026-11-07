package com.juying.tv.ui

import com.juying.tv.BuildConfig
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juying.tv.ui.components.FocusableSurface
import com.juying.tv.ui.theme.JuyingColors

enum class NavKey { HOME, LIBRARY, SOURCES, KIDS, SETTINGS, PARENT, EXIT_KIDS }

@Composable
fun NavRail(
    current: NavKey,
    kidsMode: Boolean,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val coordinator = LocalFocusCoordinator.current
    val contentEntry = coordinator.entryRequester
    val items = if (kidsMode) {
        listOf(
            NavItem(NavKey.HOME, "首页", Icons.Rounded.Home),
            NavItem(NavKey.LIBRARY, "片库", Icons.Rounded.VideoLibrary),
            NavItem(NavKey.PARENT, "家长", Icons.Rounded.Lock),
            NavItem(NavKey.EXIT_KIDS, "退出", Icons.Rounded.ChildCare)
        )
    } else {
        listOf(
            NavItem(NavKey.HOME, "首页", Icons.Rounded.Home),
            NavItem(NavKey.LIBRARY, "媒体库", Icons.Rounded.VideoLibrary),
            NavItem(NavKey.SOURCES, "媒体源", Icons.Rounded.Cloud),
            NavItem(NavKey.KIDS, "儿童", Icons.Rounded.ChildCare),
            NavItem(NavKey.SETTINGS, "设置", Icons.Rounded.Settings)
        )
    }

    Column(
        modifier = modifier
            .width(96.dp)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A101B), Color(0xFF070B12))
                )
            )
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(JuyingColors.Gold, Color(0xFFB98535))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("▶", color = JuyingColors.OnGold, fontSize = 22.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "聚影",
            color = JuyingColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(34.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEach { item ->
                NavRailItem(
                    item = item,
                    selected = item.key == current,
                    danger = item.key == NavKey.EXIT_KIDS,
                    contentEntry = contentEntry,
                    onClick = { onNavigate(item.key) }
                )
            }
        }

        Spacer(Modifier.weight(1f))
        if (kidsMode) {
            Box(
                Modifier
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(JuyingColors.KidsAccent.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                Text(
                    "儿童模式",
                    color = JuyingColors.KidsAccent,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp
                )
            }
        } else {
            Text(
                "v${BuildConfig.VERSION_NAME}",
                color = JuyingColors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun NavRailItem(
    item: NavItem,
    selected: Boolean,
    danger: Boolean,
    contentEntry: androidx.compose.ui.focus.FocusRequester?,
    onClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val labelColor = when {
        danger -> JuyingColors.Red
        selected || focused -> JuyingColors.Gold
        else -> JuyingColors.TextSecondary
    }
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .width(76.dp)
            .height(62.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    // 优先交给 Compose 做几何搜索：即使页面入口项已滚出屏幕、不再是组合状态，
                    // 也能落到当前可见的内容项上，避免按键被吞掉后焦点卡在导航栏。
                    val moved = focusManager.moveFocus(FocusDirection.Right)
                    if (moved) {
                        true
                    } else {
                        val target = contentEntry
                        if (target != null) {
                            runCatching { target.requestFocus() }.isSuccess
                        } else {
                            false
                        }
                    }
                } else {
                    false
                }
            },
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.05f,
        // 选中态只用左侧金色指示条和金色图标/文字；焦点态才出现胶囊底色和描边。
        backgroundColor = Color.Transparent,
        focusedBackgroundColor = JuyingColors.SurfaceFocused,
        borderColor = Color.Transparent,
        focusedBorderColor = if (danger) JuyingColors.Red else JuyingColors.Gold
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = labelColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                item.label,
                color = labelColor,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        if (selected) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(26.dp)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(JuyingColors.Gold)
            )
        }
    }
}

private data class NavItem(
    val key: NavKey,
    val label: String,
    val icon: ImageVector
)
