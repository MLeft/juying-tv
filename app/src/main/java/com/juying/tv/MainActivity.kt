package com.juying.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juying.tv.mcp.McpServerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.juying.tv.ui.AppRoot
import com.juying.tv.ui.components.JuyingFocusScrollSpec
import com.juying.tv.ui.theme.JuyingTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        hideSystemBars()
        lifecycleScope.launch {
            if (JuyingApp.instance.graph.settings.mcpEnabled.first()) {
                McpServerService.start(this@MainActivity)
            }
        }

        setContent {
            JuyingTheme {
                val density = LocalDensity.current
                val focusScrollSpec = remember(density) {
                    JuyingFocusScrollSpec(edgeMarginPx = with(density) { 64.dp.toPx() })
                }
                CompositionLocalProvider(
                    LocalBringIntoViewSpec provides focusScrollSpec
                ) {
                    AppRoot(viewModel())
                }
            }
        }
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}
