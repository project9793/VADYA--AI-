package com.vidya.ai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What the orb is doing right now. Drives both colour and animation speed. */
enum class OrbState { Idle, Listening, Thinking, Speaking }

private fun OrbState.accent(): Color = when (this) {
    OrbState.Idle -> Color(0xFF7C5CFF)
    OrbState.Listening -> Color(0xFF35E0D0)
    OrbState.Thinking -> Color(0xFFFFB020)
    OrbState.Speaking -> Color(0xFFFF6B9A)
}

private fun OrbState.pulseDuration(): Int = when (this) {
    OrbState.Idle -> 2600
    OrbState.Listening -> 1400
    OrbState.Thinking -> 900
    OrbState.Speaking -> 620
}

/**
 * The voice orb: a soft gradient core, a sweeping ring and a counter-rotating arc.
 * Everything is drawn on a single Canvas, so it stays cheap and has no image assets.
 */
@Composable
fun AiOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
    onClick: () -> Unit = {}
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = state.pulseDuration(),
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val accent = state.accent()

    Box(
        modifier = modifier
            .size(diameter)
            .scale(pulse)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2f, height / 2f)
            val radius = minOf(width, height) / 2f

            // 1. Outer glow.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.40f),
                        accent.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // 2. Sweeping ring.
            rotate(spin, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.85f),
                            Color.Transparent,
                            accent.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    radius = radius * 0.78f,
                    center = center,
                    style = Stroke(width = radius * 0.035f)
                )
            }

            // 3. Counter-rotating highlight arc.
            rotate(-spin * 1.6f, pivot = center) {
                drawArc(
                    color = Color.White.copy(alpha = 0.55f),
                    startAngle = 0f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.62f, center.y - radius * 0.62f),
                    size = Size(radius * 1.24f, radius * 1.24f),
                    style = Stroke(width = radius * 0.02f, cap = StrokeCap.Round)
                )
            }

            // 4. Core sphere.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        accent.copy(alpha = 0.95f),
                        accent.copy(alpha = 0.35f)
                    ),
                    center = Offset(center.x - radius * 0.16f, center.y - radius * 0.20f),
                    radius = radius * 0.62f
                ),
                radius = radius * 0.50f,
                center = center
            )

            // 5. Light sweeping across the core.
            rotate(shimmer * 360f, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    radius = radius * 0.50f,
                    center = center
                )
            }
        }
    }
}
