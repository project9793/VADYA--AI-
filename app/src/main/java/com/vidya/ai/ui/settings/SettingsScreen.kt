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
import com.vidya.ai.core.SecureStore

private val LANGS = listOf(
    "हिन्दी" to "hi-IN", "English" to "en-IN", "বাংলা" to "bn-IN", "தமிழ்" to "ta-IN",
    "తెలుగు" to "te-IN", "मराठी" to "mr-IN", "ગુજરાતી" to "gu-IN", "ਪੰਜਾਬੀ" to "pa-IN",
    "اردو" to "ur-IN", "नेपाली" to "ne-NP", "Español" to "es-ES", "Français" to "fr-FR",
    "Deutsch" to "de-DE", "العربية" to "ar-SA", "日本語" to "ja-JP", "中文" to "zh-CN"
)

private val TTS_VOICES = listOf("Kore", "Aoede", "Leda", "Zephyr", "Puck", "Charon", "Fenrir")

private val ENGINES = listOf(
    "Gemini (सुझाया)" to "gemini",
    "Google in-app" to "google",
    "Google window" to "window"
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
    var engine by remember { mutableStateOf(vm.voiceEngine) }
    var ttsEngine by remember { mutableStateOf(vm.ttsEngine) }
    var ttsVoice by remember { mutableStateOf(vm.ttsVoice) }

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
                if (engine != "gemini") {
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
                            selected = engine == key,
                            onClick = { engine = key; vm.voiceEngine = key },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI की आवाज़ (Voice output)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Gemini voice इंसानों जैसी स्वाभाविक आवाज़ देती है। Free limit पूरी होने पर app अपने आप फ़ोन की आवाज़ पर लौट जाता है।",
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ttsEngine == "gemini",
                        onClick = { ttsEngine = "gemini"; vm.ttsEngine = "gemini" },
                        label = { Text("Gemini voice (सुझाया)") }
                    )
                    FilterChip(
                        selected = ttsEngine == "android",
                        onClick = { ttsEngine = "android"; vm.ttsEngine = "android" },
                        label = { Text("फ़ोन की आवाज़") }
                    )
                }
                if (ttsEngine == "gemini") {
                    Text("आवाज़ चुनिए:", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TTS_VOICES.forEach { v ->
                            FilterChip(
                                selected = ttsVoice == v,
                                onClick = { ttsVoice = v; vm.ttsVoice = v },
                                label = { Text(v) }
                            )
                        }
                    }
                    Button(onClick = vm::testVoice) { Text("आवाज़ सुनकर देखें") }
                    if (vm.voiceTestMsg.isNotBlank()) Text(vm.voiceTestMsg, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("बोलने की भाषा (Voice language)", style = MaterialTheme.typography.titleMedium)
                Text("आप जिस भाषा में बोलेंगे वह चुनिए। AI का जवाब आपकी भाषा में ही आएगा।",
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

        KeyCard(
            title = "Gemini API key",
            hint = "Google AI Studio (aistudio.google.com) से key बनाइए।",
            initial = vm.savedGemini, secret = true, onSave = vm::saveGemini
        )
        KeyCard(
            title = "Gemini model",
            hint = "Default: ${SecureStore.DEFAULT_MODEL}। नाम में space या extra शब्द न डालें।",
            initial = vm.savedModel, secret = false, onSave = vm::saveModel,
            resetValue = SecureStore.DEFAULT_MODEL
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
        Text("Keys इस फ़ोन में encrypted रहती हैं।", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun KeyCard(
    title: String, hint: String, initial: String, secret: Boolean,
    onSave: (String) -> Unit, resetValue: String? = null
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
                if (resetValue != null) {
                    TextButton(onClick = { value = resetValue; onSave(resetValue); saved = true }) { Text("Default") }
                }
                if (saved && value.isNotBlank()) Text("✔ Saved")
            }
        }
    }
}
