package app.liftlog.ui.programs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.liftlog.data.SettingsRepository
import app.liftlog.data.db.DayExerciseWithExercise
import app.liftlog.data.db.DayWithExercises
import app.liftlog.data.db.ProgramDao
import app.liftlog.data.db.ProgramDayExercise
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DayEditorViewModel(
    private val dayId: Long,
    private val programDao: ProgramDao,
    private val settings: SettingsRepository,
) : ViewModel() {
    val day: StateFlow<DayWithExercises?> =
        programDao.observeDay(dayId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) {
        viewModelScope.launch { programDao.renameDay(dayId, name) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            programDao.deleteDay(dayId)
            onDone()
        }
    }

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch {
            programDao.insertItem(
                ProgramDayExercise(
                    programDayId = dayId,
                    exerciseId = exerciseId,
                    position = programDao.maxItemPosition(dayId) + 1,
                    targetSets = DEFAULT_SETS,
                    repMin = DEFAULT_REP_MIN,
                    repMax = DEFAULT_REP_MAX,
                    restSeconds = settings.restSeconds.first(),
                ),
            )
        }
    }

    /** Keeps the rep range valid: raising the minimum past the maximum drags the maximum up, and vice versa. */
    fun setTargets(item: ProgramDayExercise, sets: Int = item.targetSets, repMin: Int = item.repMin, repMax: Int = item.repMax) {
        val min = repMin.coerceIn(REP_RANGE)
        val max = repMax.coerceIn(REP_RANGE)
        val fixedMin = if (repMax != item.repMax) minOf(min, max) else min
        val fixedMax = if (repMin != item.repMin) maxOf(min, max) else max
        viewModelScope.launch { programDao.setItemTargets(item.id, sets.coerceIn(SET_RANGE), fixedMin, fixedMax) }
    }

    fun swap(first: ProgramDayExercise, second: ProgramDayExercise) {
        viewModelScope.launch { programDao.swapItems(first, second) }
    }

    fun remove(itemId: Long) {
        viewModelScope.launch { programDao.deleteItem(itemId) }
    }

    companion object {
        val SET_RANGE = 1..12
        val REP_RANGE = 1..50
        private const val DEFAULT_SETS = 3
        private const val DEFAULT_REP_MIN = 8
        private const val DEFAULT_REP_MAX = 12
    }
}

/** One training day: its exercises in order, each with target sets and a rep range. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditorScreen(viewModel: DayEditorViewModel, onAddExercise: () -> Unit, onBack: () -> Unit) {
    val day by viewModel.day.collectAsStateWithLifecycle()
    val items = day?.items.orEmpty()

    var showRename by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(day?.day?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename day")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete day")
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
            if (items.isEmpty()) {
                item {
                    Text(
                        "Add the exercises for this day, in the order you do them.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(items, key = { _, item -> item.item.id }) { index, item ->
                DayExerciseCard(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < items.lastIndex,
                    onMoveUp = { viewModel.swap(item.item, items[index - 1].item) },
                    onMoveDown = { viewModel.swap(item.item, items[index + 1].item) },
                    onSets = { viewModel.setTargets(item.item, sets = it) },
                    onRepMin = { viewModel.setTargets(item.item, repMin = it) },
                    onRepMax = { viewModel.setTargets(item.item, repMax = it) },
                    onRemove = { viewModel.remove(item.item.id) },
                )
            }
            item {
                FilledTonalButton(onClick = onAddExercise, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add exercise", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showRename) {
        NameDialog(
            title = "Rename day",
            initial = day?.day?.name ?: "",
            onDismiss = { showRename = false },
            onConfirm = { viewModel.rename(it); showRename = false },
        )
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Delete this day?",
            text = "The day and its targets will be removed from the program. Logged workouts stay in your history.",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
        )
    }
}

@Composable
private fun DayExerciseCard(
    item: DayExerciseWithExercise,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSets: (Int) -> Unit,
    onRepMin: (Int) -> Unit,
    onRepMax: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${item.exercise.equipment.label} · ${item.exercise.primaryMuscle.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MoveButtons(canMoveUp, canMoveDown, onMoveUp, onMoveDown)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove exercise from day")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            ) {
                Stepper("sets", item.item.targetSets, DayEditorViewModel.SET_RANGE, onSets)
                Stepper("min reps", item.item.repMin, DayEditorViewModel.REP_RANGE, onRepMin)
                Stepper("max reps", item.item.repMax, DayEditorViewModel.REP_RANGE, onRepMax)
            }
        }
    }
}
