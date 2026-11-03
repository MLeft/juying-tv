package com.juying.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 电视端输入法/弹窗关闭后，Compose 可能短暂失去焦点。
 * 这里记录每个页面“首选焦点”，在遥控器按键到达但全窗口无焦点时恢复。
 */
@Stable
class FocusCoordinator {
    var hasFocus by mutableStateOf(false)
    var entryRequester by mutableStateOf<FocusRequester?>(null)
    private var owner: FocusRequester? = null
    private var restoreAction: (() -> Unit)? = null

    fun register(requester: FocusRequester, action: () -> Unit) {
        owner = requester
        entryRequester = requester
        restoreAction = action
    }

    fun unregister(requester: FocusRequester) {
        if (owner === requester) {
            owner = null
            restoreAction = null
            entryRequester = null
        }
    }

    fun restore() {
        restoreAction?.invoke()
    }
}

val LocalFocusCoordinator = staticCompositionLocalOf { FocusCoordinator() }

/** 记录当前是否有 Dialog 窗口打开，用于避免返回键同时关闭弹窗和退出页面。 */
object DialogTracker {
    var openCount by mutableStateOf(0)
        private set
    val isOpen: Boolean get() = openCount > 0

    fun acquire() {
        openCount++
    }

    fun release() {
        openCount = (openCount - 1).coerceAtLeast(0)
    }
}

@Composable
fun RegisterScreenFocus(requester: FocusRequester) {
    val coordinator = LocalFocusCoordinator.current
    val scope = rememberCoroutineScope()
    DisposableEffect(coordinator, requester) {
        coordinator.register(requester) { runCatching { requester.requestFocus() } }
        // 启动初期窗口焦点尚未稳定时，requestFocus 可能无效；在用户还没把焦点移到别处前重试。
        val job = scope.launch {
            listOf(250L, 700L, 1500L, 2500L).forEach { wait ->
                delay(wait)
                if (!coordinator.hasFocus) {
                    runCatching { requester.requestFocus() }
                }
            }
        }
        onDispose {
            job.cancel()
            coordinator.unregister(requester)
        }
    }
}
