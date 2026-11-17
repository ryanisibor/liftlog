package app.liftlog.ui.progress

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liftlog.domain.PersonalRecords
import app.liftlog.domain.SessionStats
import app.liftlog.domain.WeightUnit
import app.liftlog.domain.formatWeight
import app.liftlog.domain.personalRecords
import app.liftlog.ui.components.ChartPoint
import app.liftlog.ui.components.LineChart
import app.liftlog.ui.formatDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the chart can plot. Weight-based metrics are hidden for exercises logged without weight. */
enum class ProgressMetric(val label: String, val needsWeight: Boolean) {
    TOP_WEIGHT("Top weight", true),
    ONE_REP_MAX("Est. 1RM", true),
    VOLUME("Volume", true),
    REPS("Best reps", false),
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM")

private fun SessionStats.valueOf(metric: ProgressMetric, unit: WeightUnit): Double? = when (metric) {
    ProgressMetric.TOP_WEIGHT -> topWeightKg?.div(unit.kgPerUnit)
    ProgressMetric.ONE_REP_MAX -> bestOneRepMaxKg?.div(unit.kgPerUnit)
    ProgressMetric.VOLUME -> volumeKg.takeIf { it > 0 }?.div(unit.kgPerUnit)
    ProgressMetric.REPS -> topReps.takeIf { it > 0 }?.toDouble()
}

/** A chart of one metric over time and the exercise's personal records. [sessions] oldest first. */
@Composable
fun ExerciseProgressSection(sessions: List<SessionStats>, unit: WeightUnit) {
    if (sessions.isEmpty()) return
    val hasWeight = sessions.any { it.topWeightKg != null }
    val metrics = ProgressMetric.entries.filter { hasWeight || !it.needsWeight }
    var selected by rememberSaveable { mutableStateOf(ProgressMetric.TOP_WEIGHT) }
    val metric = if (selected in metrics) selected else metrics.first()
    val records = remember(sessions) { personalRecords(sessions) }

    val points = sessions.mapNotNull { session ->
        session.valueOf(metric, unit)?.let { value ->
            ChartPoint(Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).format(shortDate), value)
        }
    }
    val suffix = if (metric == ProgressMetric.REPS) "" else " ${unit.label}"
    // The values are already in the display unit, so format them as plain numbers.
    val format: (Double) -> String = { formatWeight(it, WeightUnit.KG) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (metrics.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                ) {
                    metrics.forEach { option ->
                        FilterChip(selected = option == metric, onClick = { selected = option }, label = { Text(option.label) })
                    }
                }
            }
            val latest = points.lastOrNull()
            if (latest != null) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("${format(latest.value)}$suffix", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        change(points, format, suffix),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LineChart(
                    points = points,
                    formatValue = format,
                    description = "${metric.label} over ${points.size} sessions, latest ${format(latest.value)}$suffix",
                    modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp),
                )
            } else {
                Text(
                    "Nothing to chart for this measure yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (metric == ProgressMetric.ONE_REP_MAX) {
                Text(
                    "Estimated from your sets of 12 reps or fewer (Epley formula). A guide, not a tested max.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    RecordsCard(records, unit)
}

/** "First session", or the difference between the newest point and the one before it. */
private fun change(points: List<ChartPoint>, format: (Double) -> String, suffix: String): String {
    if (points.size < 2) return "Latest session · log it again to see a trend"
    val delta = points.last().value - points[points.lastIndex - 1].value
    return when {
        delta > 0 -> "Latest session · up ${format(delta)}$suffix on the one before"
        delta < 0 -> "Latest session · down ${format(-delta)}$suffix on the one before"
        else -> "Latest session · same as the one before"
    }
}

@Composable
private fun RecordsCard(records: PersonalRecords, unit: WeightUnit) {
    val rows = buildList {
        records.heaviest?.let {
            val reps = it.reps?.let { reps -> " × $reps" } ?: ""
            add(Triple("Heaviest set", "${formatWeight(it.value, unit)} ${unit.label}$reps", it.achievedAt))
        }
        records.bestOneRepMax?.let {
            add(Triple("Best estimated 1RM", "${formatWeight(it.value, unit)} ${unit.label}", it.achievedAt))
        }
        records.bestVolume?.let {
            add(Triple("Most volume in a session", "${formatWeight(it.value, unit)} ${unit.label}", it.achievedAt))
        }
        records.mostReps?.let {
            add(Triple("Most reps in a set", "${it.value.toInt()}", it.achievedAt))
        }
    }
    if (rows.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Personal records", style = MaterialTheme.typography.titleMedium)
            rows.forEach { (label, value, achievedAt) ->
                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatDate(achievedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
