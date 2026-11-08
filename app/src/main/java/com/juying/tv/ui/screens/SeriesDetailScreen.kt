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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
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
import com.juying.tv.ui.components.LoadingPane
import com.juying.tv.ui.components.ProgressLine
import com.juying.tv.ui.components.SeriesArtwork
import com.juying.tv.ui.components.TogglePill
import com.juying.tv.ui.components.TvButton
import com.juying.tv.ui.components.TvChip
import com.juying.tv.ui.components.TvDialog
import com.juying.tv.ui.components.TvTextField
import com.juying.tv.ui.theme.JuyingColors
import com.juying.tv.util.formatEpisode
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
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
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showEdit by remember { mutableStateOf(false) }
    var selectedSeason by remember(seriesId) { mutableStateOf<Int?>(null) }
    var memberships by remember { mutableStateOf<Set<String>>(emptySet()) }
    val playFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val chipsFocus = remember { FocusRequester() }
    val firstEpisodeFocus = remember { FocusRequester() }
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
    // 电视端遥控器上下键只在内容区内移动，不要跳到左侧导航栏。
    val belowPlay = if (multiSeason) chipsFocus else firstEpisodeFocus
    val firstRowUp = if (multiSeason) chipsFocus else playFocus
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

    var showMissing by remember(seriesId) { mutableStateOf(false) }
    LaunchedEffect(seriesId, series) {
        if (series == null) {
            delay(500)
            if (series == null) showMissing = true
        } else {
            showMissing = false
        }
    }

    val current = series
    if (current == null) {
        if (!showMissing) {
            LoadingPane(Modifier.fillMaxSize())
        } else {
            EmptyState(
                title = "内容不存在",
                description = "该剧集可能已被移除或重新扫描。",
                actionText = "返回",
                action = onBack
            )
        }
        return
    }

    val aiForSeries = aiState?.takeIf { it.seriesId == seriesId }
    val aiBusy = aiForSeries?.running == true
    var showAiResult by remember(seriesId) { mutableStateOf(false) }
    LaunchedEffect(aiForSeries?.finished, aiForSeries?.message) {
        if (aiForSeries?.finished == true && !aiForSeries.message.isNullOrBlank()) {
            showAiResult = true
        }
    }
    LaunchedEffect(aiBusy) {
        if (aiBusy) showAiResult = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvChip(
                "返回",
                onClick = onBack,
                icon = Icons.Rounded.ArrowBack,
                focusRequester = backFocus,
                modifier = Modifier.focusProperties {
                    up = FocusRequester.Cancel
                    down = playFocus
                }
            )
            Spacer(Modifier.width(12.dp))
            if (sourceName != null) Badge(sourceName, JuyingColors.Blue)
            Spacer(Modifier.weight(1f))
            if (!kidsMode) {
                AiActionChip(
                    busy = aiBusy,
                    done = aiForSeries?.finished == true && showAiResult,
                    onClick = {
                        if (!aiBusy) viewModel.aiOrganizeSeries(seriesId)
                    },
                    modifier = Modifier.focusProperties {
                        up = FocusRequester.Cancel
                        down = playFocus
                    }
                )
                Spacer(Modifier.width(8.dp))
                TvChip(
                    "刮削",
                    onClick = { viewModel.scrapeSeries(seriesId) },
                    icon = Icons.Rounded.AutoAwesome,
                    modifier = Modifier.focusProperties {
                        up = FocusRequester.Cancel
                        down = playFocus
                    }
                )
                Spacer(Modifier.width(8.dp))
                TvChip(
                    "编辑信息",
                    onClick = { showEdit = true },
                    icon = Icons.Rounded.Edit,
                    modifier = Modifier.focusProperties {
                        up = FocusRequester.Cancel
                        down = playFocus
                    }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (aiBusy || (aiForSeries?.finished == true && showAiResult)) {
            AiStatusBanner(
                busy = aiBusy,
                message = aiForSeries?.message ?: if (aiBusy) "正在处理…" else "已完成",
                onRetry = {
                    showAiResult = false
                    viewModel.aiOrganizeSeries(seriesId)
                },
                onDismiss = { showAiResult = false }
            )
            Spacer(Modifier.height(10.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(20.dp))
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
                Spacer(Modifier.height(6.dp))
                Text(
                    current.displayTitle,
                    color = JuyingColors.TextPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (multiSeason) "$seasonCount 季 · ${media.size} 个可播放视频" else "${media.size} 个可播放视频 · 自动归集",
                    color = JuyingColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
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
                        enabled = target != null,
                        modifier = Modifier.focusProperties {
                            up = backFocus
                            down = belowPlay
                        }
                    )
                    if (resume != null && current.kind != SeriesKind.MOVIE) {
                        TvButton(
                            "从头播放",
                            { media.firstOrNull()?.let { onPlay(it.id, seriesId, true) } },
                            primary = false,
                            modifier = Modifier.focusProperties {
                                up = backFocus
                                down = belowPlay
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
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
                    onClick = { onPlay(resume.id, seriesId, false) },
                    modifier = Modifier.focusProperties {
                        up = playFocus
                        down = belowPlay
                    }
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
                        onClick = { selectedSeason = null },
                        focusRequester = chipsFocus,
                        modifier = Modifier.focusProperties {
                            up = playFocus
                            down = firstEpisodeFocus
                        }
                    )
                }
                listItems(seasons, key = { it }) { season ->
                    TvChip(
                        "S${season.toString().padStart(2, '0')}",
                        selected = selectedSeason == season,
                        onClick = { selectedSeason = season },
                        modifier = Modifier.focusProperties {
                            up = playFocus
                            down = firstEpisodeFocus
                        }
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
                columns = TvGridCells.Fixed(4),
                state = rememberTvLazyGridState(),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(visibleMedia, key = { _, it -> it.id }) { index, item ->
                    val p = progressById[item.id]
                    val fraction = p?.let {
                        if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) else 0f
                    } ?: 0f
                    var thumbnail by remember(item.id) { mutableStateOf<File?>(null) }
                    LaunchedEffect(item.id) {
                        thumbnail = viewModel.episodeThumbnail(item.id)
                    }
                    EpisodeCard(
                        series = current,
                        media = item,
                        thumbnail = thumbnail,
                        progress = fraction,
                        isCurrent = item.id == resume?.id,
                        multiSeason = multiSeason,
                        onClick = { onPlay(item.id, seriesId, false) },
                        modifier = when {
                            index == 0 -> Modifier
                                .focusRequester(firstEpisodeFocus)
                                .focusProperties { up = firstRowUp }
                            index < 4 -> Modifier.focusProperties { up = firstRowUp }
                            else -> Modifier
                        }
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
private fun EpisodeCard(
    series: SeriesEntity,
    media: MediaEntity,
    thumbnail: File?,
    progress: Float,
    isCurrent: Boolean,
    multiSeason: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.04f,
        backgroundColor = if (isCurrent) JuyingColors.Gold.copy(alpha = 0.1f) else JuyingColors.Surface.copy(alpha = 0.45f),
        focusedBackgroundColor = JuyingColors.SurfaceFocused,
        borderColor = if (isCurrent) JuyingColors.Gold.copy(alpha = 0.55f) else JuyingColors.Outline.copy(alpha = 0.45f),
        focusedBorderColor = JuyingColors.Gold
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            if (thumbnail != null) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = episodeDisplay(media, multiSeason),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF233248), Color(0xFF101A29))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (series.kind == SeriesKind.MOVIE) "影" else media.episode.toString().padStart(2, '0'),
                        color = JuyingColors.TextMuted,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                        )
                    )
            )
            Text(
                if (series.kind == SeriesKind.MOVIE) "电影" else episodeDisplay(media, multiSeason),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
            Text(
                media.title
                    .takeIf { it.isNotBlank() && it != series.displayTitle && it != series.autoTitle }
                    ?: media.fileName.substringBeforeLast('.').ifBlank { episodeDisplay(media, multiSeason) },
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 30.dp, bottom = 7.dp)
            )
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .size(18.dp)
            )
            if (progress > 0f) {
                ProgressLine(
                    progress,
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AiActionChip(
    busy: Boolean,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(50),
        focusedScale = 1.05f,
        backgroundColor = if (busy) JuyingColors.Gold.copy(alpha = 0.16f) else JuyingColors.Surface.copy(alpha = 0.5f),
        focusedBackgroundColor = JuyingColors.SurfaceFocused,
        borderColor = if (busy) JuyingColors.Gold.copy(alpha = 0.55f) else JuyingColors.Outline.copy(alpha = 0.55f),
        focusedBorderColor = JuyingColors.Gold
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = JuyingColors.Gold,
                    strokeWidth = 2.dp
                )
            } else if (done) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = JuyingColors.Green,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = JuyingColors.Gold,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    busy -> "整理中…"
                    done -> "已完成"
                    else -> "AI 整理"
                },
                color = when {
                    busy -> JuyingColors.Gold
                    done -> JuyingColors.Green
                    else -> JuyingColors.TextPrimary
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun AiStatusBanner(
    busy: Boolean,
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JuyingColors.Gold.copy(alpha = 0.08f))
            .border(1.dp, JuyingColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = JuyingColors.Gold,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = JuyingColors.Green,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (busy) "AI 正在整理" else "AI 整理完成",
                color = JuyingColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                message,
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!busy) {
            TvChip("重试", onClick = onRetry, icon = Icons.Rounded.Refresh)
            Spacer(Modifier.width(8.dp))
            TvChip("关闭", onClick = onDismiss)
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

    TvDialog(
        onDismiss = onDismiss,
        modifier = Modifier
            .width(760.dp)
            .heightIn(max = 456.dp)
    ) {
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
                .weight(1f, fill = false)
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
