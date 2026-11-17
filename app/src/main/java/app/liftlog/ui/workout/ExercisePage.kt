package app.liftlog.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.liftlog.data.db.ProgramDayExercise
import app.liftlog.data.db.SetEntry
import app.liftlog.data.db.WorkoutExerciseWithSets
import app.liftlog.domain.WeightUnit
import app.liftlog.domain.formatSet
import app.liftlog.domain.formatWeight
import app.liftlog.ui.components.MuscleDiagramPair

/** Everything the page for one exercise can ask the screen to do. */
internal class ExercisePageActions(
    val onWeightTyped: (setId: Long, text: String) -> Unit,
    val onRepsTyped: (setId: Long, text: String) -> Unit,
    val onToggle: (set: SetEntry, previous: SetEntry?) -> Unit,
    val onToggleWarmup: (set: SetEntry) -> Unit,
    val onDeleteSet: (setId: Long) -> Unit,
    val onAddSet: () -> Unit,
    val onRemoveLastSet: () -> Unit,
    val onHistory: () -> Unit,
    val onSwap: () -> Unit,
    val onNotes: () -> Unit,
    val onRemove: () -> Unit,
    /** Null when the exercise is already first or last. */
    val onMoveEarlier: (() -> Unit)?,
    val onMoveLater: (() -> Unit)?,
)

/** One exercise of the workout: muscle diagram, name, actions and the set table. */
@Composable
internal fun ExercisePage(
    exercise: WorkoutExerciseWithSets,
    previousSets: List<SetEntry>,
    unit: WeightUnit,
    target: ProgramDayExercise?,
    actions: ExercisePageActions,
) {
    val notes = exercise.exercise.notes

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(bottom = 16.dp),
    ) {
        // Name on the left, muscle map on the right: keeps the header short so the set
        // table, where the typing happens, gets most of the screen.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                exercise.exercise.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${exercise.exercise.equipment.label} · ${exercise.exercise.primaryMuscle.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (target != null) {
                val reps = if (target.repMin == target.repMax) "${target.repMin}" else "${target.repMin}–${target.repMax}"
                Text(
                    "Target: ${target.targetSets} sets of $reps reps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (notes.isNotEmpty()) {
                Text(
                    notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp).clickable(onClick = actions.onNotes),
                )
            }
        }
            MuscleDiagramPair(
                exercise.exercise.primaryMuscle,
                exercise.exercise.secondaryMuscles,
                Modifier.height(DIAGRAM_HEIGHT),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ActionChip("History", Icons.Default.DateRange, actions.onHistory)
            ActionChip("Swap", Icons.Default.Refresh, actions.onSwap)
            ActionChip(if (notes.isEmpty()) "Add note" else "Edit note", Icons.Default.Edit, actions.onNotes)
            actions.onMoveEarlier?.let { ActionChip("Move earlier", Icons.AutoMirrored.Filled.KeyboardArrowLeft, it) }
            actions.onMoveLater?.let { ActionChip("Move later", Icons.AutoMirrored.Filled.KeyboardArrowRight, it) }
            ActionChip("Remove", Icons.Default.Delete, actions.onRemove, destructive = true)
        }

        SetTable(exercise, previousSets, unit, actions)

        Text(
            "Tap a set number to mark it as a warm-up · swipe a set left to delete it",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun SetTable(
    exercise: WorkoutExerciseWithSets,
    previousSets: List<SetEntry>,
    unit: WeightUnit,
    actions: ExercisePageActions,
) {
    // Id of the set whose input should take focus, set when the set before it is ticked.
    var focusSetId by remember { mutableStateOf<Long?>(null) }
    val focusManager = LocalFocusManager.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                HeaderCell("Set", Modifier.width(SET_COLUMN))
                HeaderCell("Last time", Modifier.weight(1f))
                HeaderCell(unit.label, Modifier.width(INPUT_COLUMN))
                HeaderCell("Reps", Modifier.width(INPUT_COLUMN))
                HeaderCell("Done", Modifier.width(TICK_COLUMN))
            }

            exercise.sets.forEachIndexed { index, set ->
                val previous = previousSets.getOrNull(index)
                key(set.id) {
                    SetRow(
                        // Warm-ups show as W; working sets are numbered among themselves.
                        label = if (set.isWarmup) "W" else exercise.sets.take(index + 1).count { !it.isWarmup }.toString(),
                        onToggleWarmup = { actions.onToggleWarmup(set) },
                        set = set,
                        previous = previous,
                        unit = unit,
                        onWeightTyped = { actions.onWeightTyped(set.id, it) },
                        onRepsTyped = { actions.onRepsTyped(set.id, it) },
                        requestFocus = focusSetId == set.id,
                        onFocusTaken = { focusSetId = null },
                        onToggle = {
                            // Only a set with reps (typed, or copied from last time) actually gets ticked.
                            val willComplete = !set.completed && (set.reps ?: previous?.reps) != null
                            if (willComplete) {
                                val next = exercise.sets.drop(index + 1).firstOrNull { !it.completed }
                                if (next != null) focusSetId = next.id else focusManager.clearFocus()
                            }
                            actions.onToggle(set, previous)
                        },
                        onDelete = { actions.onDeleteSet(set.id) },
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            ) {
                val lastIsOpen = exercise.sets.lastOrNull()?.completed == false
                FilledTonalButton(onClick = actions.onRemoveLastSet, enabled = lastIsOpen, modifier = Modifier.weight(1f)) {
                    Text("− Set")
                }
                FilledTonalButton(onClick = actions.onAddSet, modifier = Modifier.weight(1f)) {
                    Text("+ Set")
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit, destructive: Boolean = false) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        colors = AssistChipDefaults.assistChipColors(labelColor = tint, leadingIconContentColor = tint),
    )
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
    label: String,
    onToggleWarmup: () -> Unit,
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

    // Swipe left to delete: a deliberate gesture, so a set can't be removed by a stray tap.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            value == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 16.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete set",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                color = if (set.isWarmup) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .width(SET_COLUMN)
                    .clickable(
                        onClickLabel = if (set.isWarmup) "Mark as working set" else "Mark as warm-up",
                        onClick = onToggleWarmup,
                    )
                    .padding(vertical = 14.dp),
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
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(TICK_COLUMN)) {
                FilledIconToggleButton(
                    checked = set.completed,
                    onCheckedChange = { onToggle() },
                    // A visible circle when unticked, so it reads as a button on the card.
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = if (set.completed) "Mark set not done" else "Mark set done",
                    )
                }
            }
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

private val SET_COLUMN = 36.dp
private val INPUT_COLUMN = 84.dp
private val TICK_COLUMN = 56.dp
private val DIAGRAM_HEIGHT = 112.dp
