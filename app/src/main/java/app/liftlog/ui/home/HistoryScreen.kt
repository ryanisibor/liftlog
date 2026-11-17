package app.liftlog.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.liftlog.ui.appContainer
import app.liftlog.ui.formatDateTime
import app.liftlog.ui.formatDurationCoarse

/** Every finished workout, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenWorkout: (Long) -> Unit, onBack: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { HomeViewModel(container.workoutDao, container.programDao, container.settings) }
    val history by viewModel.history.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout history") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            if (history.isEmpty()) {
                item {
                    Text(
                        "No workouts yet. Finished workouts appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(history, key = { it.id }) { workout ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenWorkout(workout.id) }) {
                    ListItem(
                        headlineContent = { Text(formatDateTime(workout.startedAt)) },
                        supportingContent = {
                            val duration = formatDurationCoarse((workout.finishedAt - workout.startedAt) / 1000)
                            Text("${workout.exerciseCount} exercises · ${workout.setCount} sets · $duration")
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}
