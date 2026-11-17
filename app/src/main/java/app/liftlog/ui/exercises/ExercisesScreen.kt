package app.liftlog.ui.exercises

import android.database.sqlite.SQLiteConstraintException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.liftlog.data.Equipment
import app.liftlog.data.Muscle
import app.liftlog.data.db.Exercise
import app.liftlog.data.db.ExerciseDao
import app.liftlog.ui.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExercisesViewModel(private val exerciseDao: ExerciseDao) : ViewModel() {
    val exercises: StateFlow<List<Exercise>> =
        exerciseDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Calls [onResult] with null on success, or a message to show the user. */
    fun addCustom(name: String, muscle: Muscle, equipment: Equipment, onResult: (String?) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return onResult("Enter a name")
        if (trimmed.length > MAX_NAME_LENGTH) return onResult("Name is too long")
        viewModelScope.launch {
            try {
                exerciseDao.insert(
                    Exercise(name = trimmed, primaryMuscle = muscle, equipment = equipment, isCustom = true),
                )
                onResult(null)
            } catch (_: SQLiteConstraintException) {
                onResult("An exercise with that name already exists")
            }
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 60
    }
}

/**
 * The exercise library. Used both as a top-level tab and, with [onBack] set, as the picker
 * when adding an exercise to a workout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    title: String,
    onExerciseClick: (Exercise) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val container = appContainer()
    val viewModel = viewModel { ExercisesViewModel(container.exerciseDao) }
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<Muscle?>(null) }
    var equipment by rememberSaveable { mutableStateOf<Equipment?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val filtered = remember(exercises, query, muscle, equipment) {
        val q = query.trim()
        exercises.filter { exercise ->
            (q.isEmpty() || exercise.name.contains(q, ignoreCase = true)) &&
                (muscle == null || exercise.primaryMuscle == muscle || muscle in exercise.secondaryMuscles) &&
                (equipment == null || exercise.equipment == equipment)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add custom exercise")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search ${exercises.size} exercises") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            ChipRow(
                allLabel = "All muscles",
                options = Muscle.entries,
                selected = muscle,
                label = { it.label },
                onSelect = { muscle = it },
            )
            ChipRow(
                allLabel = "All equipment",
                options = Equipment.entries,
                selected = equipment,
                label = { it.label },
                onSelect = { equipment = it },
            )
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No exercises match. Use + to add your own.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(filtered, key = { it.id }) { exercise ->
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = {
                            val custom = if (exercise.isCustom) " · Custom" else ""
                            Text("${exercise.primaryMuscle.label} · ${exercise.equipment.label}$custom")
                        },
                        modifier = Modifier.clickable { onExerciseClick(exercise) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddExerciseDialog(
            initialName = query.trim(),
            onDismiss = { showAddDialog = false },
            onAdd = { name, newMuscle, newEquipment, onResult ->
                viewModel.addCustom(name, newMuscle, newEquipment) { error ->
                    if (error == null) showAddDialog = false
                    onResult(error)
                }
            },
        )
    }
}

@Composable
private fun <T> ChipRow(
    allLabel: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(allLabel) })
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(if (selected == option) null else option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun AddExerciseDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onAdd: (String, Muscle, Equipment, (String?) -> Unit) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var muscle by rememberSaveable { mutableStateOf(Muscle.CHEST) }
    var equipment by rememberSaveable { mutableStateOf(Equipment.BARBELL) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add custom exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                )
                EnumDropdown("Main muscle", Muscle.entries, muscle, { it.label }) { muscle = it }
                EnumDropdown("Equipment", Equipment.entries, equipment, { it.label }) { equipment = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, muscle, equipment) { error = it } }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun <T> EnumDropdown(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$title: ${label(selected)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
