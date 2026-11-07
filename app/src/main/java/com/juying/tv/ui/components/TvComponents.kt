package com.juying.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.juying.tv.JuyingApp
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.ui.theme.JuyingColors
import com.juying.tv.ui.util.PhoneField
import com.juying.tv.ui.util.PhoneInputServer
import com.juying.tv.util.generateQrBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    focusedScale: Float = 1.04f,
    focusedBorderColor: Color = JuyingColors.Gold,
    backgroundColor: Color = JuyingColors.Surface.copy(alpha = 0.55f),
    focusedBackgroundColor: Color = JuyingColors.SurfaceFocused,
    borderColor: Color = JuyingColors.Outline.copy(alpha = 0.65f),
    borderWidth: Dp = 1.dp,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f),
        label = "focusScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (focused) 18.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 520f),
        label = "focusElevation"
    )
    val border by androidx.compose.animation.animateColorAsState(
        targetValue = if (focused) focusedBorderColor else borderColor,
        animationSpec = tween(150),
        label = "focusBorder"
    )
    val background by androidx.compose.animation.animateColorAsState(
        targetValue = if (focused) focusedBackgroundColor else backgroundColor,
        animationSpec = tween(150),
        label = "focusBackground"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation.toPx()
                this.shape = shape
                clip = false
            }
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else borderWidth, border, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        content = content
    )
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true
) {
    val base = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier
    FocusableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = base.height(48.dp),
        shape = RoundedCornerShape(50),
        focusedScale = 1.05f,
        backgroundColor = if (primary) JuyingColors.Gold.copy(alpha = 0.92f) else JuyingColors.SurfaceHigh,
        focusedBackgroundColor = if (primary) JuyingColors.GoldBright else JuyingColors.SurfaceFocused,
        borderColor = if (primary) Color.Transparent else JuyingColors.Outline,
        focusedBorderColor = if (primary) Color.White.copy(alpha = 0.8f) else JuyingColors.Gold
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 22.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (primary) JuyingColors.OnGold else JuyingColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = text,
                color = if (primary) JuyingColors.OnGold else JuyingColors.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

@Composable
fun TvChip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    icon: ImageVector? = null,
    centered: Boolean = false
) {
    val base = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) JuyingColors.Gold else JuyingColors.TextSecondary,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "chipContent"
    )
    FocusableSurface(
        onClick = onClick,
        modifier = base.height(44.dp),
        shape = RoundedCornerShape(50),
        focusedScale = 1.05f,
        backgroundColor = if (selected) JuyingColors.Gold.copy(alpha = 0.16f) else JuyingColors.Surface.copy(alpha = 0.5f),
        focusedBackgroundColor = JuyingColors.SurfaceFocused,
        borderColor = if (selected) JuyingColors.Gold.copy(alpha = 0.55f) else JuyingColors.Outline.copy(alpha = 0.55f),
        focusedBorderColor = JuyingColors.Gold
    ) {
        Row(
            modifier = (if (centered) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = if (centered) 8.dp else 16.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = if (centered) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 22.dp)
                .clip(CircleShape)
                .background(JuyingColors.Gold)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = JuyingColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun GradientArtwork(
    series: SeriesEntity,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    val title = series.displayTitle
    val seed = (series.id.hashCode() * 31 + title.hashCode()).absoluteValue
    val palette = remember(seed) {
        val palettes = listOf(
            listOf(Color(0xFF1C2B4A), Color(0xFF0E1726), Color(0xFF31456B)),
            listOf(Color(0xFF3A2434), Color(0xFF160F18), Color(0xFF6B3F51)),
            listOf(Color(0xFF173A38), Color(0xFF0A1918), Color(0xFF2B5C56)),
            listOf(Color(0xFF3B2E1B), Color(0xFF1A140C), Color(0xFF66512D)),
            listOf(Color(0xFF262045), Color(0xFF100D20), Color(0xFF463A78)),
            listOf(Color(0xFF253141), Color(0xFF0C1219), Color(0xFF3F5570))
        )
        palettes[seed % palettes.size]
    }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = palette,
                start = Offset.Zero,
                end = Offset(1000f, 700f)
            )
        )
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(120.dp)
                .clip(CircleShape)
                .background(JuyingColors.Gold.copy(alpha = 0.06f))
        )
        if (showTitle && title.isNotBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                        )
                    )
                    .padding(10.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SeriesArtwork(
    series: SeriesEntity,
    modifier: Modifier = Modifier,
    showTitleFallback: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop
) {
    val image = series.coverUrl?.takeIf { it.isNotBlank() } ?: series.coverRef?.takeIf { it.isNotBlank() }
    val context = LocalContext.current
    var imageFailed by remember(image) { mutableStateOf(false) }
    var fallback by remember(series.id) { mutableStateOf<java.io.File?>(null) }
    var fallbackFailed by remember(series.id) { mutableStateOf(false) }
    LaunchedEffect(series.id, image, imageFailed) {
        fallbackFailed = false
        fallback = if (image == null || imageFailed) {
            runCatching {
                JuyingApp.graphOf(context).playbackResolver.seriesThumbnail(series.id)
            }.getOrNull()
        } else {
            null
        }
    }
    Box(modifier) {
        when {
            image != null && !imageFailed -> AsyncImage(
                model = image,
                contentDescription = series.displayTitle,
                contentScale = contentScale,
                onError = { imageFailed = true },
                modifier = Modifier.fillMaxSize()
            )
            fallback != null && !fallbackFailed -> AsyncImage(
                model = fallback,
                contentDescription = series.displayTitle,
                contentScale = contentScale,
                onError = { fallbackFailed = true },
                modifier = Modifier.fillMaxSize()
            )
            else -> GradientArtwork(series, Modifier.fillMaxSize(), showTitleFallback)
        }
    }
}

@Composable
fun ProgressLine(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = JuyingColors.Gold,
    trackColor: Color = Color.White.copy(alpha = 0.18f)
) {
    Box(
        modifier
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color)
        )
    }
}

