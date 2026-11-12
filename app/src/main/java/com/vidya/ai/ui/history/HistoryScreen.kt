package com.vidya.ai.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidya.ai.ui.assistant.AssistantViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: AssistantViewModel, onOpen: () -> Unit) {
    val list by vm.history.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    val shown = remember(list, query) {
        if (query.isBlank()) list
        else list.filter { c ->
            c.title.contains(query, true) || c.messages.any { it.text.contains(query, true) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (list.isNotEmpty()) IconButton(onClick = { confirmClear = true }) {
                Icon(Icons.Default.DeleteSweep, "Clear all")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("History में खोजें…") },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        Spacer(Modifier.height(8.dp))

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (list.isEmpty()) "अभी कोई chat नहीं है।" else "कुछ नहीं मिला।")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { c ->
                    Card(Modifier.fillMaxWidth().clickable { vm.openConversation(c.id); onOpen() }) {
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.title.ifBlank { "नई chat" }, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium)
                                Text("${c.messages.size} संदेश · ${fmt.format(Date(c.updated))}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { vm.deleteConversation(c.id) }) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("पूरी history हटाएँ?") },
            text = { Text("सारी chats इस फ़ोन से हमेशा के लिए हट जाएँगी।") },
            confirmButton = {
                TextButton(onClick = { vm.clearAllHistory(); confirmClear = false }) { Text("हटाएँ") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("रद्द करें") } }
        )
    }
}
