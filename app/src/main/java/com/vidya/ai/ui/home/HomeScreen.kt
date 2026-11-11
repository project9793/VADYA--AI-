package com.vidya.ai.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vidya.ai.ui.components.AiOrb
import com.vidya.ai.ui.components.OrbState
import com.vidya.ai.ui.theme.VidyaCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }

    val statusText = when (ui.orb) {
        OrbState.Idle -> "तैयार"
        OrbState.Listening -> "सुन रही हूँ…"
        OrbState.Thinking -> "सोच रही हूँ…"
        OrbState.Speaking -> "बोल रही हूँ…"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("VIDYA AI", style = MaterialTheme.typography.headlineLarge, color = VidyaCyan)
        Text("Your Voice. Your Intelligence. Your Personal AI.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text("नमस्ते! आज मैं आपकी कैसे मदद करूँ?",
            style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(12.dp))
        AiOrb(state = ui.orb)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(statusText) })
            AssistChip(
                onClick = {},
                label = { Text(if (ui.online) "Online" else "Offline") },
                leadingIcon = {
                    Icon(if (ui.online) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        FilledIconButton(
            onClick = vm::onMicTap,
            modifier = Modifier.size(72.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Voice assistant", Modifier.size(32.dp))
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("कुछ भी पूछिए…") },
            shape = RoundedCornerShape(28.dp),
            trailingIcon = {
                IconButton(onClick = { /* Phase 3: send to Gemini */ }, enabled = text.isNotBlank()) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        )

        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("Search", Icons.Default.Search)
            QuickAction("Code", Icons.Default.Code)
            QuickAction("Translate", Icons.Default.Translate)
            QuickAction("Camera", Icons.Default.CameraAlt)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector) {
    AssistChip(
        onClick = { /* wired in later phases */ },
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }
    )
}