@Composable
fun Badge(text: String, color: Color = JuyingColors.Gold, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp, 
            maxLines = 1
        )
    }
}

@Composable
fun LoadingPane(modifier: Modifier = Modifier, label: String = "加载中…") {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(3.dp, JuyingColors.Gold.copy(alpha = 0.2f), CircleShape)
            )
            Spacer(Modifier.height(18.dp))
            Text(label, color = JuyingColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    action: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    val internalFocus = remember { FocusRequester() }
    val targetFocus = focusRequester ?: internalFocus
    LaunchedEffect(Unit) {
        if (actionText != null && action != null) {
            delay(120)
            runCatching { targetFocus.requestFocus() }
        }
    }
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(JuyingColors.SurfaceHigh)
                .border(1.dp, JuyingColors.Outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("▶", color = JuyingColors.Gold, fontSize = 30.sp)
        }
        Spacer(Modifier.height(22.dp))
        Text(
            title,
            color = JuyingColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            description,
            color = JuyingColors.TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.widthIn(max = 620.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionText != null && action != null) {
            Spacer(Modifier.height(28.dp))
            TvButton(text = actionText, onClick = action, focusRequester = targetFocus)
        }
    }
}

@Composable
fun TvDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier.width(760.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.juying.tv.ui.DialogTracker.acquire()
        onDispose { com.juying.tv.ui.DialogTracker.release() }
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
                .background(Color.Black.copy(alpha = 0.76f))
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = modifier
                    .shadow(30.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF152134), Color(0xFF0D1624))))
                    .border(1.dp, JuyingColors.Outline.copy(alpha = 0.8f), RoundedCornerShape(26.dp))
                    .padding(30.dp),
                content = content
            )
        }
    }
}

