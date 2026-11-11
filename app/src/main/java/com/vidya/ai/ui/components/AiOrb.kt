package com.vidya.ai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vidya.ai.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState { Idle, Listening, Thinking, Speaking }

@Composable
fun AiOrb(state: OrbState, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulseMs = when (state) {
        OrbState.Idle -> 3000
        OrbState.Listening -> 1200
        OrbState.Thinking -> 800
        OrbState.Speaking -> 500
    }
    val amp = when (state) {
        OrbState.Idle -> 0.04f
        OrbState.Listening -> 0.09f
        OrbState.Thinking -> 0.05f
        OrbState.Speaking -> 0.13f
    }
    val pulse by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(pulseMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(
            tween(if (state == OrbState.Thinking) 1500 else 9000, easing = LinearEasing)
        ),
        label = "spin"
    )

    Box(modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val c = center
            val base = this.size.minDimension / 2f * 0.62f
            val r = base * (1f + amp * pulse)

            // outer glow
            drawCircle(
                Brush.radialGradient(
                    listOf(VidyaViolet.copy(alpha = 0.35f), Color.Transparent),
                    center = c, radius = this.size.minDimension / 2f
                ),
                radius = this.size.minDimension / 2f, center = c
            )
            // main sphere (offset highlight gives a 3D look)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.9f), VidyaCyan, VidyaViolet, VidyaBg),
                    center = Offset(c.x - r * 0.3f, c.y - r * 0.35f),
                    radius = r * 1.5f
                ),
                radius = r, center = c
            )
            // orbiting ring + dot
            drawCircle(
                VidyaCyan.copy(alpha = 0.5f),
                radius = r * 1.25f, center = c, style = Stroke(width = 2f)
            )
            val rad = Math.toRadians(spin.toDouble())
            drawCircle(
                VidyaPink, radius = 7f,
                center = Offset(
                    c.x + (r * 1.25f * cos(rad)).toFloat(),
                    c.y + (r * 1.25f * sin(rad)).toFloat()
                )
            )
        }
    }
}
