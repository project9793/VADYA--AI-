package com.vidya.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidya.ai.data.ChatMessage
import com.vidya.ai.ui.assistant.AssistantViewModel
import com.vidya.ai.ui.common.rememberMicAction
import com.vidya.ai.ui.components.ActionCard
import com.vidya.ai.ui.components.GuideCard
import com.vidya.ai.ui.components.OrbState
import com.vidya.ai.ui.theme.VidyaViolet

@Composable
fun ChatScreen(vm: AssistantViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    val onMic = rememberMicAction(
        onDenied = { vm.guideTo("permissions", "माइक की अनुमति चालू करनी होगी।") }
    ) { vm.toggleMic() }

    val msgs = ui.conv.messages
    LaunchedEffect(msgs.size, ui.busy) {
        val n = msgs.size + if (ui.busy) 1 else 0
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    LaunchedEffect(msgs.lastOrNull()?.text?.length) {
        if (ui.busy && msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Chat", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (ui.busy || ui.orb == OrbState.Speaking) {
                IconButton(onClick = vm::stop) { Icon(Icons.Default.Stop, "Stop") }
            }
            IconButton(onClick = vm::newChat) { Icon(Icons.Default.Add, "New chat") }
        }

        if (msgs.isEmpty() && !ui.busy) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("कोई भी सवाल पूछिए, या mic दबाकर बोलिए।", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(msgs, key = { it.id }) { m ->
                    Bubble(
                        m = m,
                        isLast = m.id == msgs.last().id,
                        onCopy = { clipboard.setText(AnnotatedString(m.text)) },
                        onSpeak = { vm.speakMessage(m.text) },
                        onRegenerate = vm::regenerate
                    )
                }
                if (ui.busy && msgs.lastOrNull()?.role != "model") item { Text("सोच रही हूँ…", style = MaterialTheme.typography.bodyMedium) }
            }
        }

        if (ui.orb == OrbState.Listening) {
            Text(
                if (ui.partial.isBlank()) "🎤 सुन रही हूँ…" else "🎤 ${ui.partial}",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        ui.pending?.let { p ->
            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                ActionCard(p, vm::confirmPending, vm::cancelPending, vm::pickOption)
            }
        }
        ui.guide?.let { g ->
            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                GuideCard(g, vm::goTo, vm::dismissGuide, vm::stopTour)
            }
        }
        ui.error?.let {
            Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
        }
        if (ui.timing.isNotBlank()) {
            Text(ui.timing, Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall)
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("संदेश लिखिए…") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            FilledTonalIconButton(onClick = onMic) {
                Icon(if (ui.orb == OrbState.Listening) Icons.Default.Stop else Icons.Default.Mic, "Voice")
            }
            FilledIconButton(
                onClick = { vm.sendText(text); text = "" },
                enabled = text.isNotBlank() && !ui.busy
            ) { Icon(Icons.Default.Send, "Send") }
        }
    }
}

@Composable
private fun Bubble(
    m: ChatMessage,
    isLast: Boolean,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onRegenerate: () -> Unit
) {
    val user = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (user) VidyaViolet.copy(alpha = 0.80f) else Color(0x40FFFFFF),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(m.text, color = Color(0xFFE8ECFF), style = MaterialTheme.typography.bodyLarge)
                if (!user) {
                    Row {
                        IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(18.dp))
                        }
                        IconButton(onClick = onSpeak, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.VolumeUp, "Speak", Modifier.size(18.dp))
                        }
                        if (isLast) IconButton(onClick = onRegenerate, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Refresh, "Regenerate", Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