@Composable
fun DialogTitle(title: String, subtitle: String? = null) {
    Text(
        title,
        color = JuyingColors.TextPrimary,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold
    )
    if (!subtitle.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            color = JuyingColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        confirmFocus.requestFocus()
    }
    TvDialog(onDismiss = onDismiss, modifier = Modifier.width(600.dp)) {
        DialogTitle(title)
        Spacer(Modifier.height(14.dp))
        Text(message, color = JuyingColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End)
        ) {
            TvButton(dismissText, onDismiss, primary = false)
            TvButton(
                confirmText,
                onConfirm,
                primary = !destructive,
                focusRequester = confirmFocus
            )
        }
    }
}

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false,
    focusRequester: FocusRequester? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var focused by remember { mutableStateOf(false) }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val base = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier
    Column(base) {
        Text(
            label,
            color = if (focused) JuyingColors.Gold else JuyingColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (singleLine) 50.dp else 96.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(JuyingColors.BackgroundAlt)
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) JuyingColors.Gold else JuyingColors.Outline,
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    placeholder,
                    color = JuyingColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    onValueChange(it.text)
                },
                singleLine = singleLine,
                minLines = minLines,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = JuyingColors.TextPrimary),
                cursorBrush = SolidColor(JuyingColors.Gold),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
                ),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) keyboard?.show()
                    }
                    .focusable()
            )
        }
    }
}

@Composable
fun TogglePill(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 50.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) JuyingColors.Gold else JuyingColors.SurfaceHigh)
            .border(1.dp, if (checked) JuyingColors.Gold else JuyingColors.Outline, CircleShape)
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(CircleShape)
                .background(if (checked) JuyingColors.OnGold else JuyingColors.TextMuted)
        )
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val base = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier
    FocusableSurface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = base.fillMaxWidth().heightIn(min = 66.dp),
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.008f,
        backgroundColor = JuyingColors.Surface.copy(alpha = 0.35f),
        focusedBackgroundColor = JuyingColors.SurfaceFocused
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(JuyingColors.SurfaceHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = JuyingColors.Gold, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = JuyingColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        color = JuyingColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (checked) JuyingColors.Gold else Color.Transparent)
            .border(
                1.5.dp,
                if (checked) JuyingColors.Gold else JuyingColors.Outline,
                RoundedCornerShape(7.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = JuyingColors.OnGold, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
fun PinDialogBody(
    title: String,
    subtitle: String,
    verify: suspend (String) -> Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    PinFocusRetry(firstKey)

    fun submit() {
        if (pin.length < 4) {
            error = "请输入至少 4 位数字"
            return
        }
        scope.launch {
            busy = true
            error = null
            val ok = runCatching { verify(pin) }.getOrDefault(false)
            busy = false
            if (ok) onSuccess() else {
                error = "密码不正确，请重试"
                pin = ""
            }
        }
    }

    TvDialog(onDismiss = onDismiss, modifier = Modifier.width(560.dp)) {
        PinHeader(title, subtitle)
        Spacer(Modifier.height(16.dp))
        PinDots(pin)
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = JuyingColors.Red, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(14.dp))
        PinKeypad(pin = pin, onPinChange = { pin = it; error = null }, firstKey = firstKey)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("取消", onDismiss, primary = false, modifier = Modifier.weight(1f))
            TvButton(
                if (busy) "验证中…" else "确定",
                { submit() },
                modifier = Modifier.weight(1f),
                enabled = !busy
            )
        }
    }
}

