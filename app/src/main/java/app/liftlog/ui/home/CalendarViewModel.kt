package app.liftlog.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftlog.data.Muscle
import app.liftlog.data.SettingsRepository
import app.liftlog.data.db.CalendarWorkout
import app.liftlog.data.db.WorkoutDao
import app.liftlog.domain.MonthStats
import app.liftlog.domain.WeightUnit
import app.liftlog.domain.WorkoutTotals
import app.liftlog.domain.monthStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A finished workout with the muscles it trained, placed on its calendar day. */
data class CalendarEntry(
    val workout: CalendarWorkout,
    val date: LocalDate,
    val primaryMuscles: Set<Muscle>,
    val secondaryMuscles: Set<Muscle>,
)

data class CalendarState(
    val month: YearMonth,
    val today: LocalDate,
    val loaded: Boolean = false,
    val entries: List<CalendarEntry> = emptyList(),
    val stats: MonthStats = monthStats(emptyList(), 0),
    val unit: WeightUnit = WeightUnit.KG,
) {
    val trainedDays: Set<LocalDate> get() = entries.map { it.date }.toSet()
    val canGoForward: Boolean get() = month < YearMonth.from(today)
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val workoutDao: WorkoutDao, settings: SettingsRepository) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val month = MutableStateFlow(YearMonth.from(today))

    val state: StateFlow<CalendarState> = month.flatMapLatest { shown ->
        val from = shown.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val until = shown.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        combine(
            workoutDao.observeWorkoutsBetween(from, until),
            workoutDao.observeWorkoutMusclesBetween(from, until),
            settings.weightUnit,
        ) { workouts, muscles, unit ->
            val musclesByWorkout = muscles.groupBy { it.workoutId }
            val entries = workouts.map { workout ->
                val rows = musclesByWorkout[workout.id].orEmpty()
                val primary = rows.map { it.primaryMuscle }.toSet()
                CalendarEntry(
                    workout = workout,
                    date = Instant.ofEpochMilli(workout.startedAt).atZone(zone).toLocalDate(),
                    primaryMuscles = primary,
                    secondaryMuscles = rows.flatMap { it.secondaryMuscles }.toSet() - primary,
                )
            }
            CalendarState(
                month = shown,
                today = today,
                loaded = true,
                entries = entries,
                stats = monthStats(
                    workouts.map { WorkoutTotals((it.finishedAt - it.startedAt) / 1000, it.volumeKg) },
                    daysCounted(shown),
                ),
                unit = unit,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState(YearMonth.from(today), today))

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        if (month.value < YearMonth.from(today)) month.value = month.value.plusMonths(1)
    }

    /** Days of the month that have happened: all of a past month, up to today for this one. */
    private fun daysCounted(shown: YearMonth): Int = when {
        shown < YearMonth.from(today) -> shown.lengthOfMonth()
        shown == YearMonth.from(today) -> today.dayOfMonth
        else -> 0
    }
}
