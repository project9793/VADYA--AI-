package com.vidya.ai.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vidya.ai.core.AppPerm
import com.vidya.ai.core.Permissions

private class PermInfo(val perm: AppPerm, val title: String, val icon: ImageVector, val desc: String)

private val PERMS = listOf(
    PermInfo(AppPerm.Mic, "Microphone 🎤", Icons.Default.Mic,
        "बोलकर बात करने के लिए।"),
    PermInfo(AppPerm.Camera, "Camera 📸", Icons.Default.CameraAlt,
        "फ़ोटो लेकर उसके बारे में पूछने के लिए।"),
    PermInfo(AppPerm.Media, "Photos & videos 🖼️", Icons.Default.PhotoLibrary,
        "गैलरी की फ़ोटो/वीडियो चुनने के लिए।"),
    PermInfo(AppPerm.Location, "Location 📍", Icons.Default.LocationOn,
        "आस-पास की जानकारी (मौसम, जगह) के लिए।"),
    PermInfo(AppPerm.Contacts, "Contacts 👤", Icons.Default.Contacts,
        "नाम बोलकर call / WhatsApp करने के लिए।"),
    PermInfo(AppPerm.Calls, "Phone calls 📞", Icons.Default.Call,
        "call लगाने और call काटने के लिए।"),
    PermInfo(AppPerm.Notifications, "Notification 🔔", Icons.Default.Notifications,
        "reminder और सूचनाएँ दिखाने के लिए।"),
    PermInfo(AppPerm.Screen, "Screen 🖥️", Icons.Default.ScreenShare,
        "दूसरे apps के ऊपर दिखने की अनुमति। (दूसरे app की स्क्रीन पढ़ने या उसमें खुद टाइप करने के लिए Accessibility अलग से चाहिए, वह अभी नहीं जोड़ा।)")
)

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++ }

    @Suppress("UNUSED_VARIABLE") val refresh = tick     // re-read statuses after any change

    fun allow(p: AppPerm) {
        val req = Permissions.runtimePerms(p)
        if (req.isEmpty()) Permissions.openSettingsFor(ctx, p) else launcher.launch(req)
    }

    val grantedCount = PERMS.count { Permissions.isGranted(ctx, it.perm) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Permissions", style = MaterialTheme.typography.headlineSmall)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$grantedCount / ${PERMS.size} चालू", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    val all = listOf(AppPerm.Mic, AppPerm.Camera, AppPerm.Media, AppPerm.Location, AppPerm.Contacts, AppPerm.Calls, AppPerm.Notifications)
                        .flatMap { Permissions.runtimePerms(it).toList() }.distinct()
                    launcher.launch(all.toTypedArray())
                }) { Text("सब Allow करें") }
                Text(
                    "Switch ON करने पर permission माँगी जाती है। Android खुद app को permission बंद करने नहीं देता, " +
                        "इसलिए Switch OFF करने पर फ़ोन की Settings खुलती है जहाँ \"Don't allow\" चुनें।",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        PERMS.forEach { info ->
            val granted = Permissions.isGranted(ctx, info.perm)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(info.icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (granted) "✔ चालू (ON)" else "✖ बंद (OFF)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = granted,
                            onCheckedChange = { on ->
                                if (on) allow(info.perm) else Permissions.openSettingsFor(ctx, info.perm)
                            }
                        )
                    }
                    Text(info.desc, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!granted) {
                            Button(onClick = { allow(info.perm) }) { Text("Allow permission") }
                            OutlinedButton(onClick = { Permissions.openSettingsFor(ctx, info.perm) }) {
                                Text("Settings")
                            }
                        } else {
                            OutlinedButton(onClick = { Permissions.openSettingsFor(ctx, info.perm) }) {
                                Text("बंद करें (Settings)")
                            }
                        }
                    }
                }
            }
        }
    }
}
