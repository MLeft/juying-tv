package com.juying.tv.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.juying.tv.JuyingApp
import com.juying.tv.data.source.QuarkConfig
import com.juying.tv.data.source.QuarkRoot
import com.juying.tv.data.source.quark.QuarkClient
import com.juying.tv.ui.DialogTracker
import com.juying.tv.ui.components.CheckMark
import com.juying.tv.ui.components.FocusableSurface
import com.juying.tv.ui.components.TvButton
import com.juying.tv.ui.components.TvChip
import com.juying.tv.ui.theme.JuyingColors
import kotlinx.coroutines.delay

/**
 * 夸克网盘目录选择器：直接浏览目录、多选扫描范围，避免只能手填 fid。
 */
@Composable
fun QuarkFolderPickerDialog(
    config: QuarkConfig,
    initialSelection: List<QuarkRoot>,
    onDismiss: () -> Unit,
    onConfirm: (List<QuarkRoot>) -> Unit
) {
    val context = LocalContext.current
    val client = remember { JuyingApp.graphOf(context).quark }
    androidx.compose.runtime.DisposableEffect(Unit) {
        DialogTracker.acquire()
        onDispose { DialogTracker.release() }
    }
    val stack = remember { mutableStateListOf(QuarkRoot("0", "夸克网盘")) }
    var dirs by remember { mutableStateOf<List<QuarkClient.QuarkDir>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember {
        mutableStateOf(
            linkedMapOf<String, QuarkRoot>().apply {
                initialSelection.forEach { put(it.fid, it) }
            }
        )
    }
    val dirFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val current = stack.last()
    val isRoot = current.fid == "0"

    LaunchedEffect(current.fid, reload) {
        loading = true
        error = null
        try {
            dirs = client.listDirectories(config, current.fid)
        } catch (e: Exception) {
            error = e.message ?: "读取夸克目录失败"
            dirs = emptyList()
        } finally {
            loading = false
        }
        delay(160)
        runCatching {
            if (dirs.isNotEmpty()) dirFocus.requestFocus() else cancelFocus.requestFocus()
        }
    }

    fun toggle(fid: String, name: String) {
        val next = LinkedHashMap(selected)
        if (next.containsKey(fid)) next.remove(fid) else next[fid] = QuarkRoot(fid, name)
        selected = next
    }

    fun goUp() {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .width(900.dp)
                    .height(500.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF152134), Color(0xFF0D1624)))
                    )
                    .border(1.dp, JuyingColors.Outline, RoundedCornerShape(24.dp))
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "选择夸克扫描目录",
                            color = JuyingColors.TextPrimary,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "可多选文件夹，只扫描选中的目录；不选则扫描整个网盘",
                            color = JuyingColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TvChip("刷新", onClick = { reload++ }, icon = Icons.Rounded.Refresh)
                }
                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(JuyingColors.Surface.copy(alpha = 0.3f))
                            .border(1.dp, JuyingColors.Outline.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isRoot) {
                                TvChip("上一层", onClick = { goUp() }, icon = Icons.Rounded.ArrowBack)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                stack.joinToString(" / ") { it.name },
                                color = JuyingColors.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isRoot) {
                                Spacer(Modifier.width(8.dp))
                                val checked = selected.containsKey(current.fid)
                                TvChip(
                                    if (checked) "取消选择当前目录" else "选择当前目录",
                                    selected = checked,
                                    onClick = { toggle(current.fid, current.name) }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "当前目录 ${dirs.size} 个文件夹",
                            color = JuyingColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(6.dp))

                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                loading -> Column(
                                    Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = JuyingColors.Gold,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text("正在读取目录…", color = JuyingColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                error != null -> Column(
                                    Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        error.orEmpty(),
                                        color = JuyingColors.Red,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Cookie 失效或网络异常时，请返回修改 Cookie 后重试。",
                                        color = JuyingColors.TextMuted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    TvChip("重试", onClick = { reload++ })
                                }
                                dirs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("此目录没有子文件夹", color = JuyingColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                                }
                                else -> LazyColumn(
                                    state = rememberLazyListState(),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(dirs, key = { it.fid }) { dir ->
                                        val checked = selected.containsKey(dir.fid)
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (checked) JuyingColors.Gold.copy(alpha = 0.1f)
                                                    else JuyingColors.Surface.copy(alpha = 0.32f)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (checked) JuyingColors.Gold.copy(alpha = 0.4f)
                                                    else JuyingColors.Outline.copy(alpha = 0.35f),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FocusableSurface(
                                                onClick = { toggle(dir.fid, dir.name) },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .then(
                                                        if (dir.fid == dirs.firstOrNull()?.fid) Modifier.focusRequester(dirFocus)
                                                        else Modifier
                                                    ),
                                                shape = RoundedCornerShape(8.dp),
                                                focusedScale = 1.08f,
                                                backgroundColor = Color.Transparent,
                                                focusedBackgroundColor = JuyingColors.SurfaceFocused,
                                                borderColor = Color.Transparent,
                                                focusedBorderColor = JuyingColors.Gold
                                            ) {
                                                CheckMark(checked, Modifier.align(Alignment.Center).size(24.dp))
                                            }
                                            Spacer(Modifier.width(9.dp))
                                            Icon(
                                                Icons.Rounded.Folder,
                                                contentDescription = null,
                                                tint = if (checked) JuyingColors.Gold else JuyingColors.TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                dir.name,
                                                color = JuyingColors.TextPrimary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (checked) {
                                                Text(
                                                    "已选",
                                                    color = JuyingColors.Gold,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            TvChip(
                                                "进入",
                                                onClick = { stack += QuarkRoot(dir.fid, dir.name) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        Modifier
                            .width(285.dp)
                            .fillMaxSize()
                    ) {
                        Text(
                            "已选 ${selected.size} 个目录",
                            color = JuyingColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (selected.isEmpty()) "未选择时扫描整个网盘" else "只扫描这些目录及其子目录",
                            color = JuyingColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(JuyingColors.Surface.copy(alpha = 0.28f))
                                .padding(8.dp)
                        ) {
                            if (selected.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "建议选择具体动画/影视文件夹，避免整个网盘文件过多",
                                        color = JuyingColors.TextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = rememberLazyListState(),
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    items(selected.values.toList(), key = { it.fid }) { root ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(JuyingColors.Gold.copy(alpha = 0.08f))
                                                .padding(horizontal = 9.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Rounded.Folder,
                                                contentDescription = null,
                                                tint = JuyingColors.Gold,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.width(7.dp))
                                            Text(
                                                root.name,
                                                color = JuyingColors.TextPrimary,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TvChip("移除", onClick = { toggle(root.fid, root.name) })
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvChip(
                                "清空",
                                onClick = { selected = linkedMapOf() }
                            )
                            TvChip(
                                "取消",
                                onClick = onDismiss,
                                focusRequester = cancelFocus
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        TvButton(
                            text = if (selected.isEmpty()) "使用整个网盘" else "保存选择（${selected.size}）",
                            onClick = { onConfirm(selected.values.toList()) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
