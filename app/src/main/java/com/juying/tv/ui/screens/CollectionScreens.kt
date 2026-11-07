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
import androidx.compose.material.icons.rounded.Folder
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
import com.juying.tv.data.db.MediaPathRow
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.ui.components.Badge
import com.juying.tv.ui.components.CheckMark
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
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val mediaPaths by viewModel.mediaPaths.collectAsStateWithLifecycle()
    val collection = collections.firstOrNull { it.id == collectionId }
    val selectedIds = remember(items) { items.map { it.seriesId }.toSet() }
    val sourcesWithMedia = remember(sources, mediaPaths) {
        sources.filter { source -> mediaPaths.any { it.sourceId == source.id } }
    }

    var mode by remember { mutableStateOf(PickerMode.DIRECTORY) }
    var selectedSourceId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sourcesWithMedia) {
        if (selectedSourceId == null || sourcesWithMedia.none { it.id == selectedSourceId }) {
            selectedSourceId = sourcesWithMedia.firstOrNull()?.id
        }
    }

    val folderIndex = remember(mediaPaths, selectedSourceId) {
        buildFolderIndex(mediaPaths, selectedSourceId)
    }
    var currentPath by remember(selectedSourceId) { mutableStateOf("/") }
    LaunchedEffect(selectedSourceId, folderIndex.initialPath) {
        if (currentPath == "/" || currentPath !in folderIndex.folders) {
            currentPath = folderIndex.initialPath
        }
    }
    val doneFocus = remember { FocusRequester() }
    val folderFocus = remember { FocusRequester() }
    RegisterScreenFocus(doneFocus)

    LaunchedEffect(mode, currentPath, folderIndex) {
        delay(150)
        if (mode == PickerMode.DIRECTORY) {
            runCatching { folderFocus.requestFocus() }
        } else {
            runCatching { doneFocus.requestFocus() }
        }
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
                    if (mode == PickerMode.DIRECTORY) {
                        "按媒体源的原始目录批量加入，合集仍以剧集卡片展示；已选 ${selectedIds.size} 部"
                    } else {
                        "点击海报加入或移出，已选 ${selectedIds.size} 部"
                    },
                    color = JuyingColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TvChip("按原始目录", selected = mode == PickerMode.DIRECTORY, onClick = { mode = PickerMode.DIRECTORY })
            Spacer(Modifier.width(8.dp))
            TvChip("按剧集", selected = mode == PickerMode.SERIES, onClick = { mode = PickerMode.SERIES })
            Spacer(Modifier.width(12.dp))
            TvButton("完成", onDone, focusRequester = doneFocus)
        }
        Spacer(Modifier.height(14.dp))

        if (mediaPaths.isEmpty() && allSeries.isEmpty()) {
            EmptyState(
                title = "媒体库还没有内容",
                description = "先扫描媒体源，再按目录或剧集把内容加入合集。",
                actionText = "完成",
                action = onDone
            )
            return@Column
        }

        if (mode == PickerMode.SERIES) {
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
        } else {
            DirectoryCollectionPicker(
                sources = sourcesWithMedia,
                selectedSourceId = selectedSourceId,
                onSelectSource = { selectedSourceId = it },
                folderIndex = folderIndex,
                currentPath = currentPath,
                onNavigate = { currentPath = it },
                selectedSeriesIds = selectedIds,
                onToggleFolder = { seriesIds, add ->
                    viewModel.toggleFolderInCollection(collectionId, seriesIds, add)
                },
                folderFocus = folderFocus
            )
        }
    }
}

private enum class PickerMode { DIRECTORY, SERIES }

private data class FolderInfo(
    val path: String,
    val name: String,
    val children: List<String>,
    val seriesIds: Set<String>,
    val videoCount: Int
)

private data class FolderIndex(
    val folders: Map<String, FolderInfo>,
    val initialPath: String
)

private fun buildFolderIndex(mediaPaths: List<MediaPathRow>, sourceId: String?): FolderIndex {
    if (sourceId == null) return FolderIndex(emptyMap(), "/")
    val rows = mediaPaths.filter { it.sourceId == sourceId }
    if (rows.isEmpty()) return FolderIndex(emptyMap(), "/")
    val allPaths = linkedSetOf("/")
    val seriesByPath = HashMap<String, MutableSet<String>>()
    val videoCountByPath = HashMap<String, Int>()
    rows.forEach { row ->
        var path = row.parentPath
        while (true) {
            allPaths += path
            seriesByPath.getOrPut(path) { mutableSetOf() } += row.seriesId
            videoCountByPath[path] = (videoCountByPath[path] ?: 0) + row.videoCount
            if (path == "/") break
            path = parentOfPath(path)
        }
    }
    val childrenByParent = allPaths.filter { it != "/" }
        .groupBy { parentOfPath(it) }
        .mapValues { (_, children) -> children.sortedWith(compareBy { folderName(it).lowercase() }) }
    val folders = allPaths.associateWith { path ->
        FolderInfo(
            path = path,
            name = if (path == "/") "全部目录" else folderName(path),
            children = childrenByParent[path].orEmpty(),
            seriesIds = seriesByPath[path].orEmpty(),
            videoCount = videoCountByPath[path] ?: 0
        )
    }
    val top = childrenByParent["/"].orEmpty()
    val initial = if (top.size == 1) top.first() else "/"
    return FolderIndex(folders, initial)
}

