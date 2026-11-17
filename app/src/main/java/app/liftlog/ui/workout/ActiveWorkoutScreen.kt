package app.liftlog.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftlog.data.db.Exercise
import app.liftlog.data.db.WorkoutExerciseWithSets
import app.liftlog.domain.WeightUnit
import app.liftlog.ui.formatDuration
import kotlinx.coroutines.launch

/**
 * The in-progress workout. One exercise fills the screen at a time; the strip along the top
 * jumps between exercises, and swiping sideways moves to the next one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    viewModel: ActiveWorkoutViewModel,
    onAddExercise: () -> Unit,
    onSwapExercise: (workoutExerciseId: Long) -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restEndsAt by viewModel.restEndsAt.collectAsStateWithLifecycle()
    val now = currentTimeTicker()
    val scope = rememberCoroutineScope()

    val exercises = state?.exercises.orEmpty()
    val exerciseCount by rememberUpdatedState(exercises.size)
    val pagerState = rememberPagerState { exerciseCount }

    var showOptions by remember { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var historyFor by remember { mutableStateOf<Exercise?>(null) }
    var notesFor by remember { mutableStateOf<WorkoutExerciseWithSets?>(null) }
    var confirmRemove by remember { mutableStateOf<WorkoutExerciseWithSets?>(null) }

    RequestNotificationPermission()

    // When an exercise is added, jump to it. -1 means "not loaded yet", so opening the
    // screen does not jump to the end.
    var knownCount by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(state != null, exercises.size) {
        if (state == null) return@LaunchedEffect
        if (knownCount in 0 until exercises.size) pagerState.animateScrollToPage(exercises.size - 1)
        knownCount = exercises.size
    }

    val totalSets = exercises.sumOf { it.sets.size }
    val doneSets = exercises.sumOf { exercise -> exercise.sets.count { it.completed } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    state?.let {
                        Text(
                            formatDuration((now - it.workout.startedAt) / 1000),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showOptions = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Workout options")
                    }
                },
                actions = {
                    Button(onClick = { confirmFinish = true }, modifier = Modifier.padding(end = 12.dp)) {
                        Text("Finish")
                    }
                },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                val endsAt = restEndsAt
                if (endsAt != null && endsAt > now) {
                    RestTimerBar(
                        secondsLeft = (endsAt - now + 999) / 1000,
                        onAddTime = { viewModel.addRestTime(30) },
                        onSkip = viewModel::skipRest,
                    )
                }
                LinearProgressIndicator(
                    progress = { if (totalSets == 0) 0f else doneSets.toFloat() / totalSets },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }
        },
    ) { padding ->
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ExerciseStrip(
                exercises = exercises,
                selectedIndex = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                onAdd = onAddExercise,
            )
            if (exercises.isEmpty()) {
                EmptyWorkout(onAddExercise)
            } else {
                HorizontalPager(
                    state = pagerState,
                    key = { exercises.getOrNull(it)?.workoutExercise?.id ?: it },
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val exercise = exercises.getOrNull(page) ?: return@HorizontalPager
                    ExercisePage(
                        exercise = exercise,
                        previousSets = current.previousSets[exercise.workoutExercise.id].orEmpty(),
                        unit = current.unit,
                        actions = ExercisePageActions(
                            onWeightTyped = { setId, text -> viewModel.onWeightTyped(setId, text, current.unit) },
                            onRepsTyped = viewModel::onRepsTyped,
                            onToggle = { set, previous -> viewModel.toggleCompleted(set, previous, current.restSeconds) },
                            onDeleteSet = viewModel::deleteSet,
                            onAddSet = { viewModel.addSet(exercise) },
                            onRemoveLastSet = { viewModel.removeLastSet(exercise) },
                            onHistory = { historyFor = exercise.exercise },
                            onSwap = { onSwapExercise(exercise.workoutExercise.id) },
                            onNotes = { notesFor = exercise },
                            onRemove = { confirmRemove = exercise },
                        ),
                    )
                }
            }
        }
    }

    if (showOptions) {
        WorkoutOptionsSheet(
            onDismiss = { showOptions = false },
            onExit = { showOptions = false; onClose() },
            onFinish = { showOptions = false; confirmFinish = true },
            onDiscard = { showOptions = false; confirmDiscard = true },
        )
    }

    historyFor?.let { exercise ->
        ExerciseHistorySheet(exercise, state?.unit ?: WeightUnit.KG, onDismiss = { historyFor = null })
    }

    notesFor?.let { exercise ->
        NotesDialog(
            exerciseName = exercise.exercise.name,
            initial = exercise.workoutExercise.notes,
            onDismiss = { notesFor = null },
            onSave = { viewModel.setNotes(exercise.workoutExercise.id, it); notesFor = null },
        )
    }

    confirmRemove?.let { exercise ->
        ConfirmDialog(
            title = "Remove ${exercise.exercise.name}?",
            text = "The exercise and any sets logged for it in this workout will be removed.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeExercise(exercise.workoutExercise.id); confirmRemove = null },
            onDismiss = { confirmRemove = null },
        )
    }

    if (confirmFinish) {
        ConfirmDialog(
            title = "Finish workout?",
            text = if (doneSets == 0) {
                "No sets are ticked, so nothing will be saved."
            } else {
                "$doneSets completed sets will be saved. Sets you haven't ticked are dropped."
            },
            confirmLabel = "Finish",
            dismissLabel = "Keep going",
            onConfirm = { confirmFinish = false; viewModel.finish { onClose() } },
            onDismiss = { confirmFinish = false },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard workout?",
            text = "Everything logged in this workout will be deleted. This can't be undone.",
            confirmLabel = "Discard",
            onConfirm = { confirmDiscard = false; viewModel.discard(onClose) },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}
