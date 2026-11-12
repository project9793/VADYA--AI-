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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vidya.ai.core.MicPermission

private val LANGS = listOf(
    "हिन्दी" to "hi-IN", "English" to "en-IN", "বাংলা" to "bn-IN", "தமிழ்" to "ta-IN",
    "తెలుగు" to "te-IN", "मराठी" to "mr-IN", "ગુજરાતી" to "gu-IN", "ਪੰਜਾਬੀ" to "pa-IN",
    "اردو" to "ur-IN", "नेपाली" to "ne-NP", "Español" to "es-ES", "Français" to "fr-FR",
    "Deutsch" to "de-DE", "العربية" to "ar-SA", "日本語" to "ja-JP", "中文" to "zh-CN"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val ctx = LocalContext.current
    var micGranted by remember { mutableStateOf(MicPermission.has(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { micGranted = MicPermission.has(ctx) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { micGranted = it }
    var lang by remember { mutableStateOf(vm.sttLang) }
    var useDialog by remember { mutableStateOf(vm.useDialog) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Microphone", style = MaterialTheme.typography.titleMedium)
                Text(if (micGranted) "✔ Permission मिल चुकी है" else "Permission अभी नहीं दी गई")
                OutlinedButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", "com.google.android.googlequicksearchbox", null))
                    )
                }) { Text("Google app की mic permission जाँचें") }
                Text("बोलना सुनने का काम Google app करता है, इसलिए उसे भी Microphone allow होना चाहिए।",
                    style = MaterialTheme.typography.bodySmall)
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

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("बोलने की भाषा (Voice language)", style = MaterialTheme.typography.titleMedium)
                Text("Speech recognition इसी भाषा में सुनेगा। AI का जवाब आपकी भाषा में ही आएगा।",
                    style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LANGS.forEach { (label, tag) ->
                        FilterChip(
                            selected = lang == tag,
                            onClick = { lang = tag; vm.sttLang = tag },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Google voice window से बोलें", style = MaterialTheme.typography.titleMedium)
                    Text("In-app mic में error आए तो इसे चालू करें। (error आने पर app इसे खुद चालू कर देता है)",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = useDialog, onCheckedChange = { useDialog = it; vm.useDialog = it })
            }
        }

        KeyCard(
            title = "Gemini API key",
            hint = "Google AI Studio (aistudio.google.com) से key बनाइए।",
            initial = vm.savedGemini, secret = true, onSave = vm::saveGemini
        )
        KeyCard(
            title = "Gemini model",
            hint = "Default: gemini-2.5-flash. Error आए तो gemini-3.5-flash या gemini-3.1-flash-lite आज़माइए।",
            initial = vm.savedModel, secret = false, onSave = vm::saveModel
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connection test", style = MaterialTheme.typography.titleMedium)
                Text("Gemini से connection और API key जाँचता है।", style = MaterialTheme.typography.bodySmall)
                Button(onClick = vm::runTest, enabled = !vm.testing) {
                    Text(if (vm.testing) "जाँच हो रही है…" else "Test करें")
                }
                if (vm.testResult.isNotBlank()) Text(vm.testResult)
            }
        }
        KeyCard(
            title = "Search API key",
            hint = "Internet search (Phase 7) के लिए। अभी सिर्फ़ सुरक्षित रखी जाती है।",
            initial = vm.savedSearch, secret = true, onSave = vm::saveSearch
        )
        Text(
            "Keys इस फ़ोन में encrypted रहती हैं।",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun KeyCard(
    title: String, hint: String, initial: String, secret: Boolean, onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    var visible by remember { mutableStateOf(!secret) }
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
                trailingIcon = if (secret) ({
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Show/Hide"
                        )
                    }
                }) else null
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onSave(value); saved = true }, enabled = !saved) { Text("Save") }
                if (saved && value.isNotBlank()) Text("✔ Saved")
            }
        }
    }
}