private fun folderName(path: String): String = path.trim('/').substringAfterLast('/')

private fun parentOfPath(path: String): String {
    val trimmed = path.trim('/')
    if (!trimmed.contains('/')) return "/"
    return "/" + trimmed.substringBeforeLast('/')
}

@Composable
private fun DirectoryCollectionPicker(
    sources: List<com.juying.tv.data.db.SourceEntity>,
    selectedSourceId: String?,
    onSelectSource: (String) -> Unit,
    folderIndex: FolderIndex,
    currentPath: String,
    onNavigate: (String) -> Unit,
    selectedSeriesIds: Set<String>,
    onToggleFolder: (Set<String>, Boolean) -> Unit,
    folderFocus: FocusRequester
) {
    val current = folderIndex.folders[currentPath]
        ?: folderIndex.folders[folderIndex.initialPath]
        ?: return
    val currentAllSelected = current.seriesIds.isNotEmpty() && current.seriesIds.all { it in selectedSeriesIds }
    val currentPartial = current.seriesIds.any { it in selectedSeriesIds } && !currentAllSelected

    Column(Modifier.fillMaxSize()) {
        if (sources.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("媒体源", color = JuyingColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                sources.forEach { source ->
                    TvChip(source.name, selected = source.id == selectedSourceId, onClick = { onSelectSource(source.id) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JuyingColors.Surface.copy(alpha = 0.32f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPath != "/") {
                TvChip("上一层", onClick = { onNavigate(parentOfPath(currentPath)) })
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    current.name,
                    color = JuyingColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${currentPath} · ${current.seriesIds.size} 部剧 / ${current.videoCount} 个视频",
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (current.seriesIds.isNotEmpty()) {
                TvChip(
                    if (currentAllSelected) "已加入当前目录" else if (currentPartial) "补齐当前目录" else "加入当前目录",
                    selected = currentAllSelected,
                    onClick = { onToggleFolder(current.seriesIds, !currentAllSelected) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "选择目录会把该目录及子目录下的剧集批量加入合集；需要单集/单剧选择时切到“按剧集”。",
            color = JuyingColors.TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))

        TvLazyColumn(
            state = rememberTvLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (current.children.isEmpty()) {
                item {
                    Text(
                        "此目录没有子文件夹，可直接加入当前目录。",
                        color = JuyingColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                }
            }
            listItems(current.children, key = { it }) { childPath ->
                val child = folderIndex.folders[childPath] ?: return@listItems
                val allSelected = child.seriesIds.isNotEmpty() && child.seriesIds.all { it in selectedSeriesIds }
                val partial = child.seriesIds.any { it in selectedSeriesIds } && !allSelected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (allSelected) JuyingColors.Gold.copy(alpha = 0.08f)
                            else JuyingColors.Surface.copy(alpha = 0.32f)
                        )
                        .border(
                            1.dp,
                            if (allSelected) JuyingColors.Gold.copy(alpha = 0.4f)
                            else JuyingColors.Outline.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FocusableSurface(
                        onClick = { onToggleFolder(child.seriesIds, !allSelected) },
                        modifier = Modifier
                            .size(34.dp)
                            .then(
                                if (childPath == current.children.firstOrNull()) Modifier.focusRequester(folderFocus)
                                else Modifier
                            ),
                        shape = RoundedCornerShape(8.dp),
                        focusedScale = 1.08f,
                        backgroundColor = Color.Transparent,
                        focusedBackgroundColor = JuyingColors.SurfaceFocused,
                        borderColor = Color.Transparent,
                        focusedBorderColor = JuyingColors.Gold
                    ) {
                        CheckMark(allSelected, Modifier.align(Alignment.Center).size(24.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = if (allSelected) JuyingColors.Gold else JuyingColors.TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            child.name,
                            color = JuyingColors.TextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildString {
                                append("${child.seriesIds.size} 部剧 / ${child.videoCount} 个视频")
                                if (partial) append(" · 部分已加入")
                                else if (allSelected) append(" · 已全部加入")
                            },
                            color = if (allSelected) JuyingColors.Gold else JuyingColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TvChip("进入", onClick = { onNavigate(childPath) })
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
