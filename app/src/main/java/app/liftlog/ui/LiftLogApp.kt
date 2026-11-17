package app.liftlog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.liftlog.ui.exercises.ExerciseDetailScreen
import app.liftlog.ui.exercises.ExercisesScreen
import app.liftlog.ui.home.HomeScreen
import app.liftlog.ui.settings.SettingsScreen
import app.liftlog.ui.workout.ActiveWorkoutScreen
import app.liftlog.ui.workout.ActiveWorkoutViewModel
import app.liftlog.ui.workout.WorkoutDetailScreen

/** Top-level destinations shown in the bottom bar. */
enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home),
    Programs("programs", "Programs", Icons.Default.DateRange),
    Exercises("exercises", "Exercises", Icons.AutoMirrored.Filled.List),
    Progress("progress", "Progress", Icons.Default.Star),
    Settings("settings", "Settings", Icons.Default.Settings),
}

private const val ID = "id"
private const val ROUTE_ACTIVE_WORKOUT = "workout/{$ID}"
private const val ROUTE_ADD_EXERCISE = "workout/{$ID}/add"
private const val ROUTE_WORKOUT_DETAIL = "history/{$ID}"
private const val ROUTE_EXERCISE_DETAIL = "exercises/{$ID}"

private val idArgument = listOf(navArgument(ID) { type = NavType.LongType })

private fun NavBackStackEntry.id(): Long = requireNotNull(arguments).getLong(ID)

@Composable
fun LiftLogApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTopLevelScreen = TopLevelDestination.entries.any { it.route == currentDestination?.route }

    Scaffold(
        // Each screen has its own top bar, which handles the status bar inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (onTopLevelScreen) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Home.route,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable(TopLevelDestination.Home.route) {
                HomeScreen(
                    onOpenActiveWorkout = { navController.navigate("workout/$it") },
                    onOpenFinishedWorkout = { navController.navigate("history/$it") },
                )
            }
            composable(TopLevelDestination.Programs.route) { ComingSoonScreen("Programs") }
            composable(TopLevelDestination.Exercises.route) {
                ExercisesScreen(
                    title = "Exercises",
                    onExerciseClick = { navController.navigate("exercises/${it.id}") },
                )
            }
            composable(TopLevelDestination.Progress.route) { ComingSoonScreen("Progress") }
            composable(TopLevelDestination.Settings.route) { SettingsScreen() }

            composable(ROUTE_ACTIVE_WORKOUT, arguments = idArgument) { entry ->
                ActiveWorkoutScreen(
                    viewModel = activeWorkoutViewModel(entry),
                    onAddExercise = { navController.navigate("workout/${entry.id()}/add") },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(ROUTE_ADD_EXERCISE, arguments = idArgument) { entry ->
                // Shares the view model of the workout screen underneath it. The lookup is
                // remembered because this screen keeps composing while it animates away,
                // by which time "previous" would be a different screen.
                val workoutEntry = remember(entry) { navController.previousBackStackEntry } ?: return@composable
                val viewModel = activeWorkoutViewModel(workoutEntry)
                ExercisesScreen(
                    title = "Add exercise",
                    onExerciseClick = {
                        // Ignore a second tap that lands during the closing animation.
                        if (navController.currentBackStackEntry == entry) {
                            viewModel.addExercise(it.id)
                            navController.popBackStack()
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_WORKOUT_DETAIL, arguments = idArgument) { entry ->
                WorkoutDetailScreen(workoutId = entry.id(), onBack = { navController.popBackStack() })
            }
            composable(ROUTE_EXERCISE_DETAIL, arguments = idArgument) { entry ->
                ExerciseDetailScreen(exerciseId = entry.id(), onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun activeWorkoutViewModel(workoutEntry: NavBackStackEntry): ActiveWorkoutViewModel {
    val container = appContainer()
    val workoutId = workoutEntry.id()
    return viewModel(viewModelStoreOwner = workoutEntry) {
        ActiveWorkoutViewModel(workoutId, container.workoutDao, container.settings, container.restTimer)
    }
}

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun ComingSoonScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title: coming in a later milestone", style = MaterialTheme.typography.bodyLarge)
    }
}
