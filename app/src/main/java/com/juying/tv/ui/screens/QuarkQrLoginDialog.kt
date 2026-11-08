package com.juying.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juying.tv.data.source.quark.QuarkQrAuth
import com.juying.tv.ui.components.TvButton
import com.juying.tv.ui.theme.JuyingColors
import com.juying.tv.util.generateQrBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/**
 * 夸克 App 扫码登录：电视只展示二维码，手机扫码确认后自动换取 Cookie。
 * 不依赖电脑 F12，也不依赖系统 WebView。
 */
@Composable
fun QuarkQrLoginDialog(
    onDismiss: () -> Unit,
    onCookie: (String) -> Unit
) {
    var status by remember { mutableStateOf("正在生成二维码…") }
    var error by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val cancelFocus = remember { FocusRequester() }
    val client = remember { OkHttpClient() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { cancelFocus.requestFocus() }
    }

    LaunchedEffect(refreshTick) {
        status = "正在生成二维码…"
        error = false
        qr = null
        val auth = QuarkQrAuth(client)
        try {
            while (true) {
                val session = auth.createSession()
                qr = generateQrBitmap(session.scanUrl, 640)
                var secondsLeft = 120
                status = "请用夸克 App 扫码（$secondsLeft 秒后自动刷新）"
                var refresh = false
                while (true) {
                    delay(2_000)
                    secondsLeft -= 2
                    if (secondsLeft <= 0) {
                        refresh = true
                        break
                    }
                    when (val result = auth.poll(session.token)) {
                        QuarkQrAuth.PollResult.Pending -> {
                            status = "请用夸克 App 扫码（$secondsLeft 秒后自动刷新）"
                        }
                        QuarkQrAuth.PollResult.Expired -> {
                            refresh = true
                            break
                        }
                        is QuarkQrAuth.PollResult.Confirmed -> {
                            status = "已确认，正在获取登录信息…"
                            val cookie = auth.exchange(result.ticket)
                            onCookie(cookie)
                            return@LaunchedEffect
                        }
                    }
                }
                if (!refresh) break
                status = "二维码已自动刷新，请重新扫码"
                error = false
                delay(600)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = e.message ?: "扫码登录失败，请重试"
            error = true
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
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                Modifier
                    .width(900.dp)
                    .height(470.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF152134), Color(0xFF0D1624))))
                    .border(1.dp, JuyingColors.Outline, RoundedCornerShape(24.dp))
                    .padding(22.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0A111D))
                        .border(1.dp, JuyingColors.Outline.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = qr
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "夸克登录二维码",
                            modifier = Modifier
                                .size(250.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(10.dp)
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = JuyingColors.Gold,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "正在生成二维码…",
                                color = JuyingColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.width(250.dp).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.QrCode2,
                            contentDescription = null,
                            tint = JuyingColors.Gold,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "扫码登录",
                            color = JuyingColors.TextPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "不用电脑，也不用按 F12。用夸克 App 扫描左侧二维码并确认即可。",
                        color = JuyingColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(14.dp))
                    LoginStep(1, "打开手机夸克 App")
                    LoginStep(2, "点右上角“扫一扫”")
                    LoginStep(3, "扫描并确认登录")
                    Spacer(Modifier.weight(1f))
                    Text(
                        status,
                        color = when {
                            error -> JuyingColors.Red
                            status.startsWith("已确认") || status.startsWith("已获取") -> JuyingColors.Green
                            else -> JuyingColors.Gold
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(10.dp))
                    TvButton(
                        "刷新二维码",
                        {
                            refreshTick++
                        },
                        primary = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    TvButton(
                        "取消",
                        onDismiss,
                        primary = false,
                        focusRequester = cancelFocus,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "二维码每 2 分钟自动刷新；扫码后请在手机上点“确认登录”。",
                        color = JuyingColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginStep(index: Int, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(JuyingColors.Gold.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                index.toString(),
                color = JuyingColors.Gold,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = JuyingColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
