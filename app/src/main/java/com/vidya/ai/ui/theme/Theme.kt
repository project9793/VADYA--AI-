package com.vidya.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VidyaBg = Color(0xFF070B1A)
val VidyaSurface = Color(0xFF121A33)
val VidyaCyan = Color(0xFF22D3EE)
val VidyaViolet = Color(0xFF8B5CF6)
val VidyaPink = Color(0xFFEC4899)

private val Scheme = darkColorScheme(
    primary = VidyaCyan,
    secondary = VidyaViolet,
    tertiary = VidyaPink,
    background = VidyaBg,
    surface = VidyaSurface,
    onPrimary = Color(0xFF00212A),
    onBackground = Color(0xFFE8ECFF),
    onSurface = Color(0xFFE8ECFF)
)

@Composable
fun VidyaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
