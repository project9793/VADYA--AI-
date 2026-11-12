package com.vidya.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val VidyaBg = Color(0xFF1A1F6B)
val VidyaSurface = Color(0xFF1C2366)
val VidyaCyan = Color(0xFF22D3EE)
val VidyaViolet = Color(0xFF8B5CF6)
val VidyaPink = Color(0xFFEC4899)

/** App-wide background: deep blue -> indigo -> teal (no flat black). */
val VidyaGradient = Brush.verticalGradient(
    listOf(Color(0xFF101B52), Color(0xFF2B1B66), Color(0xFF0A3A5E))
)

private val Scheme = darkColorScheme(
    primary = VidyaCyan,
    secondary = VidyaViolet,
    tertiary = VidyaPink,
    background = Color(0xFF14194F),
    surface = Color(0xFF1C2366),
    onPrimary = Color(0xFF00212A),
    onBackground = Color(0xFFF1F3FF),
    onSurface = Color(0xFFF1F3FF),
    surfaceVariant = Color(0x33FFFFFF),
    onSurfaceVariant = Color(0xFFDDE2FF),
    surfaceContainerLowest = Color(0x1AFFFFFF),
    surfaceContainerLow = Color(0x26FFFFFF),
    surfaceContainer = Color(0x33FFFFFF),
    surfaceContainerHigh = Color(0xFF263080),
    surfaceContainerHighest = Color(0x40FFFFFF),
    secondaryContainer = Color(0xFF4B3AA8),
    onSecondaryContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF9FB0FF),
    outlineVariant = Color(0x66FFFFFF),
    error = Color(0xFFFF8A9B)
)

@Composable
fun VidyaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
