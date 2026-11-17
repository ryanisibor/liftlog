package app.liftlog.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import app.liftlog.data.db.TrainedExercise
import app.liftlog.data.db.WorkoutDao
import app.liftlog.domain.WeekCount
import app.liftlog.domain.WeightUnit
import app.liftlog.domain.formatWeight
import app.liftlog.domain.weeklyCounts
import app.liftlog.ui.appContainer
import app.liftlog.ui.components.BarChart
import app.liftlog.ui.components.ChartPoint
import app.liftlog.ui.formatDate
import app.liftlog.domain.MuscleSets
import app.liftlog.domain.setsPerMuscle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ProgressState(
    val loaded: Boolean = false,
    val weeks: List<WeekCount> = emptyList(),
    val totalWorkouts: Int = 0,
    val exercises: List<TrainedExercise> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(workoutDao: WorkoutDao, settings: SettingsRepository) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Weeks back from the current one for the sets-per-muscle card; 0 is this week. */
    private val weeksBack = MutableStateFlow(0)

    val muscleWeek: StateFlow<MuscleWeekState> = weeksBack.flatMapLatest { back ->
        val weekStart = thisWeek.minusWeeks(back.toLong())
        val from = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val until = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        workoutDao.observeMuscleSetRows(from, until).map { rows ->
            MuscleWeekState(
                weekStart = weekStart,
                isCurrentWeek = back == 0,
                totals = setsPerMuscle(rows.map { MuscleSets(it.primaryMuscle, it.secondaryMuscles, it.sets) }),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MuscleWeekState(thisWeek))

    fun previousWeek() {
        weeksBack.value += 1
    }

    fun nextWeek() {
        if (weeksBack.value > 0) weeksBack.value -= 1
    }

    private val since = today.minusWeeks(WEEKS_SHOWN.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    val state: StateFlow<ProgressState> = combine(
        workoutDao.observeFinishedStartTimes(since),
        workoutDao.observeFinishedWorkoutCount(),
        workoutDao.observeTrainedExercises(),
        settings.weightUnit,
    ) { startTimes, total, exercises, unit ->
        ProgressState(
            loaded = true,
            weeks = weeklyCounts(startTimes, WEEKS_SHOWN, today, zone),
            totalWorkouts = total,
            exercises = exercises,
            unit = unit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    private companion object {
        const val WEEKS_SHOWN = 8
    }
}

private val weekLabel = DateTimeFormatter.ofPattern("d/M")

/** Training consistency at the top, then every exercise you have logged; tap one for its charts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(onOpenExercise: (Long) -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { ProgressViewModel(container.workoutDao, container.settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val muscleWeek by viewModel.muscleWeek.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Progress") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.loaded) return@LazyColumn

            item {
                val thisWeek = state.weeks.lastOrNull()?.count ?: 0
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Workouts per week", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Last ${state.weeks.size} weeks, by week starting",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "$thisWeek this week · ${state.totalWorkouts} in total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BarChart(
                            points = state.weeks.map { ChartPoint(it.weekStart.format(weekLabel), it.count.toDouble()) },
                            description = "Workouts per week: " + state.weeks.joinToString { "${it.count}" },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                        )
                    }
                }
            }

            item { MuscleVolumeCard(muscleWeek, viewModel::previousWeek, viewModel::nextWeek) }

            item {
                Text(
                    "Exercises",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            if (state.exercises.isEmpty()) {
                item {
                    Text(
                        "Finish a workout and the exercises you logged will appear here, each with its own charts and records.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.exercises, key = { it.exerciseId }) { exercise ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenExercise(exercise.exerciseId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            val sessions = if (exercise.sessionCount == 1) "1 session" else "${exercise.sessionCount} sessions"
                            Text(
                                "$sessions · last ${formatDate(exercise.lastTrainedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        exercise.maxWeightKg?.takeIf { it > 0 }?.let { best ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${formatWeight(best, state.unit)} ${state.unit.label}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "heaviest",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
