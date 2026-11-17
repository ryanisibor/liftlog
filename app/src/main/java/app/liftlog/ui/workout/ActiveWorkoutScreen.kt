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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import app.liftlog.ui.formatDateTime
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

    // After a reorder, stay on the exercise that was moved. The scroll waits for the new
    // order to arrive from the database; scrolling earlier would land on its neighbour.
    var followId by remember { mutableStateOf<Long?>(null) }
    val order = exercises.map { it.workoutExercise.id }
    LaunchedEffect(order) {
        val target = followId ?: return@LaunchedEffect
        val index = order.indexOf(target)
        if (index >= 0) pagerState.scrollToPage(index)
        followId = null
    }

    val dayName by viewModel.dayName.collectAsStateWithLifecycle()
    val isEditing = state?.isEditing == true
    // Leaving an edited workout by any route saves it, so unticked rows never linger in history.
    val saveEdits = { viewModel.doneEditing { onClose() } }
    BackHandler(enabled = isEditing, onBack = saveEdits)

    val totalSets = exercises.sumOf { it.sets.size }
    val doneSets = exercises.sumOf { exercise -> exercise.sets.count { it.completed } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    state?.let {
                        Column {
                            Text(
                                if (isEditing) "Editing ${dayName ?: "workout"}" else dayName ?: "Workout",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            Text(
                                if (isEditing) formatDateTime(it.workout.startedAt) else formatDuration((now - it.workout.startedAt) / 1000),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isEditing) {
                        IconButton(onClick = saveEdits) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and go back")
                        }
                    } else {
                        IconButton(onClick = { showOptions = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Workout options")
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = { if (isEditing) saveEdits() else confirmFinish = true },
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text(if (isEditing) "Done" else "Finish")
                    }
                },
            )
        },
        bottomBar = {
            // A finished workout being corrected has no rest timer and no progress to show.
            if (!isEditing) Column(modifier = Modifier.navigationBarsPadding()) {
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
            if (isEditing) WorkoutDateRow(current.workout.startedAt, onChange = viewModel::changeStart)
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
                        target = current.targets[exercise.exercise.id],
                        actions = ExercisePageActions(
                            onMoveEarlier = if (page == 0) null else {
                                { followId = exercise.workoutExercise.id; viewModel.moveExercise(exercise, -1) }
                            },
                            onMoveLater = if (page == exercises.lastIndex) null else {
                                { followId = exercise.workoutExercise.id; viewModel.moveExercise(exercise, 1) }
                            },
                            onWeightTyped = { setId, text -> viewModel.onWeightTyped(setId, text, current.unit) },
                            onRepsTyped = viewModel::onRepsTyped,
                            onToggle = { set, previous ->
                                viewModel.toggleCompleted(set, previous, current.restSecondsFor(exercise.exercise.id))
                            },
                            onToggleWarmup = viewModel::toggleWarmup,
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
            initial = exercise.exercise.notes,
            onDismiss = { notesFor = null },
            onSave = { viewModel.setExerciseNotes(exercise.exercise.id, it); notesFor = null },
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
