package app.liftlog.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.liftlog.data.db.Workout
import app.liftlog.data.db.WorkoutDao
import app.liftlog.data.db.WorkoutSummary
import app.liftlog.ui.appContainer
import app.liftlog.ui.formatDateTime
import app.liftlog.ui.formatDurationCoarse
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val workoutDao: WorkoutDao) : ViewModel() {
    val activeWorkout: StateFlow<Workout?> =
        workoutDao.observeActiveWorkout().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<WorkoutSummary>> =
        workoutDao.observeFinishedWorkouts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Opens the in-progress workout if there is one, otherwise starts a new one. */
    fun startOrResume(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = workoutDao.activeWorkoutId()
                ?: workoutDao.insertWorkout(Workout(startedAt = System.currentTimeMillis()))
            onReady(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenActiveWorkout: (Long) -> Unit,
    onOpenFinishedWorkout: (Long) -> Unit,
) {
    val container = appContainer()
    val viewModel = viewModel { HomeViewModel(container.workoutDao) }
    val activeWorkout by viewModel.activeWorkout.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("LiftLog") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val active = activeWorkout
                if (active != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ListItem(
                            headlineContent = { Text("Workout in progress") },
                            supportingContent = { Text("Started ${formatDateTime(active.startedAt)}") },
                            trailingContent = {
                                Button(onClick = { onOpenActiveWorkout(active.id) }) { Text("Resume") }
                            },
                            colors = androidx.compose.material3.ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.startOrResume(onOpenActiveWorkout) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Start empty workout")
                    }
                }
            }

            item {
                Text(
                    "Recent workouts",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            if (history.isEmpty()) {
                item {
                    Text(
                        "No workouts yet. Finished workouts appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(history, key = { it.id }) { workout ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenFinishedWorkout(workout.id) }) {
                    ListItem(
                        headlineContent = { Text(formatDateTime(workout.startedAt)) },
                        supportingContent = {
                            val duration = formatDurationCoarse((workout.finishedAt - workout.startedAt) / 1000)
                            Text("${workout.exerciseCount} exercises · ${workout.setCount} sets · $duration")
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}
