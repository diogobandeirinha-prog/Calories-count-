package com.caloriescount.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.caloriescount.app.ui.screens.CaptureScreen
import com.caloriescount.app.ui.screens.HistoryScreen
import com.caloriescount.app.ui.screens.OnboardingScreen
import com.caloriescount.app.ui.screens.SettingsScreen
import com.caloriescount.app.ui.viewmodel.AppViewModelFactory
import com.caloriescount.app.ui.viewmodel.OnboardingViewModel

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Capture("capture", "Add", Icons.Filled.CameraAlt),
    History("history", "Stats", Icons.Filled.BarChart),
    Settings("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun AppRoot(
    onboardingViewModel: OnboardingViewModel = viewModel(factory = AppViewModelFactory)
) {
    val profileState by onboardingViewModel.state.collectAsState()

    when {
        // First frame while the persisted profile loads.
        profileState == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        // First run (or "edit profile"): collect goals before entering the app.
        profileState?.onboardingComplete == false -> OnboardingScreen(onComplete = {})
        else -> MainScaffold()
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Capture.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Capture.route) {
                CaptureScreen(onSaved = { navController.navigate(Destination.History.route) })
            }
            composable(Destination.History.route) { HistoryScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}
