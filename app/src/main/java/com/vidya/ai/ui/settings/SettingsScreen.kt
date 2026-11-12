package com.vidya.ai.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vidya.ai.core.MicPermission
import com.vidya.ai.ui.tools.ToolsViewModel

private val ENGINES = listOf(
    "Gemini (सुझाया)" to "gemini",
    "Google in-app" to "google",
    "Google window" to "window"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(tools: ToolsViewModel) {
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
                if (tools.voiceEngine != "gemini") {
                    OutlinedButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", "com.google.android.googlequicksearchbox", null))
                        )
                    }) { Text("Google app की mic permission जाँचें") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Voice engine (सुनने का तरीका)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Gemini: app खुद आवाज़ record करता है और Gemini उसे text बनाता है। " +
                        "Google की speech service पर निर्भर नहीं, और कई भाषाएँ समझता है।",
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ENGINES.forEach { (label, key) ->
                        FilterChip(
                            selected = tools.voiceEngine == key,
                            onClick = { tools.setVoiceEngine(key) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("इंटरनेट से ताज़ा जानकारी", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "समाचार, भाव, मौसम जैसे सवालों पर Gemini Google Search से असली जानकारी लाता है।",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = tools.searchEnabled, onCheckedChange = tools::setSearchEnabled)
            }
        }

        Text("नाम, आवाज़, भाषा, Memory और API key अब Tools tab में हैं।",
            style = MaterialTheme.typography.bodySmall)
    }
}
