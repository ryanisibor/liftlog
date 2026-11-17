package app.liftlog.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liftlog.domain.RecordKind
import app.liftlog.domain.formatWeight
import app.liftlog.ui.countOf
import app.liftlog.ui.formatDurationCoarse
import kotlin.math.roundToLong

/** Shown once a workout is finished: what was done and any personal records broken. */
@Composable
internal fun FinishSummaryScreen(summary: FinishSummary, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val unit = summary.unit

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Column {
                Text("Workout complete", style = MaterialTheme.typography.headlineMedium)
                Text(
                    summary.dayName ?: "Workout",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(formatDurationCoarse(summary.durationSeconds), "duration", Modifier.weight(1f))
                Tile(summary.setCount.toString(), if (summary.setCount == 1) "set" else "sets", Modifier.weight(1f))
                Tile(
                    "%,d".format((summary.volumeKg / unit.kgPerUnit).roundToLong()),
                    "${unit.label} lifted",
                    Modifier.weight(1f),
                )
            }
            Text(
                "${countOf(summary.exerciseCount, "exercise")}. Sets and weight lifted leave out warm-ups.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (summary.records.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (summary.records.size == 1) "1 personal record" else "${summary.records.size} personal records",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        summary.records.forEach { record ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column {
                                    Text(record.exercise, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${formatWeight(record.weightKg, unit)} ${unit.label} × ${record.reps} · ${describe(record.kinds)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Done", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun describe(kinds: Set<RecordKind>): String = when {
    RecordKind.WEIGHT in kinds && RecordKind.ONE_REP_MAX in kinds -> "heaviest weight and best estimated 1RM"
    RecordKind.WEIGHT in kinds -> "heaviest weight"
    else -> "best estimated 1RM"
}
