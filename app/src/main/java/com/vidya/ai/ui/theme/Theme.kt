package com.vidya.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val VidyaViolet = Color(0xFF7C5CFF)
val VidyaCyan = Color(0xFF35E0D0)
val VidyaSaffron = Color(0xFFFFB020)
val VidyaRose = Color(0xFFFF6B9A)
val VidyaInk = Color(0xFF0B1020)
val VidyaSurface = Color(0xFF141A2E)
val VidyaSurfaceHigh = Color(0xFF1E2540)
val VidyaMuted = Color(0xFFA9B2C9)
val VidyaOutline = Color(0xFF2C3554)

private val DarkColors = darkColorScheme(
    primary = VidyaViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2263),
    onPrimaryContainer = Color(0xFFE3DDFF),
    secondary = VidyaCyan,
    onSecondary = Color(0xFF00201D),
    secondaryContainer = Color(0xFF123A38),
    onSecondaryContainer = Color(0xFFB8F5EE),
    tertiary = VidyaSaffron,
    onTertiary = Color(0xFF2A1B00),
    background = VidyaInk,
    onBackground = Color(0xFFE7EAF3),
    surface = VidyaSurface,
    onSurface = Color(0xFFE7EAF3),
    surfaceVariant = VidyaSurfaceHigh,
    onSurfaceVariant = VidyaMuted,
    outline = VidyaOutline,
    outlineVariant = Color(0xFF1A2038),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0000)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3FE0),
    onPrimary = Color.White,
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    tertiary = Color(0xFFB26B00),
    background = Color(0xFFF7F6FB),
    onBackground = Color(0xFF14161F),
    surface = Color.White,
    onSurface = Color(0xFF14161F),
    surfaceVariant = Color(0xFFEDEAF6),
    onSurfaceVariant = Color(0xFF5A6072),
    outline = Color(0xFFC9C4DA)
)

private val VidyaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)

private val VidyaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun VidyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = VidyaTypography,
        shapes = VidyaShapes,
        content = content
    )
}
