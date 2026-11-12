package com.vidya.ai.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vidya.ai.core.AppPerm
import com.vidya.ai.core.Languages
import com.vidya.ai.core.Permissions
import com.vidya.ai.core.SecureStore

object ToolRoutes {
    const val Home = "tools"
    const val Account = "tools/account"
    const val Assistant = "tools/assistant"
    const val Voice = "tools/voice"
    const val Language = "tools/language"
    const val Memory = "tools/memory"
    const val Keys = "tools/keys"
    const val Permissions = "tools/permissions"
}

private val TTS_VOICES = listOf("Kore", "Aoede", "Leda", "Zephyr", "Puck", "Charon", "Fenrir")

// ---------------------------------------------------------------- hub

@Composable
fun ToolsHomeScreen(tools: ToolsViewModel, onOpen: (String) -> Unit) {
    LaunchedEffect(Unit) { tools.reloadMemories() }
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val grantedCount = remember(tick) { AppPerm.entries.count { Permissions.isGranted(ctx, it) } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tools", style = MaterialTheme.typography.headlineMedium)

        ToolCard(Icons.Default.Person, "1. खाता (Account)",
            tools.userName.ifBlank { "अपना नाम डालिए" }) { onOpen(ToolRoutes.Account) }
        ToolCard(Icons.Default.SmartToy, "2. Assistant का नाम",
            tools.assistantName) { onOpen(ToolRoutes.Assistant) }
        ToolCard(Icons.Default.RecordVoiceOver, "3. आवाज़ चुनें",
            if (tools.ttsEngine == "gemini") "${tools.ttsVoice} · Gemini voice" else "फ़ोन की आवाज़") {
            onOpen(ToolRoutes.Voice)
        }
        ToolCard(Icons.Default.Language, "4. भाषा (Language)",
            Languages.label(tools.sttLang)) { onOpen(ToolRoutes.Language) }
        ToolCard(Icons.Default.Memory, "5. Memory",
            if (tools.memoryEnabled) "${tools.memories.size} बातें याद हैं" else "बंद है") {
            onOpen(ToolRoutes.Memory)
        }
        ToolCard(Icons.Default.VpnKey, "6. API Key",
            if (tools.hasGeminiKey) "✔ Gemini key सेट है" else "Gemini key नहीं डाली") {
            onOpen(ToolRoutes.Keys)
        }
        ToolCard(Icons.Default.Security, "7. Permissions",
            "$grantedCount / ${AppPerm.entries.size} चालू") {
            onOpen(ToolRoutes.Permissions)
        }
    }
}

@Composable
private fun ToolCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun ToolPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        content()
    }
}

// ---------------------------------------------------------------- 1. account

@Composable
fun AccountScreen(tools: ToolsViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf(tools.userName) }
    ToolPage("खाता (Account)", onBack) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("आपका नाम", style = MaterialTheme.typography.titleMedium)
                Text("AI आपको इसी नाम से बुलाएगा।", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("जैसे: राहुल") }
                )
                Button(onClick = { tools.saveUserName(name) }, enabled = name.trim() != tools.userName) {
                    Text("Save")
                }
                if (tools.userName.isNotBlank()) Text("✔ Saved: ${tools.userName}")
            }
        }
        Text("Email / Google login बाद के Phase में जुड़ेगा।", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------- 2. assistant name

@Composable
fun AssistantNameScreen(tools: ToolsViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf(tools.assistantName) }
    ToolPage("Assistant का नाम", onBack) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("अपने assistant को नाम दीजिए", style = MaterialTheme.typography.titleMedium)
                Text("वह खुद को इसी नाम से बताएगा।", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { tools.saveAssistantName(name) },
                        enabled = name.trim().isNotEmpty() && name.trim() != tools.assistantName
                    ) { Text("Save") }
                    TextButton(onClick = {
                        tools.saveAssistantName(SecureStore.DEFAULT_ASSISTANT)
                        name = tools.assistantName
                    }) { Text("Default") }
                }
                Text("✔ अभी का नाम: ${tools.assistantName}")
            }
        }
    }
}

