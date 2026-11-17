package app.liftlog.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftlog.data.SettingsRepository
import app.liftlog.data.db.DayWithExercises
import app.liftlog.data.db.Program
import app.liftlog.data.db.ProgramDao
import app.liftlog.data.db.Workout
import app.liftlog.data.db.WorkoutDao
import app.liftlog.domain.WeightUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The active program as the Home screen shows it. */
data class PlanState(
    val program: Program,
    val days: List<DayWithExercises>,
    /** Index into [days] of the workout that is due next in the rotation. */
    val nextDayIndex: Int,
    /** Days already trained since Monday. */
    val trainedThisWeek: Set<Long>,
    /** Heaviest weight used last time, by exercise id. */
    val lastWeights: Map<Long, Double>,
)

data class HomeState(
    val loaded: Boolean = false,
    val plan: PlanState? = null,
    val activeWorkout: Workout? = null,
    val unit: WeightUnit = WeightUnit.KG,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val workoutDao: WorkoutDao,
    private val programDao: ProgramDao,
    settings: SettingsRepository,
) : ViewModel() {

    private val weekStart: Long = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private val plan: Flow<PlanState?> = programDao.observeActiveProgram().flatMapLatest { program ->
        if (program == null) {
            flowOf(null)
        } else {
            combine(
                programDao.observeDays(program.id),
                programDao.observeLastTrainedDayId(program.id),
                programDao.observeDayIdsTrainedSince(weekStart),
                programDao.observeLastWeights(),
            ) { days, lastDayId, trained, weights ->
                PlanState(
                    program = program,
                    days = days,
                    nextDayIndex = nextDayIndex(days.map { it.day.id }, lastDayId),
                    trainedThisWeek = trained.toSet(),
                    lastWeights = weights.mapNotNull { w -> w.weightKg?.let { w.exerciseId to it } }.toMap(),
                )
            }
        }
    }

    val state: StateFlow<HomeState> = combine(
        plan,
        workoutDao.observeActiveWorkout(),
        settings.weightUnit,
    ) { plan, active, unit -> HomeState(loaded = true, plan = plan, activeWorkout = active, unit = unit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Opens the in-progress workout if there is one, otherwise starts an empty one. */
    fun startEmptyOrResume(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = workoutDao.activeWorkoutId()
                ?: workoutDao.insertWorkout(Workout(startedAt = System.currentTimeMillis()))
            onReady(id)
        }
    }

    /** Opens the in-progress workout if there is one, otherwise starts this program day. */
    fun startDayOrResume(dayId: Long, onReady: (Long) -> Unit) {
        viewModelScope.launch { onReady(workoutDao.startWorkoutFromDay(dayId, System.currentTimeMillis())) }
    }
}

/**
 * The day after the one trained most recently, wrapping round to the first.
 * Falls back to the first day if nothing has been trained or that day no longer exists.
 */
fun nextDayIndex(dayIds: List<Long>, lastTrainedDayId: Long?): Int {
    if (dayIds.isEmpty()) return 0
    val last = dayIds.indexOf(lastTrainedDayId)
    return if (last < 0) 0 else (last + 1) % dayIds.size
}
