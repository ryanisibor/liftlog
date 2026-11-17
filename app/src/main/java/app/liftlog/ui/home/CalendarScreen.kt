package app.liftlog.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.liftlog.domain.MonthStats
import app.liftlog.domain.WeightUnit
import app.liftlog.ui.countOf
import app.liftlog.ui.appContainer
import app.liftlog.ui.components.MuscleDiagramPair
import app.liftlog.ui.formatDate
import app.liftlog.ui.formatDurationCoarse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")
private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Training history as a month calendar, with the month's totals and its workouts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onOpenWorkout: (Long) -> Unit, onBack: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { CalendarViewModel(container.workoutDao, container.settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Tapping a day narrows the list to it; tapping it again, or changing month, clears that.
    var selected by remember(state.month) { mutableStateOf<LocalDate?>(null) }
    val shown = state.entries.filter { selected == null || it.date == selected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.month.format(monthTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                    IconButton(onClick = viewModel::nextMonth, enabled = state.canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MonthGrid(
                    state = state,
                    selected = selected,
                    onSelect = { day -> selected = if (selected == day) null else day },
                )
            }
            item { StatTiles(state.stats, state.unit) }
            item {
                Text(
                    selected?.let { formatDate(it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) }
                        ?: "Workouts this month",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.loaded && shown.isEmpty()) {
                item {
                    Text(
                        if (selected == null) "No workouts logged in this month." else "No workout on this day.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shown, key = { it.workout.id }) { entry ->
                WorkoutCard(entry, state.unit, onClick = { onOpenWorkout(entry.workout.id) })
            }
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarState, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val first = state.month.atDay(1)
    val leadingBlanks = first.dayOfWeek.value - 1
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..state.month.lengthOfMonth()).map { state.month.atDay(it) }
    val trained = state.trainedDays

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            weekdays.forEach { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { day -> DayCell(day, state.today, day != null && day in trained, day == selected, onSelect, Modifier.weight(1f)) }
                // Pad the final week so its days keep their columns.
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate?,
    today: LocalDate,
    trained: Boolean,
    selected: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    if (day == null) {
        Box(modifier)
        return
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (day == today) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = modifier.padding(2.dp).clickable { onSelect(day) },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (day > today) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(7.dp)
                    .background(if (trained) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
            )
        }
    }
}

@Composable
private fun StatTiles(stats: MonthStats, unit: WeightUnit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            value = countOf(stats.workouts, "workout"),
            detail = "%.1f per week".format(stats.perWeek),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = formatDurationCoarse(stats.totalSeconds),
            detail = "avg ${formatDurationCoarse(stats.averageSeconds)}",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = "${formatVolume(stats.totalVolumeKg, unit)} ${unit.label}",
            detail = "avg ${formatVolume(stats.averageVolumeKg, unit)} ${unit.label}",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(value: String, detail: String, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkoutCard(entry: CalendarEntry, unit: WeightUnit, onClick: () -> Unit) {
    val workout = entry.workout
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(workout.dayName ?: "Workout", style = MaterialTheme.typography.titleMedium)
                Text(
                    formatDate(workout.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${formatDurationCoarse((workout.finishedAt - workout.startedAt) / 1000)} · " +
                        "${countOf(workout.setCount, "set")} · ${formatVolume(workout.volumeKg, unit)} ${unit.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.primaryMuscles.isNotEmpty()) {
                MuscleDiagramPair(entry.primaryMuscles, entry.secondaryMuscles, Modifier.height(88.dp))
            }
        }
    }
}

/** Volume as a whole number in the display unit with thousands separators, e.g. "12,438". */
private fun formatVolume(volumeKg: Double, unit: WeightUnit): String =
    "%,d".format((volumeKg / unit.kgPerUnit).roundToLong())
