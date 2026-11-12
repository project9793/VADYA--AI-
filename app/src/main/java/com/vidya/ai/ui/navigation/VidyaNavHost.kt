package com.vidya.ai.ui.navigation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.vidya.ai.ui.assistant.AssistantViewModel
import com.vidya.ai.ui.chat.ChatScreen
import com.vidya.ai.ui.common.ComingSoonScreen
import com.vidya.ai.ui.history.HistoryScreen
import com.vidya.ai.ui.home.HomeScreen
import com.vidya.ai.ui.settings.SettingsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home),
    Chat("chat", "Chat", Icons.Default.Chat),
    History("history", "History", Icons.Default.History),
    Tools("tools", "Tools", Icons.Default.Build),
    Settings("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun VidyaNavHost() {
    val vm: AssistantViewModel = viewModel()

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { go(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = {
                            Text(tab.label, maxLines = 1, softWrap = false,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            nav, startDestination = Tab.Home.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        ) {
            composable(Tab.Home.route) { HomeScreen(vm, onOpenChat = { go(Tab.Chat.route) }) }
            composable(Tab.Chat.route) { ChatScreen(vm) }
            composable(Tab.History.route) { HistoryScreen(vm, onOpen = { go(Tab.Chat.route) }) }
            composable(Tab.Tools.route) { ComingSoonScreen("Tools", "Phase 8–10") }
            composable(Tab.Settings.route) { SettingsScreen() }
        }
    }
}
