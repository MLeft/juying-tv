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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items as listItems
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import com.juying.tv.AppViewModel
import com.juying.tv.data.db.CollectionEntity
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.ui.components.Badge
import com.juying.tv.ui.components.ConfirmDialog
import com.juying.tv.ui.components.EmptyState
import com.juying.tv.ui.components.FocusableSurface
import com.juying.tv.ui.components.PosterCard
import com.juying.tv.ui.components.SeriesArtwork
import com.juying.tv.ui.components.TogglePill
import com.juying.tv.ui.components.TvButton
import com.juying.tv.ui.components.TvChip
import com.juying.tv.ui.components.TvTextField
import com.juying.tv.ui.theme.JuyingColors
import kotlinx.coroutines.delay

@Composable
fun CollectionDetailScreen(
    viewModel: AppViewModel,
    collectionId: String,
    kidsMode: Boolean,
    onOpenSeries: (String) -> Unit,
    onEdit: () -> Unit,
    onAddContent: () -> Unit,
    onBack: () -> Unit
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val items by viewModel.collectionItemsFlow(collectionId).collectAsStateWithLifecycle(emptyList())
    val allSeries by viewModel.series.collectAsStateWithLifecycle()
    val kidsSeries by viewModel.kidsSeries.collectAsStateWithLifecycle()

    val collection = collections.firstOrNull { it.id == collectionId }
    val visibleById = remember(allSeries, kidsSeries, kidsMode) {
        (if (kidsMode) kidsSeries else allSeries).associateBy { it.id }
    }
    val series = remember(items, visibleById) {
        items.sortedBy { it.sortIndex }.mapNotNull { visibleById[it.seriesId] }
    }
    val addFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    RegisterScreenFocus(if (kidsMode) backFocus else addFocus)

    LaunchedEffect(Unit) {
        delay(180)
        runCatching { (if (kidsMode) backFocus else addFocus).requestFocus() }
    }

    if (collection == null) {
        EmptyState(
            title = "合集不存在",
            description = "这个合集可能已经被删除。",
            actionText = "返回媒体库",
            action = onBack
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    collection.name,
                    color = JuyingColors.TextPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${series.size} 部内容 · 可自由调整顺序",
                    color = JuyingColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!kidsMode) {
                    TvChip("编辑合集", onClick = onEdit, icon = Icons.Rounded.Edit)
                    TvChip("添加内容", onClick = onAddContent, icon = Icons.Rounded.Add, focusRequester = addFocus)
                }
                TvChip("返回", onClick = onBack, icon = Icons.Rounded.ArrowBack, focusRequester = backFocus)
            }
        }
        Spacer(Modifier.height(22.dp))
        if (series.isEmpty()) {
            EmptyState(
                title = "合集还是空的",
                description = "把喜欢的剧集或电影添加进来，并按你的顺序排列。",
                actionText = if (kidsMode) null else "添加内容",
                action = if (kidsMode) null else onAddContent,
                focusRequester = addFocus
            )
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(6),
                state = rememberTvLazyGridState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(series, key = { it.id }) { item ->
                    PosterCard(
                        series = item,
                        onClick = { onOpenSeries(item.id) },
                        topBadge = if (collection.showOnHome) "首页" else null
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionEditScreen(
    viewModel: AppViewModel,
    collectionId: String,
    onOpenPicker: () -> Unit,
    onBack: () -> Unit
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val items by viewModel.collectionItemsFlow(collectionId).collectAsStateWithLifecycle(emptyList())
    val allSeries by viewModel.series.collectAsStateWithLifecycle()
    val seriesById = remember(allSeries) { allSeries.associateBy { it.id } }
    val collection = collections.firstOrNull { it.id == collectionId }

    var name by remember(collection?.id) { mutableStateOf(collection?.name.orEmpty()) }
    var cover by remember(collection?.id) { mutableStateOf(collection?.coverUrl.orEmpty()) }
    var showOnHome by remember(collection?.id) { mutableStateOf(collection?.showOnHome ?: true) }
    var showDelete by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    RegisterScreenFocus(nameFocus)

    LaunchedEffect(collection?.id) {
        delay(180)
        nameFocus.requestFocus()
    }

    if (collection == null) {
        EmptyState(
            title = "合集不存在",
            description = "这个合集可能已经被删除。",
            actionText = "返回",
            action = onBack
        )
        return
    }

    fun save() {
        viewModel.renameCollection(collectionId, name)
        viewModel.setCollectionCover(collectionId, cover)
        viewModel.setCollectionShowOnHome(collectionId, showOnHome)
        viewModel.notify("合集设置已保存")
        onBack()
    }

    TvLazyColumn(
        state = rememberTvLazyListState(),
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "编辑合集",
                        color = JuyingColors.TextPrimary,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "调整名称、封面、首页显示与内容顺序",
                        color = JuyingColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TvChip("返回", onClick = onBack, icon = Icons.Rounded.ArrowBack)
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                TvTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "合集名称",
                    placeholder = "例如：周末动画",
                    modifier = Modifier.weight(1f),
                    focusRequester = nameFocus
                )
                TvTextField(
                    value = cover,
                    onValueChange = { cover = it },
                    label = "封面图片 URL（可选）",
                    placeholder = "https://.../poster.jpg",
                    modifier = Modifier.weight(1.35f)
                )
            }
        }
        item {
            FocusableSurface(
                onClick = { showOnHome = !showOnHome },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = RoundedCornerShape(14.dp),
                focusedScale = 1.006f,
                backgroundColor = JuyingColors.Surface.copy(alpha = 0.34f),
                focusedBackgroundColor = JuyingColors.SurfaceFocused
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("显示在首页", color = JuyingColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text("首页会把整个合集排成一行，方便快速切换", color = JuyingColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    TogglePill(showOnHome)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("保存设置", { save() })
                TvButton("添加内容", onOpenPicker, primary = false, icon = Icons.Rounded.Add)
                Spacer(Modifier.weight(1f))
                TvButton("删除合集", { showDelete = true }, primary = false, icon = Icons.Rounded.Delete)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Text(
                "内容顺序（${items.size} 部）",
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.titleMedium
            )
        }
        listItems(items, key = { it.seriesId }) { item ->
            val series = seriesById[item.seriesId]
            if (series != null) {
                CollectionItemRow(
                    index = items.indexOfFirst { it.seriesId == item.seriesId } + 1,
                    series = series,
                    onMoveUp = { viewModel.moveCollectionItem(collectionId, item.seriesId, -1) },
                    onMoveDown = { viewModel.moveCollectionItem(collectionId, item.seriesId, 1) },
                    onRemove = { viewModel.removeSeriesFromCollection(collectionId, item.seriesId) }
                )
            }
        }
        if (items.isEmpty()) {
            item {
                Text(
                    "还没有内容，点击「添加内容」从媒体库中选择。",
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 18.dp)
                )
            }
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "删除合集",
            message = "只会移除这个合集，不会删除视频文件。确定删除「${collection.name}」吗？",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                viewModel.deleteCollection(collectionId)
                showDelete = false
                onBack()
            },
            onDismiss = { showDelete = false }
        )
    }
}

@Composable
private fun CollectionItemRow(
    index: Int,
    series: SeriesEntity,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(JuyingColors.Surface.copy(alpha = 0.32f))
            .border(1.dp, JuyingColors.Outline.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            index.toString().padStart(2, '0'),
            color = JuyingColors.TextMuted,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(40.dp)
        )
        Box(Modifier.width(104.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
            SeriesArtwork(series, Modifier.fillMaxSize(), showTitleFallback = false)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                series.displayTitle,
                color = JuyingColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                collectionKindLabel(series),
                color = JuyingColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvChip("上移", onClick = onMoveUp)
            TvChip("下移", onClick = onMoveDown)
            TvChip("移除", onClick = onRemove)
        }
    }
}

@Composable
fun CollectionPickerScreen(
    viewModel: AppViewModel,
    collectionId: String,
    onDone: () -> Unit
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val items by viewModel.collectionItemsFlow(collectionId).collectAsStateWithLifecycle(emptyList())
    val allSeries by viewModel.series.collectAsStateWithLifecycle()
    val collection = collections.firstOrNull { it.id == collectionId }
    val selectedIds = remember(items) { items.map { it.seriesId }.toSet() }
    val firstFocus = remember { FocusRequester() }
    RegisterScreenFocus(firstFocus)

    LaunchedEffect(Unit) {
        delay(180)
        runCatching { firstFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "添加内容到「${collection?.name ?: "合集"}」",
                    color = JuyingColors.TextPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "点击海报即可加入或移出，已完成 ${selectedIds.size} 部",
                    color = JuyingColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TvButton("完成", onDone, focusRequester = firstFocus)
        }
        Spacer(Modifier.height(20.dp))
        if (allSeries.isEmpty()) {
            EmptyState(
                title = "媒体库还没有内容",
                description = "先扫描媒体源，再把归集好的剧集加入合集。",
                actionText = "完成",
                action = onDone
            )
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(6),
                state = rememberTvLazyGridState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(allSeries, key = { it.id }) { series ->
                    val selected = selectedIds.contains(series.id)
                    PosterCard(
                        series = series,
                        onClick = {
                            if (selected) viewModel.removeSeriesFromCollection(collectionId, series.id)
                            else viewModel.addSeriesToCollection(collectionId, series.id)
                        },
                        selected = selected
                    )
                }
            }
        }
    }
}

