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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items as listItems
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import com.juying.tv.AppViewModel
import com.juying.tv.data.db.CollectionEntity
import com.juying.tv.data.db.MediaEntity
import com.juying.tv.data.db.ProgressEntity
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.data.db.SeriesKind
import com.juying.tv.ui.components.Badge
import com.juying.tv.ui.components.CheckMark
import com.juying.tv.ui.components.EmptyState
import com.juying.tv.ui.components.FocusableSurface
import com.juying.tv.ui.components.ProgressLine
import com.juying.tv.ui.components.SeriesArtwork
import com.juying.tv.ui.components.TogglePill
import com.juying.tv.ui.components.TvButton
import com.juying.tv.ui.components.TvChip
import com.juying.tv.ui.components.TvDialog
import com.juying.tv.ui.components.TvTextField
import com.juying.tv.ui.theme.JuyingColors
import com.juying.tv.util.formatDuration
import com.juying.tv.util.formatEpisode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SeriesDetailScreen(
    viewModel: AppViewModel,
    seriesId: String,
    kidsMode: Boolean,
    onPlay: (String, String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val series by viewModel.seriesFlow(seriesId).collectAsStateWithLifecycle(initialValue = null)
    val media by viewModel.mediaFlow(seriesId).collectAsStateWithLifecycle(emptyList())
    val progress by viewModel.progressFlow(seriesId).collectAsStateWithLifecycle(emptyList())
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showEdit by remember { mutableStateOf(false) }
    var selectedSeason by remember(seriesId) { mutableStateOf<Int?>(null) }
    var memberships by remember { mutableStateOf<Set<String>>(emptySet()) }
    val playFocus = remember { FocusRequester() }
    RegisterScreenFocus(playFocus)

    val progressById = remember(progress) { progress.associateBy { it.mediaId } }
    val resume = remember(media, progressById) {
        media.filter { item ->
            val p = progressById[item.id] ?: return@filter false
            p.positionMs > 30_000 && p.durationMs > 0 && p.positionMs < p.durationMs * 95 / 100
        }.maxByOrNull { progressById[it.id]?.updatedAt ?: 0L }
    }
    val target = resume ?: media.firstOrNull()
    val seasons = remember(media) { media.map { it.season }.distinct().sorted() }
    val multiSeason = seasons.size > 1
    val seasonCount = seasons.size
    val visibleMedia = remember(media, selectedSeason) {
        if (selectedSeason == null) media else media.filter { it.season == selectedSeason }
    }
    LaunchedEffect(seasons) {
        if (selectedSeason != null && selectedSeason !in seasons) selectedSeason = null
    }
    val sourceName = remember(sources, series) {
        sources.firstOrNull { it.id == series?.sourceId }?.name
    }

    LaunchedEffect(seriesId, media.size) {
        delay(200)
        runCatching { playFocus.requestFocus() }
    }

    LaunchedEffect(showEdit) {
        if (showEdit) memberships = viewModel.collectionsForSeries(seriesId).toSet()
    }

    val current = series
    if (current == null) {
        EmptyState(
            title = "内容不存在",
            description = "该剧集可能已被移除或重新扫描。",
            actionText = "返回",
            action = onBack
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvChip("返回", onClick = onBack, icon = Icons.Rounded.ArrowBack)
            Spacer(Modifier.width(12.dp))
            if (sourceName != null) Badge(sourceName, JuyingColors.Blue)
            Spacer(Modifier.weight(1f))
            if (!kidsMode) {
                TvChip("编辑信息", onClick = { showEdit = true }, icon = Icons.Rounded.Edit)
            }
        }
        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            SeriesArtwork(current, Modifier.fillMaxSize(), showTitleFallback = false)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF070B12).copy(alpha = 0.97f),
                                Color(0xFF070B12).copy(alpha = 0.72f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF070B12).copy(alpha = 0.92f))
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 22.dp)
                    .fillMaxWidth(0.66f)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Badge(if (current.kind == SeriesKind.MOVIE) "电影" else "剧集", JuyingColors.Blue)
                    current.year?.let { Badge(it.toString(), JuyingColors.TextSecondary) }
                    if (kidsMode) Badge("儿童模式", JuyingColors.KidsAccent)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    current.displayTitle,
                    color = JuyingColors.TextPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (multiSeason) "$seasonCount 季 · ${media.size} 个可播放视频" else "${media.size} 个可播放视频 · 自动归集",
                    color = JuyingColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(
                        text = when {
                            target == null -> "暂无可播放内容"
                            resume != null -> "继续 ${episodeDisplay(target, multiSeason)}"
                            else -> if (current.kind == SeriesKind.MOVIE) "播放电影" else "从第一集开始"
                        },
                        onClick = { target?.let { onPlay(it.id, seriesId, false) } },
                        icon = Icons.Rounded.PlayArrow,
                        focusRequester = playFocus,
                        enabled = target != null
                    )
                    if (resume != null && current.kind != SeriesKind.MOVIE) {
                        TvButton(
                            "从头播放",
                            { media.firstOrNull()?.let { onPlay(it.id, seriesId, true) } },
                            primary = false
                        )
                    }
                }
            }
        }

        if (!current.overview.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                current.overview.orEmpty(),
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (current.kind == SeriesKind.MOVIE) "影片" else "剧集列表",
                color = JuyingColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (selectedSeason != null) "${visibleMedia.size} 集" else "${media.size} 集",
                color = JuyingColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.weight(1f))
            if (media.isNotEmpty() && resume != null) {
                TvChip(
                    "上次看到：${episodeDisplay(resume, multiSeason)}",
                    selected = true,
                    onClick = { onPlay(resume.id, seriesId, false) }
                )
            }
        }
        if (seasons.size > 1) {
            Spacer(Modifier.height(8.dp))
            TvLazyRow(
                state = rememberTvLazyListState(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "all") {
                    TvChip(
                        "全部季",
                        selected = selectedSeason == null,
                        onClick = { selectedSeason = null }
                    )
                }
                listItems(seasons, key = { it }) { season ->
                    TvChip(
                        "S${season.toString().padStart(2, '0')}",
                        selected = selectedSeason == season,
                        onClick = { selectedSeason = season }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (media.isEmpty()) {
            EmptyState(
                title = if (kidsMode) "家长还没有允许本剧" else "没有可播放的视频",
                description = if (kidsMode) "请让家长进入儿童模式内容管理中允许本剧或其中的某些集数。" else "文件可能已被移动或删除，重新扫描媒体源后再试。",
                actionText = null,
                action = null
            )
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(3),
                state = rememberTvLazyGridState(),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleMedia, key = { it.id }) { item ->
                    val p = progressById[item.id]
                    val fraction = p?.let {
                        if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) else 0f
                    } ?: 0f
                    EpisodeRow(
                        series = current,
                        media = item,
                        progress = fraction,
                        isCurrent = item.id == resume?.id,
                        multiSeason = multiSeason,
                        onClick = { onPlay(item.id, seriesId, false) }
                    )
                }
            }
        }
    }

    if (showEdit && !kidsMode) {
        SeriesEditDialog(
            series = current,
            collections = collections,
            memberships = memberships,
            onDismiss = { showEdit = false },
            onSave = { title, cover, kidsAllowed, hidden, selectedCollections ->
                viewModel.renameSeries(seriesId, title)
                viewModel.setSeriesCover(seriesId, cover)
                if (kidsAllowed != current.kidsAllowed) viewModel.setSeriesKids(seriesId, kidsAllowed)
                val previous = memberships
                (selectedCollections - previous).forEach { viewModel.addSeriesToCollection(it, seriesId) }
                (previous - selectedCollections).forEach { viewModel.removeSeriesFromCollection(it, seriesId) }
                if (hidden && !current.hidden) {
                    viewModel.hideSeries(seriesId)
                    onBack()
                }
                showEdit = false
            }
        )
    }
}