@Composable
fun SetPinDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var firstPin by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val firstKey = remember { FocusRequester() }
    PinFocusRetry(firstKey, firstPin)

    fun submit() {
        if (pin.length < 4) {
            error = "请输入至少 4 位数字"
            return
        }
        val previous = firstPin
        if (previous == null) {
            firstPin = pin
            pin = ""
            error = null
        } else if (pin != previous) {
            firstPin = null
            pin = ""
            error = "两次输入不一致，请重新设置"
        } else {
            onSave(pin)
        }
    }

    TvDialog(onDismiss = onDismiss, modifier = Modifier.width(560.dp)) {
        PinHeader(
            title = if (firstPin == null) "设置家长锁" else "再次输入密码",
            subtitle = if (firstPin == null) {
                "设置 4–6 位数字，之后进入儿童模式或修改内容都需要它"
            } else {
                "请重复刚才的数字以确认"
            }
        )
        Spacer(Modifier.height(16.dp))
        PinDots(pin)
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = JuyingColors.Red, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(14.dp))
        PinKeypad(pin = pin, onPinChange = { pin = it; error = null }, firstKey = firstKey)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("取消", onDismiss, primary = false, modifier = Modifier.weight(1f))
            TvButton("确定", { submit() }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PinFocusRetry(firstKey: FocusRequester, step: Any? = null) {
    LaunchedEffect(step) {
        listOf(120L, 260L, 500L, 850L).forEach { wait ->
            delay(wait)
            runCatching { firstKey.requestFocus() }
        }
    }
}

@Composable
private fun PinHeader(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(JuyingColors.Gold.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = JuyingColors.Gold,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = JuyingColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, color = JuyingColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PinDots(pin: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(6) { index ->
            Box(
                Modifier
                    .padding(horizontal = 7.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (index < pin.length) JuyingColors.Gold else JuyingColors.SurfaceHigh)
                    .border(1.dp, JuyingColors.Outline, CircleShape)
            )
        }
    }
}

@Composable
private fun PinKeypad(
    pin: String,
    onPinChange: (String) -> Unit,
    firstKey: FocusRequester
) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "清除", "0", "删除")
    keys.chunked(3).forEachIndexed { rowIndex, row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEachIndexed { colIndex, key ->
                val isFirst = rowIndex == 0 && colIndex == 0
                FocusableSurface(
                    onClick = {
                        when (key) {
                            "清除" -> onPinChange("")
                            "删除" -> if (pin.isNotEmpty()) onPinChange(pin.dropLast(1))
                            else -> if (pin.length < 6) onPinChange(pin + key)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .then(if (isFirst) Modifier.focusRequester(firstKey) else Modifier),
                    shape = RoundedCornerShape(12.dp),
                    focusedScale = 1.03f,
                    backgroundColor = JuyingColors.Surface.copy(alpha = 0.5f),
                    focusedBackgroundColor = JuyingColors.SurfaceFocused
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            key,
                            color = JuyingColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (key.length == 1) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        fieldFocus.requestFocus()
    }
    TvDialog(onDismiss = onDismiss, modifier = Modifier.width(680.dp)) {
        DialogTitle(title)
        Spacer(Modifier.height(20.dp))
        TvTextField(
            value = value,
            onValueChange = { value = it },
            label = label,
            placeholder = placeholder,
            singleLine = singleLine,
            focusRequester = fieldFocus
        )
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            TvButton("取消", onDismiss, primary = false)
            TvButton("保存", { onSave(value) })
        }
    }
}

@Composable
fun PhoneAssistDialog(
    title: String,
    fields: List<PhoneField>,
    onDismiss: () -> Unit,
    onResult: (Map<String, String>) -> Unit
) {
    val server = remember { PhoneInputServer(title, fields) }
    val url by server.url.collectAsStateWithLifecycle()
    val result by server.result.collectAsStateWithLifecycle()
    val error by server.error.collectAsStateWithLifecycle()
    val bitmap = remember(url) { url?.let { generateQrBitmap(it, 480) } }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        runCatching { cancelFocus.requestFocus() }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        server.startServer()
        onDispose { runCatching { server.stop() } }
    }
    LaunchedEffect(result) {
        result?.let {
            onResult(it.values)
            onDismiss()
        }
    }

    TvDialog(onDismiss = onDismiss, modifier = Modifier.width(920.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = JuyingColors.Gold, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = JuyingColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "手机扫码填写，避免电视遥控器输入长文本",
                    color = JuyingColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "手机填写二维码",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("正在启动…", color = Color.Black, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text("1  确保手机与电视连接同一个 Wi-Fi", color = JuyingColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Text("2  用手机相机或浏览器扫描左侧二维码", color = JuyingColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Text("3  在手机上填写并提交，电视会自动接收", color = JuyingColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(18.dp))
                if (url != null) {
                    Text(url!!, color = JuyingColors.Blue, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                } else if (error != null) {
                    Text(error!!, color = JuyingColors.Red, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("正在启动接收服务…", color = JuyingColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TvButton("取消", onDismiss, primary = false, focusRequester = cancelFocus)
        }
    }
}

@Composable
fun PosterCard(
    series: SeriesEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    progress: Float? = null,
    topBadge: String? = null,
    selected: Boolean? = null
) {
    val base = (if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier)
        .onFocusChanged { if (it.isFocused) com.juying.tv.ui.NavFocusMemory.seriesId = series.id }
    FocusableSurface(
        onClick = onClick,
        modifier = base.aspectRatio(2f / 3f),
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.05f
    ) {
        SeriesArtwork(series, Modifier.fillMaxSize(), showTitleFallback = false)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.88f))
                    )
                )
        )
        if (topBadge != null) {
            Badge(topBadge, JuyingColors.Gold, Modifier.align(Alignment.TopStart).padding(10.dp))
        }
        if (selected != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (selected) JuyingColors.Gold else Color.Black.copy(alpha = 0.5f))
                    .border(
                        1.5.dp,
                        if (selected) JuyingColors.Gold else Color.White.copy(alpha = 0.6f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = JuyingColors.OnGold, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            Text(
                series.displayTitle,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(
                    if (series.kind == com.juying.tv.data.db.SeriesKind.MOVIE) "电影" else "剧集",
                    series.year?.toString()
                ).joinToString(" · "),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            if (progress != null && progress > 0f) {
                Spacer(Modifier.height(7.dp))
                ProgressLine(progress, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * 嵌入页面内的密码面板。相比 Dialog，不会切换窗口，关闭/验证后焦点不会掉回侧边导航。
 */
@Composable
fun InlinePinPanel(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    verify: (suspend (String) -> Boolean)? = null,
    onVerified: () -> Unit = {},
    setupMode: Boolean = false,
    onSavePin: (String) -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    var firstPin by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val internalFocus = remember { FocusRequester() }
    val firstKey = focusRequester ?: internalFocus
    val scope = rememberCoroutineScope()
    PinFocusRetry(firstKey, firstPin)

    fun submit() {
        if (pin.length < 4) {
            error = "请输入至少 4 位数字"
            return
        }
        if (setupMode) {
            val previous = firstPin
            if (previous == null) {
                firstPin = pin
                pin = ""
                error = null
            } else if (pin != previous) {
                firstPin = null
                pin = ""
                error = "两次输入不一致，请重新设置"
            } else {
                onSavePin(pin)
            }
        } else {
            val verifier = verify ?: return
            scope.launch {
                busy = true
                error = null
                val ok = runCatching { verifier(pin) }.getOrDefault(false)
                busy = false
                if (ok) onVerified() else {
                    error = "密码不正确，请重试"
                    pin = ""
                }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF152134), Color(0xFF0D1624)))
                )
                .border(1.dp, JuyingColors.Outline.copy(alpha = 0.8f), RoundedCornerShape(26.dp))
                .padding(30.dp)
        ) {
            PinHeader(
                title = if (setupMode && firstPin != null) "再次输入密码" else title,
                subtitle = if (setupMode && firstPin != null) "请重复刚才的数字以确认" else subtitle
            )
            Spacer(Modifier.height(16.dp))
            PinDots(pin)
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = JuyingColors.Red, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(14.dp))
            PinKeypad(pin = pin, onPinChange = { pin = it; error = null }, firstKey = firstKey)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("返回", onBack, primary = false, modifier = Modifier.weight(1f))
                TvButton(
                    if (busy) "验证中…" else "确定",
                    { submit() },
                    modifier = Modifier.weight(1f),
                    enabled = !busy
                )
            }
        }
    }
}
