package com.juying.tv.ui.screens

import com.juying.tv.ui.RegisterScreenFocus
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import com.juying.tv.AppViewModel
import com.juying.tv.data.db.ContinueItem
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.data.db.SeriesKind
import com.juying.tv.ui.components.Badge
import com.juying.tv.ui.components.EmptyState
import com.juying.tv.ui.components.FocusableSurface
import com.juying.tv.ui.components.ProgressLine
import com.juying.tv.ui.components.SectionHeader
import com.juying.tv.ui.components.SeriesArtwork
import com.juying.tv.ui.components.TvButton
import com.juying.tv.ui.components.TvChip
import com.juying.tv.ui.theme.JuyingColors
import com.juying.tv.util.formatEpisode
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    kidsMode: Boolean,
    onOpenSeries: (String) -> Unit,
    onPlay: (String, String) -> Unit,
    onOpenSources: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenKidsSettings: () -> Unit
) {
    val allSeries by viewModel.series.collectAsStateWithLifecycle()
    val kidsSeries by viewModel.kidsSeries.collectAsStateWithLifecycle()
    val continueItems by viewModel.continueWatching.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val collectionItems by viewModel.collectionItems.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    val visible = if (kidsMode) kidsSeries else allSeries
    val visibleById = remember(visible) { visible.associateBy { it.id } }
    val filteredContinue = continueItems.filter { visibleById.containsKey(it.seriesId) }

    val featured = filteredContinue.firstOrNull()?.let { visibleById[it.seriesId] }
        ?: visible.filter { it.kind == SeriesKind.SERIES && it.displayTitle.any { c -> c.isLetter() } }
            .maxByOrNull { it.createdAt }
        ?: visible.filter { it.displayTitle.any { c -> c.isLetter() } }.maxByOrNull { it.createdAt }
        ?: visible.maxByOrNull { it.createdAt }
    val featuredContinue = featured?.let { series ->
        filteredContinue.firstOrNull { it.seriesId == series.id }
    }

    val collectionRows = remember(collections, collectionItems, visibleById, kidsMode) {
        if (kidsMode) emptyList()
        else collections
            .filter { it.showOnHome }
            .sortedBy { it.sortIndex }
            .mapNotNull { collection ->
                val items = collectionItems
                    .filter { it.collectionId == collection.id }
                    .sortedBy { it.sortIndex }
                    .mapNotNull { visibleById[it.seriesId] }
                if (items.isEmpty()) null else collection to items
            }
    }

    val recent = remember(visible) { visible.sortedByDescending { it.createdAt }.take(20) }
    val firstFocus = remember { FocusRequester() }
    RegisterScreenFocus(firstFocus)

    LaunchedEffect(featured?.id, visible.size, sources.size) {
        delay(220)
        runCatching { firstFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        if (visible.isEmpty()) {
            when {
                sources.isEmpty() -> EmptyState(
                    title = "先接入你的媒体资源",
                    description = "添加电视本地目录、U 盘、WebDAV，或用夸克网盘 Cookie 直接接入。聚影会把动画和影视自动归集到媒体库。",
                    actionText = "添加媒体源",
                    action = onOpenSources,
                    focusRequester = firstFocus
                )
                scanState?.running == true -> EmptyState(
                    title = "正在扫描媒体源",
                    description = "已发现 ${scanState?.files ?: 0} 个文件，请稍候。首次扫描云盘目录可能需要几分钟。",
                    actionText = "查看扫描进度",
                    action = onOpenSources
                )
                else -> EmptyState(
                    title = "还没有识别到视频",
                    description = "可以重新扫描，或添加其他目录。支持 mp4 / mkv / ts / avi 等常见格式。",
                    actionText = "重新扫描",
                    action = { viewModel.scanAll() },
                    focusRequester = firstFocus
                )
            }
        } else {
            TvLazyColumn(
                state = rememberTvLazyListState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item(key = "topline") {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "聚影 · 私人影院",
                            color = JuyingColors.TextSecondary,
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.width(14.dp))
                        if (kidsMode) {
                            Badge("儿童模式", JuyingColors.KidsAccent)
                        } else if (sources.isNotEmpty()) {
                            Badge("${sources.size} 个媒体源", JuyingColors.Blue)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${visible.size} 部内容",
                            color = JuyingColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                featured?.let { series ->
                    item(key = "hero-${series.id}") {
                        HeroBanner(
                            series = series,
                            continueItem = featuredContinue,
                            kidsMode = kidsMode,
                            primaryFocus = firstFocus,
                            onPrimary = {
                                if (featuredContinue != null) onPlay(featuredContinue.mediaId, series.id)
                                else onOpenSeries(series.id)
                            },
                            onDetails = { onOpenSeries(series.id) }
                        )
                    }
                }

                if (filteredContinue.isNotEmpty()) {
                    item(key = "continue") {
                        ContinueShelf(
                            items = filteredContinue,
                            seriesById = visibleById,
                            onPlay = onPlay
                        )
                    }
                }

                collectionRows.forEach { (collection, items) ->
                    item(key = "collection-${collection.id}") {
                        SeriesShelf(
                            title = collection.name,
                            subtitle = "${items.size} 部内容 · 自定义合集",
                            seriesList = items,
                            onOpenSeries = onOpenSeries,
                            onOpenCollection = { onOpenCollection(collection.id) }
                        )
                    }
                }

                if (recent.isNotEmpty()) {
                    item(key = "recent") {
                        SeriesShelf(
                            title = "最近加入",
                            subtitle = "新收录到媒体库的内容",
                            seriesList = recent,
                            onOpenSeries = onOpenSeries
                        )
                    }
                }

                item(key = "all") {
                    SeriesShelf(
                        title = if (kidsMode) "适合观看" else "全部内容",
                        subtitle = if (kidsMode) "家长允许观看的剧集与电影" else "按名称排序，可在媒体库中重新整理",
                        seriesList = visible.take(40),
                        onOpenSeries = onOpenSeries
                    )
                }

                if (kidsMode) {
                    item(key = "kids-note") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(JuyingColors.KidsAccent.copy(alpha = 0.08f))
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "儿童模式只会显示家长允许的内容。需要修改时，请进入“家长”并输入密码。",
                                color = JuyingColors.KidsAccent,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TvChip("家长管理", onClick = onOpenKidsSettings)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(
    series: SeriesEntity,
    continueItem: ContinueItem?,
    kidsMode: Boolean,
    primaryFocus: FocusRequester,
    onPrimary: () -> Unit,
    onDetails: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(26.dp))
    ) {
        SeriesArtwork(series, Modifier.fillMaxSize(), showTitleFallback = false)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF070B12).copy(alpha = 0.97f),
                            Color(0xFF070B12).copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF070B12).copy(alpha = 0.94f))
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp, end = 32.dp)
                .fillMaxWidth(0.62f)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (kidsMode) Badge("儿童推荐", JuyingColors.KidsAccent)
                else if (continueItem != null) Badge("继续观看")
                else Badge("精选")
                Badge(
                    if (series.kind == SeriesKind.MOVIE) "电影" else "剧集",
                    JuyingColors.Blue
                )
                series.year?.let { Badge(it.toString(), JuyingColors.TextSecondary) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                series.displayTitle,
                color = JuyingColors.TextPrimary,
                style = if (series.displayTitle.length > 18) {
                    MaterialTheme.typography.headlineLarge
                } else {
                    MaterialTheme.typography.displayMedium
                },
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Text(
                continueItem?.let {
                    "上次看到 ${formatEpisode(it.season, it.episode, it.kind == SeriesKind.MOVIE)} · ${(it.progressFraction * 100).toInt()}%"
                } ?: "已归集到聚影媒体库，随时从上次的位置继续",
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2
            )
            if (continueItem != null) {
                Spacer(Modifier.height(16.dp))
                ProgressLine(
                    fraction = continueItem.progressFraction,
                    modifier = Modifier.width(310.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = if (continueItem != null) "继续观看" else "开始观看",
                    onClick = onPrimary,
                    icon = Icons.Rounded.PlayArrow,
                    focusRequester = primaryFocus
                )
                TvButton("查看详情", onDetails, primary = false)
            }
        }
    }
}

@Composable
private fun ContinueShelf(
    items: List<ContinueItem>,
    seriesById: Map<String, SeriesEntity>,
    onPlay: (String, String) -> Unit
) {
    Column {
        SectionHeader(
            title = "继续观看",
            subtitle = "从上次停下的位置接着看",
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Spacer(Modifier.height(14.dp))
        TvLazyRow(
            state = rememberTvLazyListState(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.mediaId }) { item ->
                val series = seriesById[item.seriesId]
                if (series != null) {
                    ContinueCard(
                        series = series,
                        item = item,
                        onClick = { onPlay(item.mediaId, item.seriesId) }
                    )
                    }
            }
        }
    }
}

@Composable
private fun ContinueCard(
    series: SeriesEntity,
    item: ContinueItem,
    onClick: () -> Unit
) {
    Column(Modifier.width(250.dp).padding(vertical = 4.dp)) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = RoundedCornerShape(16.dp),
            focusedScale = 1.0f
        ) {
            SeriesArtwork(series, Modifier.fillMaxSize(), showTitleFallback = false)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
            )
            Badge(
                formatEpisode(item.season, item.episode, item.kind == SeriesKind.MOVIE),
                JuyingColors.Gold,
                Modifier.align(Alignment.TopStart).padding(10.dp)
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    series.displayTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                ProgressLine(item.progressFraction, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun SeriesShelf(
    title: String,
    subtitle: String? = null,
    seriesList: List<SeriesEntity>,
    onOpenSeries: (String) -> Unit,
    onOpenCollection: (() -> Unit)? = null
) {
    if (seriesList.isEmpty()) return
    Column {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.padding(horizontal = 2.dp),
            trailing = onOpenCollection?.let {
                {
                    TvChip("查看合集", onClick = it)
                }
            }
        )
        Spacer(Modifier.height(14.dp))
        TvLazyRow(
            state = rememberTvLazyListState(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(seriesList, key = { it.id }) { series ->
                SeriesCard(series = series, onClick = { onOpenSeries(series.id) })
            }
        }
    }
}

@Composable
fun SeriesCard(
    series: SeriesEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.width(200.dp).padding(vertical = 4.dp)) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = RoundedCornerShape(15.dp),
            focusedScale = 1.0f
        ) {
            SeriesArtwork(series, Modifier.fillMaxSize(), showTitleFallback = false)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                        )
                    )
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            series.displayTitle,
            color = JuyingColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            listOfNotNull(
                if (series.kind == SeriesKind.MOVIE) "电影" else "剧集",
                series.year?.toString()
            ).joinToString(" · "),
            color = JuyingColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}
