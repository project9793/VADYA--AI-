package com.vidya.ai.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vidya.ai.core.MicPermission

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val ctx = LocalContext.current
    var micGranted by remember { mutableStateOf(MicPermission.has(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { micGranted = MicPermission.has(ctx) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { micGranted = it }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Microphone", style = MaterialTheme.typography.titleMedium)
                Text(if (micGranted) "✔ Permission मिल चुकी है" else "Permission अभी नहीं दी गई")
                if (!micGranted) {
                    Button(onClick = { launcher.launch(android.Manifest.permission.RECORD_AUDIO) }) {
                        Text("Mic permission दें")
                    }
                    OutlinedButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", ctx.packageName, null))
                        )
                    }) { Text("App settings खोलें") }
                }
            }
        }

        KeyCard(
            title = "Gemini API key",
            hint = "Google AI Studio (aistudio.google.com) से key बनाइए।",
            initial = vm.savedGemini,
            onSave = vm::saveGemini
        )
        KeyCard(
            title = "Search API key",
            hint = "Internet search के लिए। Provider Phase 7 में तय होगा; अभी key सुरक्षित रख सकते हैं।",
            initial = vm.savedSearch,
            onSave = vm::saveSearch
        )
        Text(
            "Keys इस फ़ोन में encrypted रहती हैं। बाद के Phase में इन्हें Firebase backend पर ले जाया जाएगा।",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun KeyCard(title: String, hint: String, initial: String, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var visible by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(initial.isNotBlank()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation =
                    if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Show/Hide"
                        )
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onSave(value); saved = true }, enabled = !saved) { Text("Save") }
                if (saved && value.isNotBlank()) Text("✔ Saved", Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
            }
        }
    }
}
