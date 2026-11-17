package app.liftlog.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.liftlog.data.BackupRepository
import app.liftlog.data.InvalidBackupException
import app.liftlog.data.SettingsRepository
import app.liftlog.domain.WeightUnit
import app.liftlog.timer.RestTimer
import app.liftlog.ui.appContainer
import app.liftlog.ui.formatDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
    private val restTimer: RestTimer,
) : ViewModel() {
    val unit = settings.weightUnit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)
    val restSeconds = settings.restSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_REST_SECONDS)

    private val _backupMessage = MutableStateFlow<String?>(null)

    /** Outcome of the last export or import, for display. */
    val backupMessage: StateFlow<String?> = _backupMessage

    fun setUnit(unit: WeightUnit) {
        viewModelScope.launch { settings.setWeightUnit(unit) }
    }

    fun changeRest(deltaSeconds: Int) {
        viewModelScope.launch { settings.setRestSeconds(restSeconds.value + deltaSeconds) }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _backupMessage.value = try {
                "Backup saved: ${workouts(backup.exportTo(uri))}."
            } catch (_: IOException) {
                "The backup could not be saved. Try a different location."
            } catch (_: SecurityException) {
                "The backup could not be saved. Try a different location."
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _backupMessage.value = try {
                val count = backup.importFrom(uri)
                restTimer.cancel()
                "Backup restored: ${workouts(count)}."
            } catch (e: InvalidBackupException) {
                e.message
            } catch (_: IOException) {
                "The file could not be read. Nothing was changed."
            } catch (_: SecurityException) {
                "The file could not be read. Nothing was changed."
            }
        }
    }

    private fun workouts(count: Int) = if (count == 1) "1 workout" else "$count workouts"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val container = appContainer()
    val viewModel = viewModel { SettingsViewModel(container.settings, container.backup, container.restTimer) }
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val restSeconds by viewModel.restSeconds.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()

    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::export) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImport = uri }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Weight unit", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow {
                WeightUnit.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = unit == option,
                        onClick = { viewModel.setUnit(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, WeightUnit.entries.size),
                    ) {
                        Text(option.label)
                    }
                }
            }
            Hint("Changes how weights are shown and entered. Your history is not altered.")

            SectionTitle("Rest timer")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledTonalButton(onClick = { viewModel.changeRest(-REST_STEP_SECONDS) }) { Text("−15 s") }
                Text(formatDuration(restSeconds.toLong()), style = MaterialTheme.typography.headlineSmall)
                FilledTonalButton(onClick = { viewModel.changeRest(REST_STEP_SECONDS) }) { Text("+15 s") }
            }
            Hint("Starts automatically each time you tick a set.")

            SectionTitle("Backup")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { exportLauncher.launch("liftlog-backup-${LocalDate.now()}.json") }) {
                    Text("Export backup")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                    Text("Restore backup")
                }
            }
            Hint(
                "Your workouts exist only on this phone. Export saves everything to one file, which " +
                    "you can keep in Google Drive. Restore replaces everything in the app with a backup file.",
            )
            backupMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "Everything currently in the app, including any workout in progress, will be " +
                        "replaced by the contents of the backup. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingImport = null; viewModel.import(uri) }) { Text("Replace my data") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val REST_STEP_SECONDS = 15
