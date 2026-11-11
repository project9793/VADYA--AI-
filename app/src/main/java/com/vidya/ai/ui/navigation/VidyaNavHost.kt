package com.vidya.ai.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vidya.ai.R
import com.vidya.ai.ui.common.ComingSoonScreen
import com.vidya.ai.ui.home.HomeScreen

/** Top-level destinations shown in the bottom bar. */
enum class VidyaDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Home("home", R.string.nav_home, Icons.Rounded.Home),
    Tutor("tutor", R.string.nav_tutor, Icons.Rounded.Search),
    Notes("notes", R.string.nav_notes, Icons.Rounded.Edit),
    Quiz("quiz", R.string.nav_quiz, Icons.Rounded.PlayArrow),
    Profile("profile", R.string.nav_profile, Icons.Rounded.Person)
}

@Composable
fun VidyaNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: VidyaDestination.Home.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                VidyaDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes)
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = VidyaDestination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(VidyaDestination.Home.route) {
                HomeScreen(
                    onNavigate = { route ->
                        navController.navigate(route) { launchSingleTop = true }
                    }
                )
            }
            composable(VidyaDestination.Tutor.route) {
                ComingSoonScreen(
                    title = stringResource(R.string.tutor_title),
                    description = stringResource(R.string.tutor_body),
                    icon = Icons.Rounded.Search,
                    onBack = { navController.popBackStack(VidyaDestination.Home.route, false) }
                )
            }
            composable(VidyaDestination.Notes.route) {
                ComingSoonScreen(
                    title = stringResource(R.string.notes_title),
                    description = stringResource(R.string.notes_body),
                    icon = Icons.Rounded.Edit,
                    onBack = { navController.popBackStack(VidyaDestination.Home.route, false) }
                )
            }
            composable(VidyaDestination.Quiz.route) {
                ComingSoonScreen(
                    title = stringResource(R.string.quiz_title),
                    description = stringResource(R.string.quiz_body),
                    icon = Icons.Rounded.PlayArrow,
                    onBack = { navController.popBackStack(VidyaDestination.Home.route, false) }
                )
            }
            composable(VidyaDestination.Profile.route) {
                ComingSoonScreen(
                    title = stringResource(R.string.profile_title),
                    description = stringResource(R.string.profile_body),
                    icon = Icons.Rounded.Person,
                    onBack = { navController.popBackStack(VidyaDestination.Home.route, false) }
                )
            }
        }
    }
}
