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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TextButton
import app.liftlog.data.InvalidProgramFileException
import app.liftlog.data.ProgramTransfer
import app.liftlog.data.db.ProgramDao
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException
import app.liftlog.ui.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProgramsViewModel(
    private val programDao: ProgramDao,
    private val transfer: ProgramTransfer,
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)

    /** Why the last import failed, if it did. */
    val message: StateFlow<String?> = _message

    /** Adds a program from a file exported on this or another phone, then opens it. */
    fun import(uri: Uri, onImported: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val id = transfer.importFrom(uri)
                _message.value = null
                onImported(id)
            } catch (e: InvalidProgramFileException) {
                _message.value = e.message
            } catch (_: IOException) {
                _message.value = "The file could not be read."
            } catch (_: SecurityException) {
                _message.value = "The file could not be read."
            }
        }
    }

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
    val viewModel = viewModel { ProgramsViewModel(container.programDao, container.programTransfer) }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.import(it, onOpenProgram) }
    }
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Programs") },
                actions = {
                    TextButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                    ) { Text("Import") }
                },
            )
        },
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
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (programs.isEmpty()) {
                item {
                    Text(
                        "A program is your training week: a set of days, each with its exercises, " +
                            "sets and rep ranges. Tap + to create one, or Import to load one shared from another phone.",
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
