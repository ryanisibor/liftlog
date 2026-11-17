package app.liftlog.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftlog.data.SettingsRepository
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
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModel(
    private val workoutId: Long,
    private val workoutDao: WorkoutDao,
    settings: SettingsRepository,
    private val restTimer: RestTimer,
) : ViewModel() {

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

    /** Null while loading, and again once the workout has been finished or discarded. */
    val state: StateFlow<WorkoutState?> = combine(
        workoutDao.observeWorkout(workoutId),
        exercises,
        previousSets,
        settings.weightUnit,
        settings.restSeconds,
    ) { workout, exerciseList, previous, unit, restSeconds ->
        workout?.let { WorkoutState(it, exerciseList, previous, unit, restSeconds) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val restEndsAt: StateFlow<Long?> = restTimer.endsAt

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch { workoutDao.addExerciseToWorkout(workoutId, exerciseId) }
    }

    fun removeExercise(workoutExerciseId: Long) {
        viewModelScope.launch { workoutDao.deleteWorkoutExercise(workoutExerciseId) }
    }

    fun swapExercise(workoutExerciseId: Long, newExerciseId: Long) {
        viewModelScope.launch { workoutDao.setWorkoutExerciseExercise(workoutExerciseId, newExerciseId) }
    }

    fun setNotes(workoutExerciseId: Long, notes: String) {
        viewModelScope.launch { workoutDao.setWorkoutExerciseNotes(workoutExerciseId, notes.trim().take(MAX_NOTE_LENGTH)) }
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
            restTimer.start(restSeconds)
        }
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
