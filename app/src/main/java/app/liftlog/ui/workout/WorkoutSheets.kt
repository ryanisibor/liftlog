package app.liftlog.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftlog.data.db.Exercise
import app.liftlog.domain.WeightUnit
import app.liftlog.ui.appContainer
import app.liftlog.ui.exercises.ExerciseSessionCard
import app.liftlog.ui.exercises.groupIntoSessions
import app.liftlog.ui.formatDuration
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutOptionsSheet(
    onDismiss: () -> Unit,
    onExit: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Workout options",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        OptionRow(Icons.AutoMirrored.Filled.ArrowBack, "Exit workout", "Keeps it in progress so you can resume", onExit)
        OptionRow(Icons.Default.Check, "Finish workout", "Saves your ticked sets to history", onFinish)
        OptionRow(Icons.Default.Delete, "Discard workout", "Deletes everything logged in this workout", onDiscard, destructive = true)
        Box(Modifier.height(16.dp))
    }
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, destructive: Boolean = false) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        headlineContent = { Text(title, color = tint) },
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Past sessions of one exercise, shown over the workout without leaving it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseHistorySheet(exercise: Exercise, unit: WeightUnit, onDismiss: () -> Unit) {
    val workoutDao = appContainer().workoutDao
    val rows by remember(exercise.id) { workoutDao.observeExerciseHistory(exercise.id) }
        .collectAsStateWithLifecycle(initialValue = null)
    val sessions = remember(rows) { rows?.let(::groupIntoSessions) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                Text("History", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sessions != null && sessions.isEmpty()) {
                item {
                    Text(
                        "No finished workouts include this exercise yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(sessions.orEmpty(), key = { it.workoutId }) { session ->
                ExerciseSessionCard(session, unit)
            }
        }
    }
}

@Composable
internal fun NotesDialog(exerciseName: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note for $exerciseName") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 500) text = it },
                placeholder = { Text("Seat height, grip, cues. Shown every time you do this exercise.") },
                minLines = 3,
                maxLines = 6,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun RestTimerBar(secondsLeft: Long, onAddTime: () -> Unit, onSkip: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
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
internal fun currentTimeTicker(): Long {
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
internal fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
