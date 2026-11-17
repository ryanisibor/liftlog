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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.liftlog.data.db.Program
import app.liftlog.data.db.ProgramDao
import app.liftlog.ui.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProgramsViewModel(private val programDao: ProgramDao) : ViewModel() {
    val programs: StateFlow<List<Program>> =
        programDao.observePrograms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Creates a program; the first one becomes the active program automatically. */
    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val isFirst = programDao.programCount() == 0
            val id = programDao.insertProgram(Program(name = name))
            if (isFirst) programDao.setActiveProgram(id)
            onCreated(id)
        }
    }

    fun setActive(id: Long) {
        viewModelScope.launch { programDao.setActiveProgram(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(onOpenProgram: (Long) -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { ProgramsViewModel(container.programDao) }
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Programs") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create program")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (programs.isEmpty()) {
                item {
                    Text(
                        "A program is your training week: a set of days, each with its exercises, " +
                            "sets and rep ranges. Tap + to create one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(programs, key = { it.id }) { program ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenProgram(program.id) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(program.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (program.isActive) "Active: shown on Home" else "Tap to edit",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (program.isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        if (!program.isActive) {
                            OutlinedButton(onClick = { viewModel.setActive(program.id) }) { Text("Make active") }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "New program",
            placeholder = "e.g. Upper / Lower",
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                viewModel.create(name, onOpenProgram)
            },
        )
    }
}
