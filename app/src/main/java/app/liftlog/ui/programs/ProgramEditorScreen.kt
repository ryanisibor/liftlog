package app.liftlog.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.lifecycle.viewmodel.compose.viewModel
import app.liftlog.data.db.DayWithExercises
import app.liftlog.data.db.Program
import app.liftlog.data.db.ProgramDao
import app.liftlog.data.db.ProgramDay
import app.liftlog.ui.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProgramEditorViewModel(private val programId: Long, private val programDao: ProgramDao) : ViewModel() {
    val program: StateFlow<Program?> =
        programDao.observeProgram(programId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val days: StateFlow<List<DayWithExercises>> =
        programDao.observeDays(programId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(name: String) {
        viewModelScope.launch { programDao.renameProgram(programId, name) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            programDao.deleteProgram(programId)
            onDone()
        }
    }

    fun addDay(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val position = programDao.maxDayPosition(programId) + 1
            onCreated(programDao.insertDay(ProgramDay(programId = programId, position = position, name = name)))
        }
    }

    fun swap(first: ProgramDay, second: ProgramDay) {
        viewModelScope.launch { programDao.swapDays(first, second) }
    }
}

/** A program's training days, in the order they rotate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramEditorScreen(programId: Long, onOpenDay: (Long) -> Unit, onBack: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel(key = "program-$programId") { ProgramEditorViewModel(programId, container.programDao) }
    val program by viewModel.program.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()

    var showRename by rememberSaveable { mutableStateOf(false) }
    var showAddDay by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(program?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename program")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete program")
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
            if (days.isEmpty()) {
                item {
                    Text(
                        "Add a day for each workout in your week, for example Upper Body A, Lower Body A.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(days, key = { _, day -> day.day.id }) { index, day ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenDay(day.day.id) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Day ${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(day.day.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${day.items.size} exercises · ${day.totalSets} sets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MoveButtons(
                            canMoveUp = index > 0,
                            canMoveDown = index < days.lastIndex,
                            onMoveUp = { viewModel.swap(day.day, days[index - 1].day) },
                            onMoveDown = { viewModel.swap(day.day, days[index + 1].day) },
                        )
                    }
                }
            }
            item {
                FilledTonalButton(onClick = { showAddDay = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add day", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showRename) {
        NameDialog(
            title = "Rename program",
            initial = program?.name ?: "",
            onDismiss = { showRename = false },
            onConfirm = { viewModel.rename(it); showRename = false },
        )
    }
    if (showAddDay) {
        NameDialog(
            title = "New day",
            placeholder = "e.g. Upper Body A",
            confirmLabel = "Add",
            onDismiss = { showAddDay = false },
            onConfirm = { name -> showAddDay = false; viewModel.addDay(name, onOpenDay) },
        )
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Delete this program?",
            text = "Its days and targets will be deleted. Workouts you've already logged stay in your history.",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
        )
    }
}
