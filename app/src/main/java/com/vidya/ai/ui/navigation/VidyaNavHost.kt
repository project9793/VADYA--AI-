package com.vidya.ai.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.vidya.ai.ui.common.ComingSoonScreen
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
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Home.route) { HomeScreen() }
            composable(Tab.Chat.route) { ComingSoonScreen("Chat", "Phase 3") }
            composable(Tab.History.route) { ComingSoonScreen("History", "Phase 11") }
            composable(Tab.Tools.route) { ComingSoonScreen("Tools", "Phase 8–10") }
            composable(Tab.Settings.route) { SettingsScreen() }
        }
    }
}
