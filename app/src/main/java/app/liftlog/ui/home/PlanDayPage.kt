package app.liftlog.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liftlog.data.Muscle
import app.liftlog.data.db.DayExerciseWithExercise
import app.liftlog.data.db.DayWithExercises
import app.liftlog.domain.WeightUnit
import app.liftlog.domain.formatWeight
import app.liftlog.ui.countOf
import app.liftlog.ui.components.MuscleDiagram
import app.liftlog.ui.components.MuscleDiagramPair
import app.liftlog.ui.components.bestViewFor

/** One training day of the plan: what it works, how big it is and its exercises. */
@Composable
internal fun PlanDayPage(
    day: DayWithExercises,
    isNext: Boolean,
    trainedThisWeek: Boolean,
    lastWeights: Map<Long, Double>,
    unit: WeightUnit,
) {
    val items = day.items
    val primary: Set<Muscle> = items.map { it.exercise.primaryMuscle }.toSet()
    val secondary: Set<Muscle> = items.flatMap { it.exercise.secondaryMuscles }.toSet() - primary

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxSize().padding(bottom = 4.dp),
    ) {
        LazyColumn(
            // Room at the bottom so the last exercise clears the Start button.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            isNext -> Badge("Next workout", highlighted = true)
                            trainedThisWeek -> Badge("Done this week", highlighted = false)
                        }
                        Text(day.day.name, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${countOf(items.size, "exercise")} · ${countOf(day.totalSets, "set")}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (items.isNotEmpty()) {
                        MuscleDiagramPair(primary, secondary, Modifier.height(124.dp))
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        "No exercises yet. Use the edit button at the top to add some.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items, key = { it.item.id }) { item ->
                PlanExerciseRow(item, lastWeights[item.exercise.id], unit)
            }
        }
    }
}

@Composable
private fun Badge(text: String, highlighted: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun PlanExerciseRow(item: DayExerciseWithExercise, lastWeightKg: Double?, unit: WeightUnit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(10.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(width = 48.dp, height = 60.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MuscleDiagram(
                        primary = item.exercise.primaryMuscle,
                        secondary = item.exercise.secondaryMuscles,
                        view = bestViewFor(item.exercise.primaryMuscle),
                        modifier = Modifier.height(50.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.exercise.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.exercise.equipment.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val reps = if (item.item.repMin == item.item.repMax) "${item.item.repMin}" else "${item.item.repMin}–${item.item.repMax}"
                val weight = lastWeightKg?.let { " · ${formatWeight(it, unit)} ${unit.label}" } ?: ""
                Text(
                    "${countOf(item.item.targetSets, "set")} · $reps reps$weight",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
