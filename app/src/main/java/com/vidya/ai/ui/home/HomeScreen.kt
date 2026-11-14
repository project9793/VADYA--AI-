package com.vidya.ai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidya.ai.ui.assistant.AssistantViewModel
import com.vidya.ai.ui.tools.ToolsViewModel
import com.vidya.ai.ui.common.rememberMicAction
import com.vidya.ai.ui.components.ActionCard
import com.vidya.ai.ui.components.GuideCard
import com.vidya.ai.ui.components.AiOrb
import com.vidya.ai.ui.components.OrbState
import com.vidya.ai.ui.theme.VidyaCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: AssistantViewModel, tools: ToolsViewModel, onOpenChat: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }

    val onMic = rememberMicAction(
        onDenied = { vm.guideTo("permissions", "माइक की अनुमति चालू करनी होगी।") }
    ) { vm.toggleMic() }

    val statusText = when (ui.orb) {
        OrbState.Idle -> "तैयार"
        OrbState.Listening -> "सुन रही हूँ…"
        OrbState.Thinking -> "सोच रही हूँ…"
        OrbState.Speaking -> "बोल रही हूँ…"
    }
    val greet = buildString {
        append("नमस्ते")
        if (tools.userName.isNotBlank()) append(" ${tools.userName}")
        append("! मैं ${tools.assistantName} हूँ। आज मैं आपकी कैसे मदद करूँ?")
    }
    val msgs = ui.conv.messages
    val lastUser = msgs.lastOrNull { it.role == "user" }
    val lastModel = msgs.lastOrNull()?.takeIf { it.role == "model" }
    val scroll = rememberScrollState()
    LaunchedEffect(lastModel?.id) {
        if (lastModel != null) {
            kotlinx.coroutines.delay(150)
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("VIDYA AI", style = MaterialTheme.typography.headlineLarge, color = VidyaCyan)
        Text("Your Voice. Your Intelligence. Your Personal AI.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(greet, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

        Spacer(Modifier.height(8.dp))
        AiOrb(
            state = ui.orb,
            size = 200.dp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onMic
            )
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            AssistChip(onClick = {}, label = { Text(statusText) })
            AssistChip(
                onClick = {},
                label = { Text(if (online) "Online" else "Offline") },
                leadingIcon = {
                    Icon(if (online) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
            FilterChip(
                selected = ui.handsFree,
                onClick = vm::toggleHandsFree,
                label = { Text("Hands-free", maxLines = 1, softWrap = false) }
            )
        }

        Spacer(Modifier.height(12.dp))
        FilledIconButton(onClick = onMic, modifier = Modifier.size(72.dp), shape = CircleShape) {
            Icon(
                if (ui.orb == OrbState.Idle) Icons.Default.Mic else Icons.Default.Stop,
                contentDescription = "Voice assistant", modifier = Modifier.size(32.dp)
            )
        }
        Text("Orb या mic दबाकर बोलिए", style = MaterialTheme.typography.bodySmall)

        if (ui.partial.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("🎤 ${ui.partial}", style = MaterialTheme.typography.bodyLarge)
        }
        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
        }
        if (ui.timing.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(ui.timing, style = MaterialTheme.typography.labelSmall)
        }

        ui.pending?.let { p ->
            Spacer(Modifier.height(12.dp))
            ActionCard(p, vm::confirmPending, vm::cancelPending)
        }
        ui.guide?.let { g ->
            Spacer(Modifier.height(12.dp))
            GuideCard(g, vm::goTo, vm::dismissGuide, vm::stopTour)
        }

        if (lastUser != null) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("आप: ${lastUser.text}", style = MaterialTheme.typography.bodyMedium)
                    if (lastModel != null) Text(lastModel.text, style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onOpenChat) { Text("पूरी chat देखें") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("कुछ भी पूछिए…") },
            shape = RoundedCornerShape(28.dp),
            trailingIcon = {
                IconButton(
                    onClick = { vm.sendText(text); text = "" },
                    enabled = text.isNotBlank() && !ui.busy
                ) { Icon(Icons.Default.Send, contentDescription = "Send") }
            }
        )

        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("Search", Icons.Default.Search) { text = "इंटरनेट से खोजकर बताओ: " }
            QuickAction("Code", Icons.Default.Code) { text = "इस काम का code लिखो: " }
            QuickAction("Translate", Icons.Default.Translate) { text = "इसे English में translate करो: " }
            QuickAction("Explain", Icons.Default.Lightbulb) { text = "आसान भाषा में समझाओ: " }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }
    )
}
