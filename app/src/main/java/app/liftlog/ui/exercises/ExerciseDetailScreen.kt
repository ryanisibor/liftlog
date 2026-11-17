package app.liftlog.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import app.liftlog.ui.components.MuscleDiagramPair
import androidx.compose.runtime.remember
import android.database.sqlite.SQLiteConstraintException
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.liftlog.data.Equipment
import app.liftlog.data.Muscle
import kotlinx.coroutines.launch
import app.liftlog.domain.LoggedSet
import app.liftlog.domain.setLabels
import app.liftlog.domain.sessionStats
import app.liftlog.ui.progress.ExerciseProgressSection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.liftlog.data.SettingsRepository
import app.liftlog.data.db.Exercise
import app.liftlog.data.db.ExerciseDao
import app.liftlog.data.db.ExerciseHistoryRow
import app.liftlog.data.db.WorkoutDao
import app.liftlog.domain.WeightUnit
import app.liftlog.domain.formatSet
import app.liftlog.ui.appContainer
import app.liftlog.ui.formatDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ExerciseSession(val workoutId: Long, val startedAt: Long, val sets: List<ExerciseHistoryRow>)

/** Rows arrive newest session first; groupBy keeps that order. */
fun groupIntoSessions(rows: List<ExerciseHistoryRow>): List<ExerciseSession> =
    rows.groupBy { it.workoutId }.map { (workoutId, sets) ->
        ExerciseSession(workoutId, sets.first().startedAt, sets)
    }

data class ExerciseDetailState(
    val exercise: Exercise? = null,
    val sessions: List<ExerciseSession> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
)

class ExerciseDetailViewModel(
    private val exerciseId: Long,
    private val exerciseDao: ExerciseDao,
    workoutDao: WorkoutDao,
    settings: SettingsRepository,
) : ViewModel() {
    /** Saves changes to a custom exercise. [onResult] gets null on success or a message to show. */
    fun update(name: String, muscle: Muscle, equipment: Equipment, onResult: (String?) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return onResult("Enter a name")
        if (trimmed.length > ExercisesViewModel.MAX_NAME_LENGTH) return onResult("Name is too long")
        viewModelScope.launch {
            try {
                exerciseDao.updateCustom(exerciseId, trimmed, muscle, equipment)
                onResult(null)
            } catch (_: SQLiteConstraintException) {
                onResult("An exercise with that name already exists")
            }
        }
    }

    /** Hides the exercise from the library. Workouts that used it keep their history. */
    fun hide(onDone: () -> Unit) {
        viewModelScope.launch {
            exerciseDao.setArchived(exerciseId, true)
            onDone()
        }
    }

    val state: StateFlow<ExerciseDetailState> = combine(
        exerciseDao.observe(exerciseId),
        workoutDao.observeExerciseHistory(exerciseId),
        settings.weightUnit,
    ) { exercise, rows, unit ->
        ExerciseDetailState(exercise, groupIntoSessions(rows), unit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(exerciseId: Long, onBack: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel(key = "exercise-$exerciseId") {
        ExerciseDetailViewModel(exerciseId, container.exerciseDao, container.workoutDao, container.settings)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exercise = state.exercise
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var confirmHide by rememberSaveable { mutableStateOf(false) }

    if (showEdit && exercise != null) {
        AddExerciseDialog(
            initialName = exercise.name,
            onDismiss = { showEdit = false },
            onAdd = { name, muscle, equipment, onResult ->
                viewModel.update(name, muscle, equipment) { error ->
                    if (error == null) showEdit = false
                    onResult(error)
                }
            },
            title = "Edit exercise",
            confirmLabel = "Save",
            initialMuscle = exercise.primaryMuscle,
            initialEquipment = exercise.equipment,
        )
    }
    if (confirmHide && exercise != null) {
        AlertDialog(
            onDismissRequest = { confirmHide = false },
            title = { Text("Remove ${exercise.name}?") },
            text = {
                Text(
                    "It will disappear from the library and pickers. Workouts you've logged with it keep " +
                        "their history. Adding an exercise with the same name later brings it back.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmHide = false; viewModel.hide(onBack) }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmHide = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Only exercises the user created can be changed; the built-in library is fixed.
                    if (exercise?.isCustom == true) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit exercise")
                        }
                        IconButton(onClick = { confirmHide = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove exercise from library")
                        }
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
            if (exercise != null) {
                item {
                    // Details on the left, muscle map on the right, as on the workout screen,
                    // so the chart below starts near the top of the page.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text("Main muscle: ${exercise.primaryMuscle.label}")
                            if (exercise.secondaryMuscles.isNotEmpty()) {
                                Text("Also works: ${exercise.secondaryMuscles.joinToString { it.label }}")
                            }
                            Text("Equipment: ${exercise.equipment.label}")
                            if (exercise.notes.isNotEmpty()) {
                                Text("Note: ${exercise.notes}", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        MuscleDiagramPair(
                            exercise.primaryMuscle,
                            exercise.secondaryMuscles,
                            Modifier.height(112.dp),
                        )
                    }
                }
            }
            if (state.sessions.isNotEmpty()) {
                item {
                    // Charts read left to right in time, so oldest session first.
                    val stats = remember(state.sessions) {
                        state.sessions.reversed().map { session ->
                            // Warm-ups are left out of charts and records.
                            sessionStats(
                                session.startedAt,
                                session.sets.filter { !it.isWarmup }.map { LoggedSet(it.weightKg, it.reps) },
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        ExerciseProgressSection(stats, state.unit)
                    }
                }
            }
            item {
                Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            }
            if (state.sessions.isEmpty()) {
                item {
                    Text(
                        "You haven't logged this exercise yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.sessions, key = { it.workoutId }) { session ->
                ExerciseSessionCard(session, state.unit)
            }
        }
    }
}

/** One past session of an exercise: the date and each completed set. */
@Composable
fun ExerciseSessionCard(session: ExerciseSession, unit: WeightUnit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatDate(session.startedAt), style = MaterialTheme.typography.titleSmall)
            val labels = setLabels(session.sets.map { it.isWarmup })
            session.sets.forEachIndexed { index, set ->
                Text(
                    "${labels[index]}.  ${formatSet(set.weightKg, set.reps, unit)} ${weightSuffix(set, unit)}",
                    color = if (set.isWarmup) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun weightSuffix(set: ExerciseHistoryRow, unit: WeightUnit): String =
    if (set.weightKg != null && set.reps != null) unit.label else ""
