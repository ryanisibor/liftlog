package app.liftlog.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftlog.data.SettingsRepository
import app.liftlog.data.db.ExerciseDao
import app.liftlog.data.db.ProgramDayExercise
import app.liftlog.data.db.SetEntry
import app.liftlog.data.db.Workout
import app.liftlog.data.db.WorkoutDao
import app.liftlog.data.db.WorkoutExerciseWithSets
import app.liftlog.domain.WeightUnit
import app.liftlog.domain.parseReps
import app.liftlog.domain.parseWeightToKg
import app.liftlog.timer.RestTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutState(
    val workout: Workout,
    val exercises: List<WorkoutExerciseWithSets>,
    /** Last session's completed sets, keyed by workout-exercise id. */
    val previousSets: Map<Long, List<SetEntry>>,
    val unit: WeightUnit,
    val restSeconds: Int,
    /** Target sets and rep range from the program, keyed by exercise id; empty for ad-hoc workouts. */
    val targets: Map<Long, ProgramDayExercise> = emptyMap(),
) {
    /** True when this is a finished workout opened for correction, not one in progress. */
    val isEditing: Boolean get() = workout.finishedAt != null

    /** The rest to take after a set of this exercise: the program's, or the default. */
    fun restSecondsFor(exerciseId: Long): Int = targets[exerciseId]?.restSeconds ?: restSeconds
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModel(
    private val workoutId: Long,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    settings: SettingsRepository,
    private val restTimer: RestTimer,
) : ViewModel() {

    /** Name of the program day this workout came from, e.g. "Upper Body A"; null if ad-hoc. */
    val dayName: StateFlow<String?> = workoutDao.observeWorkoutDayName(workoutId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val exercises = workoutDao.observeExercises(workoutId)

    // Re-query last session's numbers only when the set of exercises changes, not on every keystroke.
    private val previousSets = exercises
        .map { list -> list.map { it.workoutExercise.id to it.exercise.id } }
        .distinctUntilChanged()
        .mapLatest { pairs ->
            pairs.associate { (workoutExerciseId, exerciseId) ->
                workoutExerciseId to workoutDao.previousSets(exerciseId, workoutId)
            }
        }

    // The program day's targets, if this workout was started from one.
    private val targets = workoutDao.observeWorkout(workoutId)
        .map { it?.programDayId }
        .distinctUntilChanged()
        .flatMapLatest { dayId -> if (dayId == null) flowOf(emptyList()) else workoutDao.observeDayTargets(dayId) }
        .map { list -> list.associateBy { it.exerciseId } }

    /** Null while loading, and again once the workout has been finished or discarded. */
    val state: StateFlow<WorkoutState?> = combine(
        combine(
            workoutDao.observeWorkout(workoutId),
            exercises,
            previousSets,
            settings.weightUnit,
            settings.restSeconds,
        ) { workout, exerciseList, previous, unit, restSeconds ->
            workout?.let { WorkoutState(it, exerciseList, previous, unit, restSeconds) }
        },
        targets,
    ) { state, targetMap -> state?.copy(targets = targetMap) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val restEndsAt: StateFlow<Long?> = restTimer.endsAt

    /** Moves an exercise one place earlier (-1) or later (+1) in the workout. */
    fun moveExercise(exercise: WorkoutExerciseWithSets, direction: Int) {
        val list = state.value?.exercises ?: return
        val index = list.indexOfFirst { it.workoutExercise.id == exercise.workoutExercise.id }
        val other = list.getOrNull(index + direction) ?: return
        viewModelScope.launch { workoutDao.swapWorkoutExercises(exercise.workoutExercise, other.workoutExercise) }
    }

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch { workoutDao.addExerciseToWorkout(workoutId, exerciseId) }
    }

    fun removeExercise(workoutExerciseId: Long) {
        viewModelScope.launch { workoutDao.deleteWorkoutExercise(workoutExerciseId) }
    }

    fun swapExercise(workoutExerciseId: Long, newExerciseId: Long) {
        viewModelScope.launch { workoutDao.setWorkoutExerciseExercise(workoutExerciseId, newExerciseId) }
    }

    /** Saves the standing note for an exercise; it shows in every workout that includes it. */
    fun setExerciseNotes(exerciseId: Long, notes: String) {
        viewModelScope.launch { exerciseDao.setNotes(exerciseId, notes.trim().take(MAX_NOTE_LENGTH)) }
    }

    fun toggleWarmup(set: SetEntry) {
        viewModelScope.launch { workoutDao.setWarmup(set.id, !set.isWarmup) }
    }

    /** Removes the bottom set of the exercise, unless it has already been ticked. */
    fun removeLastSet(exercise: WorkoutExerciseWithSets) {
        val last = exercise.sets.lastOrNull()?.takeIf { !it.completed } ?: return
        deleteSet(last.id)
    }

    /** Adds a set, carrying over the weight of the set above it. */
    fun addSet(exercise: WorkoutExerciseWithSets) {
        viewModelScope.launch {
            val workoutExerciseId = exercise.workoutExercise.id
            workoutDao.insertSets(
                listOf(
                    SetEntry(
                        workoutExerciseId = workoutExerciseId,
                        setNumber = workoutDao.maxSetNumber(workoutExerciseId) + 1,
                        weightKg = exercise.sets.lastOrNull()?.weightKg,
                    ),
                ),
            )
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch { workoutDao.deleteSet(setId) }
    }

    fun onWeightTyped(setId: Long, text: String, unit: WeightUnit) {
        viewModelScope.launch { workoutDao.setWeight(setId, parseWeightToKg(text, unit)) }
    }

    fun onRepsTyped(setId: Long, text: String) {
        viewModelScope.launch { workoutDao.setReps(setId, parseReps(text)) }
    }

    /**
     * Ticks or unticks a set. Ticking an empty set copies last session's numbers for that
     * set, so repeating a workout is one tap per set. A set needs reps to be ticked.
     */
    fun toggleCompleted(set: SetEntry, previous: SetEntry?, restSeconds: Int) {
        viewModelScope.launch {
            if (set.completed) {
                workoutDao.setCompletion(set.id, set.weightKg, set.reps, completed = false)
                return@launch
            }
            val reps = set.reps ?: previous?.reps ?: return@launch
            val weightKg = set.weightKg ?: previous?.weightKg
            workoutDao.setCompletion(set.id, weightKg, reps, completed = true)
            // No rest timer when correcting a workout that is already finished.
            if (state.value?.isEditing != true) restTimer.start(restSeconds)
        }
    }

    /**
     * Saves edits to an already finished workout: unticked sets are dropped, as on finishing.
     * [onDone] receives false if nothing was left and the workout was deleted.
     */
    /**
     * Moves a finished workout to a new start time, keeping its duration. A time in the
     * future is pulled back to now.
     */
    fun changeStart(newStartedAt: Long) {
        val workout = state.value?.workout ?: return
        val finishedAt = workout.finishedAt ?: return
        val start = minOf(newStartedAt, System.currentTimeMillis())
        viewModelScope.launch {
            workoutDao.setWorkoutTimes(workoutId, start, start + (finishedAt - workout.startedAt))
        }
    }

    fun doneEditing(onDone: (kept: Boolean) -> Unit) {
        viewModelScope.launch { onDone(workoutDao.pruneWorkout(workoutId)) }
    }

    fun addRestTime(seconds: Int) = restTimer.addSeconds(seconds)

    fun skipRest() = restTimer.cancel()

    /** [onDone] receives false if nothing was completed and the workout was thrown away. */
    fun finish(onDone: (saved: Boolean) -> Unit) {
        viewModelScope.launch {
            restTimer.cancel()
            onDone(workoutDao.finishWorkout(workoutId, System.currentTimeMillis()))
        }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            restTimer.cancel()
            workoutDao.deleteWorkout(workoutId)
            onDone()
        }
    }

    private companion object {
        const val MAX_NOTE_LENGTH = 500
    }
}