@Composable
private fun EpisodeRow(
    series: SeriesEntity,
    media: MediaEntity,
    progress: Float,
    isCurrent: Boolean,
    multiSeason: Boolean,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(13.dp),
        focusedScale = 1.02f,
        backgroundColor = if (isCurrent) JuyingColors.Gold.copy(alpha = 0.08f) else JuyingColors.Surface.copy(alpha = 0.34f),
        focusedBackgroundColor = JuyingColors.SurfaceFocused,
        borderColor = if (isCurrent) JuyingColors.Gold.copy(alpha = 0.4f) else JuyingColors.Outline.copy(alpha = 0.45f)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) JuyingColors.Gold else JuyingColors.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (series.kind == SeriesKind.MOVIE) "影" else media.episode.toString().padStart(2, '0'),
                    color = if (isCurrent) JuyingColors.OnGold else JuyingColors.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (series.kind == SeriesKind.MOVIE) series.displayTitle else episodeDisplay(media, multiSeason),
                    color = JuyingColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        media.fileName.takeIf { it.isNotBlank() },
                        if (media.durationMs > 0) formatDuration(media.durationMs) else null
                    ).joinToString(" · "),
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (progress > 0f) {
                Box(Modifier.width(54.dp)) {
                    ProgressLine(progress, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(10.dp))
            }
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = JuyingColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

private fun episodeDisplay(media: MediaEntity, multiSeason: Boolean): String =
    if (multiSeason) {
        "S${media.season.toString().padStart(2, '0')}E${media.episode.toString().padStart(2, '0')}"
    } else {
        formatEpisode(media.season, media.episode, false)
    }

@Composable
private fun SeriesEditDialog(
    series: SeriesEntity,
    collections: List<CollectionEntity>,
    memberships: Set<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, cover: String?, kidsAllowed: Boolean, hidden: Boolean, collections: Set<String>) -> Unit
) {
    var title by remember(series.id) { mutableStateOf(series.customTitle ?: "") }
    var cover by remember(series.id) { mutableStateOf(series.coverUrl.orEmpty()) }
    var kidsAllowed by remember(series.id) { mutableStateOf(series.kidsAllowed) }
    var hidden by remember(series.id) { mutableStateOf(series.hidden) }
    var selected by remember(series.id, memberships) { mutableStateOf(memberships) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        focus.requestFocus()
    }

    TvDialog(onDismiss = onDismiss, modifier = Modifier.width(760.dp)) {
        Text(
            "编辑「${series.displayTitle}」",
            color = JuyingColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "修改只影响聚影媒体库，不会重命名或移动原文件。",
            color = JuyingColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvTextField(
                value = title,
                onValueChange = { title = it },
                label = "显示名称（留空恢复自动识别）",
                placeholder = series.autoTitle,
                focusRequester = focus
            )
            TvTextField(
                value = cover,
                onValueChange = { cover = it },
                label = "封面图片 URL（可选）",
                placeholder = "https://.../poster.jpg"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EditToggle(
                    title = "允许儿童模式观看",
                    subtitle = "开启后会允许本剧全部集数",
                    checked = kidsAllowed,
                    modifier = Modifier.weight(1f),
                    onToggle = { kidsAllowed = !kidsAllowed }
                )
                EditToggle(
                    title = "从媒体库隐藏",
                    subtitle = "不会删除文件，可在管理页恢复",
                    checked = hidden,
                    modifier = Modifier.weight(1f),
                    onToggle = { hidden = !hidden }
                )
            }
            Text(
                "加入合集",
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.labelLarge
            )
            if (collections.isEmpty()) {
                Text(
                    "还没有合集。可以先在媒体库创建一个，再把本剧加入。",
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                collections.forEach { collection ->
                    val checked = selected.contains(collection.id)
                    FocusableSurface(
                        onClick = {
                            selected = if (checked) selected - collection.id else selected + collection.id
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(12.dp),
                        focusedScale = 1.01f,
                        backgroundColor = if (checked) JuyingColors.Gold.copy(alpha = 0.08f) else JuyingColors.Surface.copy(alpha = 0.35f),
                        focusedBackgroundColor = JuyingColors.SurfaceFocused
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CheckMark(checked)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                collection.name,
                                color = JuyingColors.TextPrimary,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (checked) "已加入" else "未加入",
                                color = if (checked) JuyingColors.Gold else JuyingColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            TvButton("取消", onDismiss, primary = false)
            TvButton(
                "保存",
                {
                    onSave(
                        title.trim(),
                        cover.trim().ifBlank { null },
                        kidsAllowed,
                        hidden,
                        selected
                    )
                }
            )
        }
    }
}

@Composable
private fun EditToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    FocusableSurface(
        onClick = onToggle,
        modifier = modifier.heightIn(min = 78.dp),
        shape = RoundedCornerShape(13.dp),
        focusedScale = 1.01f,
        backgroundColor = JuyingColors.Surface.copy(alpha = 0.35f),
        focusedBackgroundColor = JuyingColors.SurfaceFocused
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = JuyingColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = JuyingColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(10.dp))
            TogglePill(checked)
        }
    }
}
