package com.vidya.ai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vidya.ai.ui.theme.VidyaCyan
import com.vidya.ai.ui.theme.VidyaPink
import com.vidya.ai.ui.theme.VidyaViolet
import kotlin.math.cos
import kotlin.math.sin

/** Floating 3D logo: glossy sphere, glowing "V", tilted orbit ring with a moving dot. */
@Composable
fun VidyaLogo(modifier: Modifier = Modifier, logoSize: Dp = 220.dp) {
    val t = rememberInfiniteTransition(label = "logo")
    val bob by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "spin"
    )

    Canvas(modifier.size(logoSize)) {
        val w = this.size.minDimension
        val r = w * 0.26f
        val c = Offset(w / 2f, w / 2f - bob * w * 0.025f)
        val tilt = -18f

        // floor shadow (shrinks as the logo floats up)
        val sw = r * 1.9f * (1f - bob * 0.12f)
        drawOval(
            Color.Black.copy(alpha = 0.30f - bob * 0.10f),
            topLeft = Offset(w / 2f - sw / 2f, w * 0.88f),
            size = Size(sw, r * 0.20f)
        )
        // glow
        drawCircle(
            Brush.radialGradient(
                listOf(VidyaViolet.copy(alpha = 0.55f), Color.Transparent),
                center = c, radius = w * 0.5f
            ),
            radius = w * 0.5f, center = c
        )

        val ringW = r * 2.9f
        val ringH = r * 0.95f
        val ringTopLeft = Offset(c.x - ringW / 2f, c.y - ringH / 2f)
        val ringSize = Size(ringW, ringH)
        val ringStroke = Stroke(width = w * 0.022f, cap = StrokeCap.Round)

        // ring, back half (behind the sphere)
        rotate(tilt, pivot = c) {
            drawArc(
                VidyaCyan.copy(alpha = 0.40f), 180f, 180f, false,
                ringTopLeft, ringSize, style = ringStroke
            )
        }

        // sphere with offset highlight = 3D
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, VidyaCyan, VidyaViolet, Color(0xFF1E1B4B)),
                center = Offset(c.x - r * 0.35f, c.y - r * 0.40f),
                radius = r * 1.65f
            ),
            radius = r, center = c
        )
        // specular
        drawOval(
            Color.White.copy(alpha = 0.35f),
            topLeft = Offset(c.x - r * 0.65f, c.y - r * 0.80f),
            size = Size(r * 0.75f, r * 0.40f)
        )

        // letter V with drop shadow
        val v = Path().apply {
            moveTo(c.x - r * 0.60f, c.y - r * 0.40f)
            lineTo(c.x - r * 0.24f, c.y - r * 0.40f)
            lineTo(c.x, c.y + r * 0.18f)
            lineTo(c.x + r * 0.24f, c.y - r * 0.40f)
            lineTo(c.x + r * 0.60f, c.y - r * 0.40f)
            lineTo(c.x + r * 0.15f, c.y + r * 0.58f)
            lineTo(c.x - r * 0.15f, c.y + r * 0.58f)
            close()
        }
        translate(r * 0.04f, r * 0.07f) {
            drawPath(v, Color(0xFF141040).copy(alpha = 0.45f))
        }
        drawPath(
            v,
            Brush.verticalGradient(
                listOf(Color.White, Color(0xFFBDF3FF)),
                startY = c.y - r * 0.4f, endY = c.y + r * 0.6f
            )
        )

        // ring, front half (in front of the sphere) + orbiting dot
        rotate(tilt, pivot = c) {
            drawArc(
                VidyaCyan.copy(alpha = 0.95f), 0f, 180f, false,
                ringTopLeft, ringSize, style = ringStroke
            )
            val a = Math.toRadians(spin.toDouble())
            drawCircle(
                VidyaPink, w * 0.018f,
                Offset(
                    c.x + (ringW / 2f * cos(a)).toFloat(),
                    c.y + (ringH / 2f * sin(a)).toFloat()
                )
            )
        }
    }
}
