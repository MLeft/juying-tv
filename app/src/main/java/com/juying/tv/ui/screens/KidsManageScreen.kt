package com.juying.tv.ui.screens

import com.juying.tv.ui.RegisterScreenFocus
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import com.juying.tv.AppViewModel
import com.juying.tv.data.db.MediaEntity
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.ui.components.Badge
import com.juying.tv.ui.components.CheckMark
import com.juying.tv.ui.components.ConfirmDialog
import com.juying.tv.ui.components.EmptyState
import com.juying.tv.ui.components.FocusableSurface
import com.juying.tv.ui.components.InlinePinPanel
import com.juying.tv.ui.components.TvButton
import com.juying.tv.ui.components.TvChip
import com.juying.tv.ui.theme.JuyingColors
import kotlinx.coroutines.delay

@Composable
fun KidsManageScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val series by viewModel.series.collectAsStateWithLifecycle()
    val pinSet by viewModel.pinSet.collectAsStateWithLifecycle()
    val kidsMode by viewModel.kidsMode.collectAsStateWithLifecycle()

    var unlocked by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf<Boolean?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val gateFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    RegisterScreenFocus(if (unlocked) contentFocus else gateFocus)

    LaunchedEffect(unlocked) {
        if (unlocked) {
            listOf(250L, 600L, 1100L).forEach { wait ->
                delay(wait)
                runCatching { contentFocus.requestFocus() }
            }
        }
    }

    LaunchedEffect(pinSet) {
        if (!pinSet) unlocked = false
    }

    LaunchedEffect(series) {
        if (selectedId == null) selectedId = series.firstOrNull()?.id
    }

    if (!pinSet) {
        InlinePinPanel(
            title = "设置家长锁",
            subtitle = "设置 4–6 位数字，用于进入儿童模式与管理允许内容",
            setupMode = true,
            onBack = onBack,
            onSavePin = { pin ->
                viewModel.setPin(pin)
                unlocked = true
            },
            focusRequester = gateFocus
        )
    } else if (!unlocked) {
        InlinePinPanel(
            title = "家长验证",
            subtitle = "输入家长锁密码后管理儿童可看内容",
            verify = { pin -> viewModel.verifyPin(pin) },
            onVerified = { unlocked = true },
            onBack = onBack,
            focusRequester = gateFocus
        )
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "儿童模式内容管理",
                        color = JuyingColors.TextPrimary,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "只有被允许的剧集和集数会出现在儿童模式中",
                        color = JuyingColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TvChip("全部允许", onClick = { confirmAll = true })
                Spacer(Modifier.width(8.dp))
                TvChip("全部禁止", onClick = { confirmAll = false })
                Spacer(Modifier.width(8.dp))
                TvButton("返回", onBack, primary = false, icon = Icons.Rounded.ArrowBack, focusRequester = contentFocus)
            }
            Spacer(Modifier.height(16.dp))
            if (series.isEmpty()) {
                EmptyState(
                    title = "媒体库还没有内容",
                    description = "先添加媒体源并扫描，归集出剧集后再设置儿童模式。",
                    actionText = "返回",
                    action = onBack
                )
            } else {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SeriesSelector(
                        series = series,
                        selectedId = selectedId,
                        onSelect = { selectedId = it },
                        modifier = Modifier.width(310.dp)
                    )
                    val selected = series.firstOrNull { it.id == selectedId } ?: series.firstOrNull()
                    if (selected != null) {
                        KidsSeriesPane(
                            viewModel = viewModel,
                            series = selected,
                            actionFocus = contentFocus,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    confirmAll?.let { allow ->
        ConfirmDialog(
            title = if (allow) "允许全部内容" else "禁止全部内容",
            message = if (allow) {
                "会把媒体库中所有剧集和集数加入儿童模式允许列表。确定继续吗？"
            } else {
                "儿童模式将看不到任何内容，直到你重新选择。确定继续吗？"
            },
            confirmText = if (allow) "全部允许" else "全部禁止",
            destructive = !allow,
            onConfirm = {
                viewModel.setAllKids(allow)
                confirmAll = null
            },
            onDismiss = { confirmAll = null }
        )
    }
}

@Composable
private fun SeriesSelector(
    series: List<SeriesEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TvLazyColumn(
        state = rememberTvLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(series, key = { it.id }) { item ->
            val selected = item.id == selectedId
            FocusableSurface(
                onClick = { onSelect(item.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(12.dp),
                focusedScale = 1.015f,
                backgroundColor = if (selected) JuyingColors.Gold.copy(alpha = 0.1f) else JuyingColors.Surface.copy(alpha = 0.3f),
                focusedBackgroundColor = JuyingColors.SurfaceFocused,
                borderColor = if (selected) JuyingColors.Gold.copy(alpha = 0.4f) else JuyingColors.Outline.copy(alpha = 0.4f)
            ) {
                Row(
                    Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CheckMark(item.kidsAllowed)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.displayTitle,
                            color = JuyingColors.TextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.kidsAllowed) {
                            Text("已允许", color = JuyingColors.KidsAccent, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KidsSeriesPane(
    viewModel: AppViewModel,
    series: SeriesEntity,
    actionFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    val media by viewModel.allMediaFlow(series.id).collectAsStateWithLifecycle(emptyList())
    val allowedCount = media.count { it.kidsAllowed }
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(JuyingColors.Surface.copy(alpha = 0.28f))
            .border(1.dp, JuyingColors.Outline.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    series.displayTitle,
                    color = JuyingColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$allowedCount / ${media.size} 集已允许",
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TvChip("全选本剧", selected = true, onClick = { viewModel.setSeriesKids(series.id, true) }, focusRequester = actionFocus)
            Spacer(Modifier.width(8.dp))
            TvChip("清空本剧", onClick = { viewModel.setSeriesKids(series.id, false) })
        }
        Spacer(Modifier.height(12.dp))
        TvLazyColumn(
            state = rememberTvLazyListState(),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(media, key = { it.id }) { item ->
                EpisodeToggleRow(
                    media = item,
                    onClick = { viewModel.setEpisodeKids(item.id, !item.kidsAllowed) }
                )
            }
            if (media.isEmpty()) {
                item {
                    Text(
                        "本剧没有可管理的集数。",
                        color = JuyingColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeToggleRow(media: MediaEntity, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.01f,
        backgroundColor = if (media.kidsAllowed) JuyingColors.KidsAccent.copy(alpha = 0.06f) else JuyingColors.Surface.copy(alpha = 0.26f),
        focusedBackgroundColor = JuyingColors.SurfaceFocused,
        borderColor = if (media.kidsAllowed) JuyingColors.KidsAccent.copy(alpha = 0.28f) else JuyingColors.Outline.copy(alpha = 0.35f)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckMark(media.kidsAllowed)
            Spacer(Modifier.width(10.dp))
            Text(
                if (media.season > 1) "S${media.season}E${media.episode}" else "第 ${media.episode} 集",
                color = JuyingColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(80.dp)
            )
            Text(
                media.title,
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
