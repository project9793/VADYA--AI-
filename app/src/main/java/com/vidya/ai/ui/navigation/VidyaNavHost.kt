package com.vidya.ai.ui.navigation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidya.ai.ui.tools.GuideScreen
import com.vidya.ai.ui.tools.PhoneControlScreen
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.vidya.ai.ui.assistant.AssistantViewModel
import com.vidya.ai.ui.chat.ChatScreen
import com.vidya.ai.ui.history.HistoryScreen
import com.vidya.ai.ui.home.HomeScreen
import com.vidya.ai.ui.settings.SettingsScreen
import com.vidya.ai.ui.tools.AccountScreen
import com.vidya.ai.ui.tools.ApiKeyScreen
import com.vidya.ai.ui.tools.AssistantNameScreen
import com.vidya.ai.ui.tools.LanguageScreen
import com.vidya.ai.ui.tools.MemoryScreen
import com.vidya.ai.ui.tools.PermissionsScreen
import com.vidya.ai.ui.tools.ToolRoutes
import com.vidya.ai.ui.tools.ToolsHomeScreen
import com.vidya.ai.ui.tools.ToolsViewModel
import com.vidya.ai.ui.tools.VoiceScreen
import com.vidya.ai.ui.theme.VidyaViolet

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home),
    Chat("chat", "Chat", Icons.Default.Chat),
    History("history", "History", Icons.Default.History),
    Tools(ToolRoutes.Home, "Tools", Icons.Default.Build),
    Settings("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun VidyaNavHost() {
    val vm: AssistantViewModel = viewModel()
    val tools: ToolsViewModel = viewModel()

    // Google's voice window (fallback speech input)
    val dialogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !t.isNullOrBlank()) vm.onDialogResult(t)
        else vm.onDialogCancel()
    }
    LaunchedEffect(Unit) {
        vm.dialogEvents.collect {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, vm.speechLang)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "बोलिए…")
            }
            try {
                dialogLauncher.launch(i)
            } catch (e: ActivityNotFoundException) {
                vm.showError("इस फ़ोन में Google voice input उपलब्ध नहीं है। Google app install/update करें।")
                vm.onDialogCancel()
            }
        }
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    fun go(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun open(route: String) {
        nav.navigate(route) { launchSingleTop = true }
    }
    val back: () -> Unit = { nav.popBackStack() }

    // the guide can take the user to the right screen, and speaks about each screen the first time
    val ui by vm.ui.collectAsStateWithLifecycle()
    fun navigateTo(route: String) {
        if (Tab.entries.any { it.route == route }) go(route)
        else { go(Tab.Tools.route); open(route) }
    }
    LaunchedEffect(Unit) { vm.navEvents.collect { navigateTo(it) } }
    LaunchedEffect(current) { current?.let { vm.onScreen(it) } }

    // permissions asked right when a command needs them (call, contacts ...)
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPermissionResult() }
    LaunchedEffect(Unit) { vm.permEvents.collect { permLauncher.launch(it) } }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            NavigationBar(containerColor = Color(0xF00D1650)) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route || current?.startsWith(tab.route + "/") == true,
                        onClick = { go(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = {
                            Text(tab.label, maxLines = 1, softWrap = false,
                                style = MaterialTheme.typography.labelSmall)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = VidyaViolet.copy(alpha = 0.55f),
                            unselectedIconColor = Color(0xFFB8C2FF),
                            unselectedTextColor = Color(0xFFB8C2FF)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) {
        NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.fillMaxSize()) {
            composable(Tab.Home.route) { HomeScreen(vm, tools, onOpenChat = { go(Tab.Chat.route) }) }
            composable(Tab.Chat.route) { ChatScreen(vm) }
            composable(Tab.History.route) { HistoryScreen(vm, onOpen = { go(Tab.Chat.route) }) }
            composable(ToolRoutes.Home) { ToolsHomeScreen(tools, onOpen = { open(it) }) }
            composable(ToolRoutes.Account) { AccountScreen(tools, back) }
            composable(ToolRoutes.Assistant) { AssistantNameScreen(tools, back) }
            composable(ToolRoutes.Voice) { VoiceScreen(tools, back) }
            composable(ToolRoutes.Language) { LanguageScreen(tools, back) }
            composable(ToolRoutes.Memory) { MemoryScreen(tools, back) }
            composable(ToolRoutes.Keys) { ApiKeyScreen(tools, back) }
            composable(ToolRoutes.Permissions) { PermissionsScreen(back) }
            composable(ToolRoutes.Guide) { GuideScreen(vm, tools, back) }
            composable(ToolRoutes.PhoneControl) {
                PhoneControlScreen(tools, back, onPermissions = { open(ToolRoutes.Permissions) })
            }
            composable(Tab.Settings.route) { SettingsScreen(tools, onGoTools = { go(Tab.Tools.route) }) }
        }
        if (ui.tourOn) {
            TourBar(ui.guide?.title ?: "Tour", onStop = vm::stopTour, modifier = Modifier.align(Alignment.TopCenter))
        }
        }
    }
}

@Composable
private fun TourBar(title: String, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xEE1B2A7A)
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🧭 $title", Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLarge, color = Color.White)
            TextButton(onClick = onStop) { Text("रोकें") }
        }
    }
}
