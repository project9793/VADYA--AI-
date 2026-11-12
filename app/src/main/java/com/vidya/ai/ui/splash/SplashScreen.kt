package com.vidya.ai.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidya.ai.ui.components.VidyaLogo
import com.vidya.ai.ui.theme.VidyaCyan
import com.vidya.ai.ui.theme.VidyaGradient
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        delay(1500)
        onFinished()
    }
    Column(
        Modifier.fillMaxSize().background(VidyaGradient),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VidyaLogo(
            logoSize = 260.dp,
            modifier = Modifier.graphicsLayer {
                val s = 0.7f + 0.3f * a.value
                scaleX = s
                scaleY = s
                alpha = a.value
            }
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "VIDYA AI",
            modifier = Modifier.alpha(a.value),
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 8.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your Voice. Your Intelligence. Your Personal AI.",
            modifier = Modifier.alpha(a.value).padding(horizontal = 32.dp),
            color = VidyaCyan,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}
