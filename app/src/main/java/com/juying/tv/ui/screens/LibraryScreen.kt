package com.juying.tv.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

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
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items as listItems
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import coil.compose.AsyncImage
import com.juying.tv.AppViewModel
import com.juying.tv.data.db.CollectionEntity
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.data.db.SeriesKind
import com.juying.tv.ui.components.Badge
import com.juying.tv.ui.components.ConfirmDialog
import com.juying.tv.ui.components.EmptyState
import com.juying.tv.ui.components.FocusableSurface
import com.juying.tv.ui.components.PosterCard
import com.juying.tv.ui.components.SeriesArtwork
import com.juying.tv.ui.components.TextInputDialog
import com.juying.tv.ui.components.TogglePill
import com.juying.tv.ui.components.TvButton
import com.juying.tv.ui.components.TvChip
import com.juying.tv.ui.theme.JuyingColors
import kotlinx.coroutines.delay

private enum class LibraryTab(val label: String) {
    COLLECTIONS("合集"),
    ALL("全部"),
    MANAGE("管理")
}

@Composable
fun LibraryScreen(
    viewModel: AppViewModel,
    kidsMode: Boolean,
    onOpenSeries: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onEditCollection: (String) -> Unit,
    onOpenHidden: () -> Unit,
    onOpenKidsManage: () -> Unit,
    onOpenSources: () -> Unit
) {
    val allSeries by viewModel.series.collectAsStateWithLifecycle()
    val kidsSeries by viewModel.kidsSeries.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val collectionItems by viewModel.collectionItems.collectAsStateWithLifecycle()
    val hiddenSeries by viewModel.hiddenSeries.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    val visible = if (kidsMode) kidsSeries else allSeries
    val visibleById = remember(visible) { visible.associateBy { it.id } }
    var tabName by rememberSaveable { mutableStateOf(if (kidsMode) LibraryTab.ALL.name else LibraryTab.COLLECTIONS.name) }
    val tab = LibraryTab.valueOf(tabName)
    var showCreate by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var kindFilter by rememberSaveable { mutableStateOf("ALL") }
    var deleteTarget by remember { mutableStateOf<CollectionEntity?>(null) }
    val filtered = remember(visible, searchQuery, kindFilter) {
        visible.filter { series ->
            val kindOk = when (kindFilter) {
                "SERIES" -> series.kind == SeriesKind.SERIES
                "MOVIE" -> series.kind == SeriesKind.MOVIE
                else -> true
            }
            val queryOk = searchQuery.isBlank() ||
                series.displayTitle.contains(searchQuery.trim(), ignoreCase = true)
            kindOk && queryOk
        }
    }
    val hasActiveFilter = searchQuery.isNotBlank() || kindFilter != "ALL"
    val firstTabFocus = remember { FocusRequester() }
    val firstContentFocus = remember { FocusRequester() }
    RegisterScreenFocus(if (kidsMode) firstContentFocus else firstTabFocus)

    LaunchedEffect(Unit) {
        delay(180)
        if (kidsMode) runCatching { firstContentFocus.requestFocus() }
        else runCatching { firstTabFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(start = 42.dp, end = 42.dp, top = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (kidsMode) "片库" else "媒体库",
                color = JuyingColors.TextPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(16.dp))
            Badge("${visible.size} 部内容", JuyingColors.Blue)
            if (!kidsMode && hiddenSeries.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Badge("${hiddenSeries.size} 部已隐藏", JuyingColors.TextSecondary)
            }
            Spacer(Modifier.weight(1f))
            if (scanState?.running == true) {
                Text(
                    "正在扫描 · ${scanState?.files ?: 0}",
                    color = JuyingColors.Gold,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.width(14.dp))
            }
            TvChip("扫描全部", onClick = { viewModel.scanAll() }, icon = Icons.Rounded.Refresh)
        }

        Spacer(Modifier.height(18.dp))

        if (!kidsMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LibraryTab.values().forEachIndexed { index, item ->
                    TvChip(
                        text = item.label,
                        selected = tab == item,
                        onClick = { tabName = item.name },
                        focusRequester = if (index == 0) firstTabFocus else null
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (kidsMode || tab == LibraryTab.ALL) {
            SearchFilterRow(
                query = searchQuery,
                kind = kindFilter,
                count = filtered.size,
                onSearch = { showSearch = true },
                onQueryChange = { searchQuery = it },
                onKindChange = { kindFilter = it }
            )
            Spacer(Modifier.height(14.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                kidsMode -> {
                    if (filtered.isEmpty() && visible.isNotEmpty() && hasActiveFilter) {
                        EmptyState(
                            title = "没有匹配的内容",
                            description = "试试其他关键词，或清除筛选条件。",
                            actionText = "清除筛选",
                            action = { searchQuery = ""; kindFilter = "ALL" },
                            focusRequester = firstContentFocus
                        )
                    } else {
                        AllSeriesGrid(
                            series = filtered,
                            onOpenSeries = onOpenSeries,
                            emptyAction = { viewModel.scanAll() },
                            firstFocus = firstContentFocus
                        )
                    }
                }
                tab == LibraryTab.COLLECTIONS -> CollectionsContent(
                    collections = collections,
                    collectionItems = collectionItems,
                    seriesById = visibleById,
                    onCreate = { showCreate = true },
                    onOpenCollection = onOpenCollection,
                    onEditCollection = onEditCollection,
                    firstContentFocus = firstContentFocus
                )
                tab == LibraryTab.ALL -> {
                    if (filtered.isEmpty() && visible.isNotEmpty() && hasActiveFilter) {
                        EmptyState(
                            title = "没有匹配的内容",
                            description = "试试其他关键词，或清除筛选条件。",
                            actionText = "清除筛选",
                            action = { searchQuery = ""; kindFilter = "ALL" },
                            focusRequester = firstContentFocus
                        )
                    } else {
                        AllSeriesGrid(
                            series = filtered,
                            onOpenSeries = onOpenSeries,
                            emptyAction = onOpenSources,
                            firstFocus = firstContentFocus
                        )
                    }
                }
                else -> ManageContent(
                    collections = collections,
                    collectionItems = collectionItems,
                    hiddenCount = hiddenSeries.size,
                    firstFocus = firstContentFocus,
                    onMoveCollection = { id, delta -> viewModel.moveCollection(id, delta) },
                    onToggleHome = { id, show -> viewModel.setCollectionShowOnHome(id, show) },
                    onEditCollection = onEditCollection,
                    onDeleteCollection = { deleteTarget = it },
                    onOpenHidden = onOpenHidden,
                    onOpenKidsManage = onOpenKidsManage,
                    onOpenSources = onOpenSources,
                    onScan = { viewModel.scanAll() }
                )
            }
        }
    }

    if (showSearch) {
        TextInputDialog(
            title = "搜索媒体库",
            label = "片名关键词",
            initialValue = searchQuery,
            placeholder = "输入动画、剧集或电影名称",
            onDismiss = { showSearch = false },
            onSave = {
                searchQuery = it.trim()
                showSearch = false
            }
        )
    }

    if (showCreate) {
        TextInputDialog(
            title = "新建合集",
            label = "合集名称",
            initialValue = "",
            placeholder = "例如：周末动画、孩子必看",
            onDismiss = { showCreate = false },
            onSave = { name ->
                viewModel.createCollection(name) { collection ->
                    showCreate = false
                    onOpenCollection(collection.id)
                }
            }
        )
    }

    deleteTarget?.let { collection ->
        ConfirmDialog(
            title = "删除合集",
            message = "确定删除「${collection.name}」吗？不会删除合集里的视频，只会移除这个分组。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                viewModel.deleteCollection(collection.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun SearchFilterRow(
    query: String,
    kind: String,
    count: Int,
    onSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onKindChange: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FocusableSurface(
            onClick = onSearch,
            modifier = Modifier.width(250.dp).height(42.dp),
            shape = RoundedCornerShape(50),
            focusedScale = 1.02f,
            backgroundColor = JuyingColors.Surface.copy(alpha = 0.45f),
            focusedBackgroundColor = JuyingColors.SurfaceFocused
        ) {
            Row(
                Modifier.padding(horizontal = 15.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = JuyingColors.TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (query.isBlank()) "搜索片名" else query,
                    color = if (query.isBlank()) JuyingColors.TextMuted else JuyingColors.TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (query.isNotBlank()) {
            TvChip("清除", onClick = { onQueryChange("") })
        }
        TvChip("全部", selected = kind == "ALL", onClick = { onKindChange("ALL") })
        TvChip("剧集", selected = kind == "SERIES", onClick = { onKindChange("SERIES") })
        TvChip("电影", selected = kind == "MOVIE", onClick = { onKindChange("MOVIE") })
        Spacer(Modifier.weight(1f))
        Text(
            "$count 部",
            color = JuyingColors.TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CollectionsContent(
    collections: List<CollectionEntity>,
    collectionItems: List<com.juying.tv.data.db.CollectionItemEntity>,
    seriesById: Map<String, SeriesEntity>,
    onCreate: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onEditCollection: (String) -> Unit,
    firstContentFocus: FocusRequester
) {
    if (collections.isEmpty()) {
        EmptyState(
            title = "创建你的第一个合集",
            description = "把不同目录、不同网盘里的同一部动画或同主题内容放进合集，并按你的喜好排序。",
            actionText = "新建合集",
            action = onCreate,
            focusRequester = firstContentFocus
        )
        return
    }

    val seriesByCollection = remember(collectionItems, seriesById) {
        collectionItems.groupBy { it.collectionId }.mapValues { (_, items) ->
            items.sortedBy { it.sortIndex }.mapNotNull { seriesById[it.seriesId] }
        }
    }
    val restoreCollectionId = com.juying.tv.ui.NavFocusMemory.collectionId
    val restoreCollection = collections.firstOrNull { it.id == restoreCollectionId } ?: collections.firstOrNull()

    TvLazyVerticalGrid(
        columns = TvGridCells.Fixed(3),
        state = rememberTvLazyGridState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        val collectionTotal = collections.size + 1
        val lastCollectionRowStart = ((collectionTotal - 1) / 3) * 3
        itemsIndexed(collections, key = { _, it -> it.id }) { index, collection ->
            CollectionCard(
                collection = collection,
                series = seriesByCollection[collection.id].orEmpty(),
                onClick = { onOpenCollection(collection.id) },
                modifier = if (index >= lastCollectionRowStart) Modifier.blockDownAtBottom() else Modifier,
                focusRequester = if (collection.id == restoreCollection?.id) firstContentFocus else null
            )
        }
        item(key = "create") {
            FocusableSurface(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 8.4f)
                    .then(if (collections.size >= lastCollectionRowStart) Modifier.blockDownAtBottom() else Modifier),
                shape = RoundedCornerShape(20.dp),
                focusedScale = 1.03f,
                backgroundColor = JuyingColors.Surface.copy(alpha = 0.28f),
                focusedBackgroundColor = JuyingColors.SurfaceFocused
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(JuyingColors.Gold.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = JuyingColors.Gold, modifier = Modifier.size(25.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("新建合集", color = JuyingColors.Gold, style = MaterialTheme.typography.titleMedium)
                    Text("按自己的方式重新编排内容", color = JuyingColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(
    collection: CollectionEntity,
    series: List<SeriesEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val surfaceModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 8.4f)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged {
            if (it.isFocused) com.juying.tv.ui.NavFocusMemory.collectionId = collection.id
        }
    Column(modifier.padding(vertical = 4.dp)) {
        FocusableSurface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = RoundedCornerShape(18.dp),
            focusedScale = 1.035f
        ) {
            val cover = collection.coverUrl
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (series.isNotEmpty()) {
                SeriesArtwork(series.first(), Modifier.fillMaxSize(), showTitleFallback = false)
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF22344F), Color(0xFF101B2A)))
                    )
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.18f))
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp)
                    .fillMaxWidth(0.68f)
            ) {
                Text(
                    collection.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${series.size} 部内容 · 自定义合集",
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (collection.showOnHome) {
                Badge(
                    "首页",
                    JuyingColors.Gold,
                    Modifier.align(Alignment.TopEnd).padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun AllSeriesGrid(
    series: List<SeriesEntity>,
    onOpenSeries: (String) -> Unit,
    emptyAction: () -> Unit,
    firstFocus: FocusRequester? = null
) {
    if (series.isEmpty()) {
        EmptyState(
            title = "片库还是空的",
            description = "扫描媒体源后，视频会自动按剧集归集到这里。也可以直接添加本地文件夹或网盘。",
            actionText = "去添加媒体源",
            action = emptyAction
        )
        return
    }
    TvLazyVerticalGrid(
        columns = TvGridCells.Fixed(6),
        state = rememberTvLazyGridState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        val lastSeriesRowStart = ((series.size - 1) / 6) * 6
        val restoreId = com.juying.tv.ui.NavFocusMemory.seriesId
        val restoreSeries = series.firstOrNull { it.id == restoreId } ?: series.firstOrNull()
        itemsIndexed(series, key = { _, it -> it.id }) { index, item ->
            PosterCard(
                series = item,
                onClick = { onOpenSeries(item.id) },
                modifier = if (index >= lastSeriesRowStart) Modifier.blockDownAtBottom() else Modifier,
                focusRequester = if (item.id == restoreSeries?.id) firstFocus else null
            )
        }
    }
}

@Composable
private fun ManageContent(
    collections: List<CollectionEntity>,
    collectionItems: List<com.juying.tv.data.db.CollectionItemEntity>,
    hiddenCount: Int,
    firstFocus: FocusRequester,
    onMoveCollection: (String, Int) -> Unit,
    onToggleHome: (String, Boolean) -> Unit,
    onEditCollection: (String) -> Unit,
    onDeleteCollection: (CollectionEntity) -> Unit,
    onOpenHidden: () -> Unit,
    onOpenKidsManage: () -> Unit,
    onOpenSources: () -> Unit,
    onScan: () -> Unit
) {
    TvLazyColumn(
        state = rememberTvLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "合集顺序",
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        listItems(collections, key = { it.id }) { collection ->
            val count = collectionItems.count { it.collectionId == collection.id }
            CollectionManageRow(
                collection = collection,
                count = count,
                focusRequester = if (collection.id == collections.firstOrNull()?.id) firstFocus else null,
                onMoveUp = { onMoveCollection(collection.id, -1) },
                onMoveDown = { onMoveCollection(collection.id, 1) },
                onToggleHome = { onToggleHome(collection.id, !collection.showOnHome) },
                onEdit = { onEditCollection(collection.id) },
                onDelete = { onDeleteCollection(collection) }
            )
        }
        if (collections.isEmpty()) {
            item {
                Text(
                    "还没有合集。在「合集」页新建一个，就能把内容按自己的顺序排列。",
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            Text(
                "内容与家长控制",
                color = JuyingColors.TextSecondary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        item {
            ManageLinkRow(
                icon = Icons.Rounded.VisibilityOff,
                title = "已隐藏的内容",
                subtitle = if (hiddenCount == 0) "没有被隐藏的剧集" else "$hiddenCount 部内容已从媒体库隐藏",
                onClick = onOpenHidden
            )
        }
        item {
            ManageLinkRow(
                icon = Icons.Rounded.ChildCare,
                title = "儿童模式内容管理",
                subtitle = "选择孩子可以观看的剧集，支持按集全选",
                onClick = onOpenKidsManage
            )
        }
        item {
            ManageLinkRow(
                icon = Icons.Rounded.Refresh,
                title = "重新扫描与媒体源",
                subtitle = "扫描全部媒体源，或添加新的本地 / 网盘目录",
                onClick = onOpenSources
            )
        }
        item {
            ManageLinkRow(
                icon = Icons.Rounded.Collections,
                title = "立即扫描全部媒体源",
                subtitle = "重新归集新增的视频文件",
                onClick = onScan
            )
        }
    }
}

@Composable
private fun CollectionManageRow(
    collection: CollectionEntity,
    count: Int,
    focusRequester: FocusRequester?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleHome: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(JuyingColors.Surface.copy(alpha = 0.34f))
            .border(1.dp, JuyingColors.Outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        collection.name,
                        color = JuyingColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "$count 部",
                        color = JuyingColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    if (collection.showOnHome) "显示在首页" else "仅在媒体库显示",
                    color = if (collection.showOnHome) JuyingColors.Gold else JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvChip("上移", onClick = onMoveUp, focusRequester = focusRequester)
                TvChip("下移", onClick = onMoveDown)
                TvChip(if (collection.showOnHome) "首页显示" else "仅媒体库", selected = collection.showOnHome, onClick = onToggleHome)
                TvChip("编辑", onClick = onEdit)
                TvChip("删除", onClick = onDelete)
            }
        }
    }
}

@Composable
private fun ManageLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.006f,
        backgroundColor = JuyingColors.Surface.copy(alpha = 0.34f),
        focusedBackgroundColor = JuyingColors.SurfaceFocused
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(JuyingColors.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = JuyingColors.Gold, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = JuyingColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = JuyingColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = JuyingColors.TextMuted, fontSize = 22.sp)
        }
    }
}


private fun Modifier.blockDownAtBottom(): Modifier = onPreviewKeyEvent { event ->
    event.type == KeyEventType.KeyDown &&
        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
}