@Composable
fun HiddenManageScreen(
    viewModel: AppViewModel,
    onOpenSeries: (String) -> Unit,
    onBack: () -> Unit
) {
    val hidden by viewModel.hiddenSeries.collectAsStateWithLifecycle()
    val backFocus = remember { FocusRequester() }
    RegisterScreenFocus(backFocus)
    TvLazyColumn(
        state = rememberTvLazyListState(),
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "已隐藏的内容",
                        color = JuyingColors.TextPrimary,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "隐藏不会删除文件，重新扫描也不会自动恢复。",
                        color = JuyingColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TvChip("返回", onClick = onBack, icon = Icons.Rounded.ArrowBack, focusRequester = backFocus)
            }
        }
        if (hidden.isEmpty()) {
            item {
                Text(
                    "没有隐藏的内容。",
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 30.dp)
                )
            }
        }
        listItems(hidden, key = { it.id }) { series ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(JuyingColors.Surface.copy(alpha = 0.32f))
                    .border(1.dp, JuyingColors.Outline.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(118.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                    SeriesArtwork(series, Modifier.fillMaxSize(), showTitleFallback = false)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(series.displayTitle, color = JuyingColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(collectionKindLabel(series), color = JuyingColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                TvChip("查看", onClick = { onOpenSeries(series.id) })
                Spacer(Modifier.width(8.dp))
                TvChip("恢复显示", selected = true, onClick = { viewModel.restoreSeries(series.id) })
            }
        }
    }
}

private fun collectionKindLabel(series: SeriesEntity): String = listOfNotNull(
    if (series.kind == com.juying.tv.data.db.SeriesKind.MOVIE) "电影" else "剧集",
    series.year?.toString()
).joinToString(" · ")
