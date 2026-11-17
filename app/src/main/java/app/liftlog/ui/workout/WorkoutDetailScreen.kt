package app.liftlog.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.liftlog.data.SettingsRepository
import app.liftlog.data.db.Workout
import app.liftlog.data.db.WorkoutDao
import app.liftlog.data.db.WorkoutExerciseWithSets
import app.liftlog.domain.WeightUnit
import app.liftlog.domain.formatSet
import app.liftlog.ui.appContainer
import app.liftlog.ui.formatDateTime
import app.liftlog.ui.formatDurationCoarse
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutDetailState(
    val workout: Workout? = null,
    val exercises: List<WorkoutExerciseWithSets> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
)

class WorkoutDetailViewModel(
    private val workoutId: Long,
    private val workoutDao: WorkoutDao,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<WorkoutDetailState> = combine(
        workoutDao.observeWorkout(workoutId),
        workoutDao.observeExercises(workoutId),
        settings.weightUnit,
        ::WorkoutDetailState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailState())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            workoutDao.deleteWorkout(workoutId)
            onDone()
        }
    }
}

/** Read-only view of a finished workout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(workoutId: Long, onBack: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel(key = "workout-detail-$workoutId") {
        WorkoutDetailViewModel(workoutId, container.workoutDao, container.settings)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val workout = state.workout

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.let { formatDateTime(it.startedAt) } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete workout")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val finishedAt = workout?.finishedAt
            if (workout != null && finishedAt != null) {
                item {
                    Text(
                        "Duration: ${formatDurationCoarse((finishedAt - workout.startedAt) / 1000)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.exercises, key = { it.workoutExercise.id }) { exercise ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(exercise.exercise.name, style = MaterialTheme.typography.titleSmall)
                        exercise.sets.forEachIndexed { index, set ->
                            val suffix = if (set.weightKg != null && set.reps != null) state.unit.label else ""
                            Text("${index + 1}.  ${formatSet(set.weightKg, set.reps, state.unit)} $suffix")
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete workout?") },
            text = { Text("This workout and its sets will be removed from your history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
