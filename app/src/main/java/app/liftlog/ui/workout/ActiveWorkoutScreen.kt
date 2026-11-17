package app.liftlog.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftlog.data.db.SetEntry
import app.liftlog.data.db.WorkoutExerciseWithSets
import app.liftlog.domain.WeightUnit
import app.liftlog.domain.formatSet
import app.liftlog.domain.formatWeight
import app.liftlog.ui.formatDuration
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    viewModel: ActiveWorkoutViewModel,
    onAddExercise: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restEndsAt by viewModel.restEndsAt.collectAsStateWithLifecycle()
    val now = currentTimeTicker()

    var menuOpen by remember { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    RequestNotificationPermission()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Workout")
                        state?.let {
                            Text(
                                formatDuration((now - it.workout.startedAt) / 1000),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(onClick = { confirmFinish = true }) { Text("Finish") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Discard workout") },
                                onClick = { menuOpen = false; confirmDiscard = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            val endsAt = restEndsAt
            if (endsAt != null && endsAt > now) {
                RestTimerBar(
                    secondsLeft = (endsAt - now + 999) / 1000,
                    onAddTime = { viewModel.addRestTime(30) },
                    onSkip = viewModel::skipRest,
                )
            }
        },
    ) { padding ->
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (current.exercises.isEmpty()) {
                item {
                    Text(
                        "Add your first exercise to get started.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(current.exercises, key = { it.workoutExercise.id }) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    previousSets = current.previousSets[exercise.workoutExercise.id].orEmpty(),
                    unit = current.unit,
                    onWeightTyped = { setId, text -> viewModel.onWeightTyped(setId, text, current.unit) },
                    onRepsTyped = viewModel::onRepsTyped,
                    onToggle = { set, previous -> viewModel.toggleCompleted(set, previous, current.restSeconds) },
                    onDeleteSet = viewModel::deleteSet,
                    onAddSet = { viewModel.addSet(exercise) },
                    onRemoveExercise = { viewModel.removeExercise(exercise.workoutExercise.id) },
                )
            }
            item {
                OutlinedButton(onClick = onAddExercise, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add exercise", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (confirmFinish) {
        val doneSets = state?.exercises?.sumOf { exercise -> exercise.sets.count { it.completed } } ?: 0
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish workout?") },
            text = {
                Text(
                    if (doneSets == 0) {
                        "No sets are ticked, so nothing will be saved."
                    } else {
                        "$doneSets completed sets will be saved. Sets you haven't ticked are dropped."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmFinish = false; viewModel.finish { onClose() } }) { Text("Finish") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going") } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard workout?") },
            text = { Text("Everything logged in this workout will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; viewModel.discard(onClose) }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: WorkoutExerciseWithSets,
    previousSets: List<SetEntry>,
    unit: WeightUnit,
    onWeightTyped: (Long, String) -> Unit,
    onRepsTyped: (Long, String) -> Unit,
    onToggle: (SetEntry, SetEntry?) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onAddSet: () -> Unit,
    onRemoveExercise: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Id of the set whose input should take focus, set when the set before it is ticked.
    var focusSetId by remember { mutableStateOf<Long?>(null) }
    val focusManager = LocalFocusManager.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exercise.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Exercise options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Remove exercise") },
                            onClick = { menuOpen = false; onRemoveExercise() },
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                HeaderCell("Set", Modifier.width(SET_COLUMN))
                HeaderCell("Last time", Modifier.weight(1f))
                HeaderCell(unit.label, Modifier.width(INPUT_COLUMN))
                HeaderCell("Reps", Modifier.width(INPUT_COLUMN))
                HeaderCell("", Modifier.width(ACTION_COLUMNS))
            }

            exercise.sets.forEachIndexed { index, set ->
                val previous = previousSets.getOrNull(index)
                SetRow(
                    number = index + 1,
                    set = set,
                    previous = previous,
                    unit = unit,
                    onWeightTyped = { onWeightTyped(set.id, it) },
                    onRepsTyped = { onRepsTyped(set.id, it) },
                    requestFocus = focusSetId == set.id,
                    onFocusTaken = { focusSetId = null },
                    onToggle = {
                        // Only a set with reps (typed, or copied from last time) actually gets ticked.
                        val willComplete = !set.completed && (set.reps ?: previous?.reps) != null
                        if (willComplete) {
                            val next = exercise.sets.drop(index + 1).firstOrNull { !it.completed }
                            if (next != null) focusSetId = next.id else focusManager.clearFocus()
                        }
                        onToggle(set, previous)
                    },
                    onDelete = { onDeleteSet(set.id) },
                )
            }

            TextButton(onClick = onAddSet) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add set", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun SetRow(
    number: Int,
    set: SetEntry,
    previous: SetEntry?,
    unit: WeightUnit,
    onWeightTyped: (String) -> Unit,
    onRepsTyped: (String) -> Unit,
    requestFocus: Boolean,
    onFocusTaken: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val weightFocus = remember { FocusRequester() }
    val repsFocus = remember { FocusRequester() }

    // The text fields own what the user is typing. They re-read the database only when the
    // set is ticked or unticked (ticking may fill in last time's numbers) or the unit changes.
    var weightText by remember(set.id, set.completed, unit) {
        mutableStateOf(set.weightKg?.let { formatWeight(it, unit) } ?: "")
    }
    var repsText by remember(set.id, set.completed) {
        mutableStateOf(set.reps?.toString() ?: "")
    }

    // Jump to the first thing still to fill in: the weight, or the reps if weight is carried over.
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            (if (weightText.isBlank()) weightFocus else repsFocus).requestFocus()
            onFocusTaken()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            number.toString(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(SET_COLUMN),
        )
        Text(
            previous?.let { formatSet(it.weightKg, it.reps, unit) } ?: "–",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = weightText,
            onValueChange = { weightText = it; onWeightTyped(it) },
            placeholder = previous?.weightKg?.let { formatWeight(it, unit) },
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
            maxLength = 7,
            focusRequester = weightFocus,
        )
        NumberField(
            value = repsText,
            onValueChange = { repsText = it; onRepsTyped(it) },
            placeholder = previous?.reps?.toString(),
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            maxLength = 4,
            focusRequester = repsFocus,
        )
        FilledIconToggleButton(checked = set.completed, onCheckedChange = { onToggle() }) {
            Icon(Icons.Default.Check, contentDescription = if (set.completed) "Mark set not done" else "Mark set done")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete set",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    maxLength: Int,
    focusRequester: FocusRequester,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            val allowed = text.filter { it.isDigit() || (keyboardType == KeyboardType.Decimal && (it == '.' || it == ',')) }
            if (allowed.length <= maxLength) onValueChange(allowed)
        },
        placeholder = {
            if (placeholder != null) {
                Text(placeholder, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        modifier = Modifier.width(INPUT_COLUMN).padding(horizontal = 2.dp).focusRequester(focusRequester),
    )
}

@Composable
private fun RestTimerBar(secondsLeft: Long, onAddTime: () -> Unit, onSkip: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 3.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Rest", style = MaterialTheme.typography.labelMedium)
                Text(formatDuration(secondsLeft), style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(onClick = onAddTime) { Text("+30 s") }
            TextButton(onClick = onSkip) { Text("Skip") }
        }
    }
}

/** Current time, refreshed a few times a second while on screen, for the clocks. */
@Composable
private fun currentTimeTicker(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    return now
}

/** Asks once per visit for permission to show the "rest over" alert (Android 13+). */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private val SET_COLUMN = 32.dp
private val INPUT_COLUMN = 76.dp
private val ACTION_COLUMNS = 96.dp