// ---------------------------------------------------------------- 3. voice

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceScreen(tools: ToolsViewModel, onBack: () -> Unit) {
    ToolPage("आवाज़ चुनें", onBack) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI की आवाज़", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Gemini voice इंसानों जैसी स्वाभाविक आवाज़ देती है। Free limit पूरी होने पर app अपने आप फ़ोन की आवाज़ पर लौट जाता है।",
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tools.ttsEngine == "gemini",
                        onClick = { tools.updateTtsEngine("gemini") },
                        label = { Text("Gemini voice (सुझाया)") }
                    )
                    FilterChip(
                        selected = tools.ttsEngine == "android",
                        onClick = { tools.updateTtsEngine("android") },
                        label = { Text("फ़ोन की आवाज़") }
                    )
                }
            }
        }
        if (tools.ttsEngine == "gemini") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("आवाज़ चुनिए", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TTS_VOICES.forEach { v ->
                            FilterChip(
                                selected = tools.ttsVoice == v,
                                onClick = { tools.updateTtsVoice(v) },
                                label = { Text(v) }
                            )
                        }
                    }
                    Button(onClick = tools::testVoice) { Text("आवाज़ सुनकर देखें") }
                    if (tools.voiceTestMsg.isNotBlank()) {
                        Text(tools.voiceTestMsg, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 4. language

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageScreen(tools: ToolsViewModel, onBack: () -> Unit) {
    ToolPage("भाषा (Language)", onBack) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("आप किस भाषा में बोलेंगे?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "AI आपकी भाषा में ही जवाब देगा। भोजपुरी और संस्कृत Gemini voice engine के साथ ही चलती हैं।",
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Languages.all.forEach { (label, tag) ->
                        FilterChip(
                            selected = tools.sttLang == tag,
                            onClick = { tools.updateLang(tag) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 5. memory

@Composable
fun MemoryScreen(tools: ToolsViewModel, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { tools.reloadMemories() }

    ToolPage("Memory", onBack) {
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Memory चालू", style = MaterialTheme.typography.titleMedium)
                    Text("AI इन बातों को याद रखकर जवाब देगा। बोलकर भी कह सकते हैं: \"याद रखो, मुझे चाय पसंद है\"।",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = tools.memoryEnabled, onCheckedChange = tools::updateMemoryEnabled)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("नई बात जोड़ें", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("जैसे: मेरा जन्मदिन 5 मई को है") }
                )
                Button(onClick = { tools.addMemory(text); text = "" }, enabled = text.isNotBlank()) {
                    Text("जोड़ें")
                }
            }
        }
        if (tools.memories.isEmpty()) {
            Text("अभी कुछ याद नहीं है।", style = MaterialTheme.typography.bodyMedium)
        } else {
            tools.memories.forEach { m ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(m.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { tools.deleteMemory(m.id) }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                }
            }
            TextButton(onClick = { confirmClear = true }) { Text("सब हटाएँ") }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("पूरी memory हटाएँ?") },
            text = { Text("याद रखी सारी बातें हमेशा के लिए हट जाएँगी।") },
            confirmButton = {
                TextButton(onClick = { tools.clearMemories(); confirmClear = false }) { Text("हटाएँ") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("रद्द करें") } }
        )
    }
}

// ---------------------------------------------------------------- 6. API keys

@Composable
fun ApiKeyScreen(tools: ToolsViewModel, onBack: () -> Unit) {
    ToolPage("API Key", onBack) {
        KeyCard(
            title = "Gemini API key",
            hint = "Google AI Studio (aistudio.google.com) से key बनाइए।",
            initial = tools.savedGemini, secret = true, onSave = tools::saveGemini
        )
        KeyCard(
            title = "Gemini model",
            hint = "Default: ${SecureStore.DEFAULT_MODEL}। नाम में space या extra शब्द न डालें।",
            initial = tools.savedModel, secret = false, onSave = tools::saveModel,
            resetValue = SecureStore.DEFAULT_MODEL
        )
        KeyCard(
            title = "Search API key (optional)",
            hint = "इंटरनेट खोज अभी Gemini के अपने Google Search से चलती है। यह key बाद के लिए सुरक्षित रखी जाती है।",
            initial = tools.savedSearch, secret = true, onSave = tools::saveSearch
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connection test", style = MaterialTheme.typography.titleMedium)
                Text("Gemini से connection और API key जाँचता है।", style = MaterialTheme.typography.bodySmall)
                Button(onClick = tools::runTest, enabled = !tools.testing) {
                    Text(if (tools.testing) "जाँच हो रही है…" else "Test करें")
                }
                if (tools.testResult.isNotBlank()) Text(tools.testResult)
            }
        }
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
