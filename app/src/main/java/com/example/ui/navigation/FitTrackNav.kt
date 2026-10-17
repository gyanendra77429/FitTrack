package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.ui.achievements.AchievementsScreen
import com.example.ui.activity.ActivityHistoryScreen
import com.example.ui.dashboard.HomeScreen
import com.example.ui.reports.ReportsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.viewmodel.FitTrackViewModel
import com.example.ui.workouts.WorkoutsScreen

enum class Screen(val title: String, val icon: ImageVector, val tag: String) {
    HOME("Home", Icons.Default.DirectionsWalk, "nav_home"),
    ACTIVITY("Activity", Icons.Default.Timeline, "nav_activity"),
    WORKOUTS("Workouts", Icons.Default.FitnessCenter, "nav_workouts"),
    REPORTS("Reports", Icons.Default.BarChart, "nav_reports"),
    ACHIEVEMENTS("Badges", Icons.Default.EmojiEvents, "nav_achievements"),
    SETTINGS("Settings", Icons.Default.Settings, "nav_settings")
}

@Composable
fun FitTrackAppNavigation(
    viewModel: FitTrackViewModel,
    onSignOutComplete: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    // Intercept back presses on secondary screens to return to Home
    if (currentScreen != Screen.HOME) {
        BackHandler {
            currentScreen = Screen.HOME
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Screen.values().forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag(screen.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToWorkouts = { currentScreen = Screen.WORKOUTS },
                    onNavigateToAchievements = { currentScreen = Screen.ACHIEVEMENTS }
                )
                Screen.ACTIVITY -> ActivityHistoryScreen(
                    viewModel = viewModel
                )
                Screen.WORKOUTS -> WorkoutsScreen(
                    viewModel = viewModel
                )
                Screen.REPORTS -> ReportsScreen(
                    viewModel = viewModel
                )
                Screen.ACHIEVEMENTS -> AchievementsScreen(
                    viewModel = viewModel
                )
                Screen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onSignOutComplete = onSignOutComplete
                )
            }
        }
    }
}
