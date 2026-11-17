package app.liftlog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.liftlog.data.SettingsRepository
import app.liftlog.domain.WeightUnit
import app.liftlog.ui.appContainer
import app.liftlog.ui.formatDuration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    val unit = settings.weightUnit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)
    val restSeconds = settings.restSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_REST_SECONDS)

    fun setUnit(unit: WeightUnit) {
        viewModelScope.launch { settings.setWeightUnit(unit) }
    }

    fun changeRest(deltaSeconds: Int) {
        viewModelScope.launch { settings.setRestSeconds(restSeconds.value + deltaSeconds) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val container = appContainer()
    val viewModel = viewModel { SettingsViewModel(container.settings) }
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val restSeconds by viewModel.restSeconds.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
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
            Text(
                "Changes how weights are shown and entered. Your history is not altered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "Rest timer",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledTonalButton(onClick = { viewModel.changeRest(-REST_STEP_SECONDS) }) { Text("−15 s") }
                Text(formatDuration(restSeconds.toLong()), style = MaterialTheme.typography.headlineSmall)
                FilledTonalButton(onClick = { viewModel.changeRest(REST_STEP_SECONDS) }) { Text("+15 s") }
            }
            Text(
                "Starts automatically each time you tick a set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val REST_STEP_SECONDS = 15
