package com.juying.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object JuyingColors {
    val Background = Color(0xFF070B12)
    val BackgroundAlt = Color(0xFF0B1220)
    val Surface = Color(0xFF111C2E)
    val SurfaceHigh = Color(0xFF1A283C)
    val SurfaceFocused = Color(0xFF243956)
    val Outline = Color(0xFF263B55)
    val OutlineFocused = Color(0xFFE7B563)
    val Gold = Color(0xFFE7B563)
    val GoldBright = Color(0xFFFFD48C)
    val OnGold = Color(0xFF201503)
    val TextPrimary = Color(0xFFF4F7FB)
    val TextSecondary = Color(0xFF9FB0C7)
    val TextMuted = Color(0xFF6E8099)
    val Blue = Color(0xFF6FB7FF)
    val Red = Color(0xFFFF6B6B)
    val Green = Color(0xFF63D6A3)
    val KidsAccent = Color(0xFF7EE0C2)
}

private val JuyingColorScheme = darkColorScheme(
    primary = JuyingColors.Gold,
    onPrimary = JuyingColors.OnGold,
    secondary = JuyingColors.Blue,
    onSecondary = Color(0xFF04121F),
    background = JuyingColors.Background,
    onBackground = JuyingColors.TextPrimary,
    surface = JuyingColors.Surface,
    onSurface = JuyingColors.TextPrimary,
    surfaceVariant = JuyingColors.SurfaceHigh,
    onSurfaceVariant = JuyingColors.TextSecondary,
    outline = JuyingColors.Outline,
    error = JuyingColors.Red
)

private val JuyingTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 54.sp,
        lineHeight = 62.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 50.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 26.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp
    )
)

private val JuyingShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JuyingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JuyingColorScheme,
        typography = JuyingTypography,
        shapes = JuyingShapes,
        content = content
    )
}
