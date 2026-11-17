package app.liftlog.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.liftlog.data.Muscle
import app.liftlog.domain.formatWeight
import app.liftlog.domain.WeightUnit
import app.liftlog.ui.components.MuscleHeatPair
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Sets per muscle for one Monday-to-Sunday week, largest first. */
data class MuscleWeekState(
    val weekStart: LocalDate,
    val isCurrentWeek: Boolean = true,
    val totals: List<Pair<Muscle, Double>> = emptyList(),
)

private val dayMonth = DateTimeFormatter.ofPattern("d MMM")

/**
 * How much each muscle was trained in a week: a body shaded by sets, then a bar per muscle.
 * The arrows step back and forward a week.
 */
@Composable
fun MuscleVolumeCard(state: MuscleWeekState, onPreviousWeek: () -> Unit, onNextWeek: () -> Unit) {
    val highest = state.totals.maxOfOrNull { it.second } ?: 0.0
    // Bars and shading share a scale: the upper guide figure, or the busiest muscle if higher.
    val scale = maxOf(highest, GUIDE_HIGH)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sets per muscle", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.isCurrentWeek) {
                            "This week, from ${state.weekStart.format(dayMonth)}"
                        } else {
                            "${state.weekStart.format(dayMonth)} to ${state.weekStart.plusDays(6).format(dayMonth)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPreviousWeek) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
                }
                IconButton(onClick = onNextWeek, enabled = !state.isCurrentWeek) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
                }
            }

            if (state.totals.isEmpty()) {
                Text(
                    "No sets logged in this week.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                return@Column
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                MuscleHeatPair(
                    intensity = state.totals.associate { (muscle, sets) -> muscle to (sets / scale).toFloat() },
                    description = "Sets per muscle: " + state.totals.joinToString { (muscle, sets) -> "${muscle.label} ${number(sets)}" },
                    modifier = Modifier.height(150.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 8.dp)) {
                state.totals.forEach { (muscle, sets) -> MuscleBar(muscle.label, sets, scale) }
            }

            Text(
                "A set counts once for its main muscle and half for each secondary muscle; warm-ups are " +
                    "left out. Many programmes aim for roughly 10 to 20 sets per muscle a week; " +
                    "treat that as a rough guide, not a rule.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, end = 8.dp),
            )
        }
    }
}

@Composable
private fun MuscleBar(label: String, sets: Double, scale: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((sets / scale).toFloat().coerceIn(0.02f, 1f))
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
            )
        }
        Text(
            number(sets),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

/** Sets as a plain number: "12" or "7.5". */
private fun number(value: Double): String = formatWeight(value, WeightUnit.KG)

private const val GUIDE_HIGH = 20.0
