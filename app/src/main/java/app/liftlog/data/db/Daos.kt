package app.liftlog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.liftlog.data.Equipment
import app.liftlog.data.Muscle
import app.liftlog.domain.PlannedSet
import app.liftlog.domain.planSets
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    fun observe(id: Long): Flow<Exercise?>

    /** Throws SQLiteConstraintException if the name is already taken. */
    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Query("SELECT * FROM exercise WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Exercise?

    /** Renames or reclassifies a custom exercise. Throws SQLiteConstraintException if the name is taken. */
    @Query("UPDATE exercise SET name = :name, primaryMuscle = :muscle, equipment = :equipment WHERE id = :id AND isCustom = 1")
    suspend fun updateCustom(id: Long, name: String, muscle: Muscle, equipment: Equipment)

    /** Hides an exercise from the library and pickers. Its logged history is kept. */
    @Query("UPDATE exercise SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE exercise SET notes = :notes WHERE id = :id")
    suspend fun setNotes(id: Long, notes: String)

    /** Used for seeding: entries whose name already exists are skipped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(exercises: List<Exercise>)
}

@Dao
interface WorkoutDao {
    // --- workouts ---

    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Query("SELECT * FROM workout WHERE id = :id")
    fun observeWorkout(id: Long): Flow<Workout?>

    @Query("SELECT * FROM workout WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveWorkout(): Flow<Workout?>

    @Query("SELECT id FROM workout WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeWorkoutId(): Long?

    @Query(
        """
        SELECT w.id, w.startedAt, w.finishedAt, d.name AS dayName,
            (SELECT COUNT(*) FROM workout_exercise we WHERE we.workoutId = w.id) AS exerciseCount,
            (SELECT COUNT(*) FROM set_entry s
                JOIN workout_exercise we ON s.workoutExerciseId = we.id
                WHERE we.workoutId = w.id AND s.completed = 1) AS setCount
        FROM workout w
        LEFT JOIN program_day d ON d.id = w.programDayId
        WHERE w.finishedAt IS NOT NULL
        ORDER BY w.startedAt DESC
        """,
    )
    fun observeFinishedWorkouts(): Flow<List<WorkoutSummary>>

    /** Name of the program day a workout was started from; null for ad-hoc workouts. */
    @Query("SELECT d.name FROM workout w JOIN program_day d ON d.id = w.programDayId WHERE w.id = :workoutId")
    fun observeWorkoutDayName(workoutId: Long): Flow<String?>

    /** Moves a finished workout to another date and time; used when it was logged late. */
    @Query("UPDATE workout SET startedAt = :startedAt, finishedAt = :finishedAt WHERE id = :id AND finishedAt IS NOT NULL")
    suspend fun setWorkoutTimes(id: Long, startedAt: Long, finishedAt: Long)

    @Query("UPDATE workout SET finishedAt = :finishedAt WHERE id = :id")
    suspend fun setFinishedAt(id: Long, finishedAt: Long)

    @Query("DELETE FROM workout WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    // --- exercises within a workout ---

    @Transaction
    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY position")
    fun observeExercises(workoutId: Long): Flow<List<WorkoutExerciseWithSets>>

    @Insert
    suspend fun insertWorkoutExercise(workoutExercise: WorkoutExercise): Long

    @Query("SELECT COALESCE(MAX(position), -1) FROM workout_exercise WHERE workoutId = :workoutId")
    suspend fun maxPosition(workoutId: Long): Int

    @Query("DELETE FROM workout_exercise WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: Long)

    /** Swaps the exercise in place; sets already logged in this slot are kept. */
    @Query("UPDATE workout_exercise SET exerciseId = :exerciseId WHERE id = :id")
    suspend fun setWorkoutExerciseExercise(id: Long, exerciseId: Long)

    @Query("UPDATE workout_exercise SET notes = :notes WHERE id = :id")
    suspend fun setWorkoutExerciseNotes(id: Long, notes: String)

    // --- sets ---

    @Insert
    suspend fun insertSets(sets: List<SetEntry>)

    @Query("SELECT COALESCE(MAX(setNumber), 0) FROM set_entry WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun maxSetNumber(workoutExerciseId: Long): Int

    @Query("UPDATE set_entry SET weightKg = :weightKg WHERE id = :id")
    suspend fun setWeight(id: Long, weightKg: Double?)

    @Query("UPDATE set_entry SET reps = :reps WHERE id = :id")
    suspend fun setReps(id: Long, reps: Int?)

    @Query("UPDATE set_entry SET weightKg = :weightKg, reps = :reps, completed = :completed WHERE id = :id")
    suspend fun setCompletion(id: Long, weightKg: Double?, reps: Int?, completed: Boolean)

    @Query("DELETE FROM set_entry WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("UPDATE set_entry SET isWarmup = :isWarmup WHERE id = :id")
    suspend fun setWarmup(id: Long, isWarmup: Boolean)

    /**
     * Completed sets of [exerciseId] from the most recent finished workout that started
     * before the current one. "Before" matters when an older workout is being edited.
     */
    @Query(
        """
        SELECT s.* FROM set_entry s
        WHERE s.completed = 1 AND s.workoutExerciseId = (
            SELECT we.id FROM workout_exercise we
            JOIN workout w ON w.id = we.workoutId
            WHERE we.exerciseId = :exerciseId
                AND w.finishedAt IS NOT NULL
                AND w.id != :currentWorkoutId
                AND w.startedAt <= (SELECT startedAt FROM workout WHERE id = :currentWorkoutId)
            ORDER BY w.startedAt DESC
            LIMIT 1
        )
        ORDER BY s.setNumber
        """,
    )
    suspend fun previousSets(exerciseId: Long, currentWorkoutId: Long): List<SetEntry>

    /** Every completed working set of [exerciseId] from finished workouts that started before the current one. */
    @Query(
        """
        SELECT s.weightKg AS weightKg, s.reps AS reps
        FROM set_entry s
        JOIN workout_exercise we ON s.workoutExerciseId = we.id
        JOIN workout w ON w.id = we.workoutId
        WHERE we.exerciseId = :exerciseId AND s.completed = 1 AND s.isWarmup = 0
            AND w.finishedAt IS NOT NULL AND w.id != :currentWorkoutId
            AND w.startedAt <= (SELECT startedAt FROM workout WHERE id = :currentWorkoutId)
        """,
    )
    suspend fun priorSets(exerciseId: Long, currentWorkoutId: Long): List<PriorSetRow>

    @Query(
        """
        SELECT w.id AS workoutId, w.startedAt, s.setNumber, s.weightKg, s.reps, s.isWarmup
        FROM set_entry s
        JOIN workout_exercise we ON s.workoutExerciseId = we.id
        JOIN workout w ON w.id = we.workoutId
        WHERE we.exerciseId = :exerciseId AND s.completed = 1 AND w.finishedAt IS NOT NULL
        ORDER BY w.startedAt DESC, s.setNumber
        """,
    )
    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseHistoryRow>>

    /** Finished workouts that started in [from, until), newest first, for the calendar. */
    @Query(
        """
        SELECT w.id, w.startedAt, w.finishedAt, d.name AS dayName,
            (SELECT COUNT(*) FROM set_entry s
                JOIN workout_exercise we ON s.workoutExerciseId = we.id
                WHERE we.workoutId = w.id AND s.completed = 1 AND s.isWarmup = 0) AS setCount,
            (SELECT COALESCE(SUM(s.weightKg * s.reps), 0) FROM set_entry s
                JOIN workout_exercise we ON s.workoutExerciseId = we.id
                WHERE we.workoutId = w.id AND s.completed = 1 AND s.isWarmup = 0) AS volumeKg
        FROM workout w
        LEFT JOIN program_day d ON d.id = w.programDayId
        WHERE w.finishedAt IS NOT NULL AND w.startedAt >= :from AND w.startedAt < :until
        ORDER BY w.startedAt DESC
        """,
    )
    fun observeWorkoutsBetween(from: Long, until: Long): Flow<List<CalendarWorkout>>

    @Query(
        """
        SELECT we.workoutId AS workoutId, e.primaryMuscle AS primaryMuscle, e.secondaryMuscles AS secondaryMuscles
        FROM workout_exercise we
        JOIN workout w ON w.id = we.workoutId
        JOIN exercise e ON e.id = we.exerciseId
        WHERE w.finishedAt IS NOT NULL AND w.startedAt >= :from AND w.startedAt < :until
        """,
    )
    fun observeWorkoutMusclesBetween(from: Long, until: Long): Flow<List<WorkoutMuscles>>

    /** Every exercise with at least one completed set in a finished workout, most recent first. */
    @Query(
        """
        SELECT e.id AS exerciseId, e.name AS name,
            MAX(w.startedAt) AS lastTrainedAt,
            COUNT(DISTINCT w.id) AS sessionCount,
            MAX(CASE WHEN s.isWarmup = 0 THEN s.weightKg END) AS maxWeightKg
        FROM set_entry s
        JOIN workout_exercise we ON s.workoutExerciseId = we.id
        JOIN workout w ON w.id = we.workoutId
        JOIN exercise e ON e.id = we.exerciseId
        WHERE s.completed = 1 AND w.finishedAt IS NOT NULL
        GROUP BY e.id
        ORDER BY lastTrainedAt DESC
        """,
    )
    fun observeTrainedExercises(): Flow<List<TrainedExercise>>

    @Query("SELECT startedAt FROM workout WHERE finishedAt IS NOT NULL AND startedAt >= :since")
    fun observeFinishedStartTimes(since: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM workout WHERE finishedAt IS NOT NULL")
    fun observeFinishedWorkoutCount(): Flow<Int>

    /** Completed working sets per exercise for workouts started in [from, until). */
    @Query(
        """
        SELECT e.primaryMuscle AS primaryMuscle, e.secondaryMuscles AS secondaryMuscles, COUNT(*) AS sets
        FROM set_entry s
        JOIN workout_exercise we ON s.workoutExerciseId = we.id
        JOIN workout w ON w.id = we.workoutId
        JOIN exercise e ON e.id = we.exerciseId
        WHERE s.completed = 1 AND s.isWarmup = 0 AND w.finishedAt IS NOT NULL
            AND w.startedAt >= :from AND w.startedAt < :until
        GROUP BY e.id
        """,
    )
    fun observeMuscleSetRows(from: Long, until: Long): Flow<List<MuscleSetRow>>

    /** The exercises used most recently, newest first, for the top of the picker. */
    @Query(
        """
        SELECT we.exerciseId FROM workout_exercise we
        JOIN workout w ON w.id = we.workoutId
        JOIN exercise e ON e.id = we.exerciseId
        WHERE e.archived = 0
        GROUP BY we.exerciseId
        ORDER BY MAX(w.startedAt) DESC
        LIMIT :limit
        """,
    )
    fun observeRecentExerciseIds(limit: Int): Flow<List<Long>>

    // --- finishing ---

    @Query(
        """
        DELETE FROM set_entry WHERE completed = 0 AND workoutExerciseId IN
            (SELECT id FROM workout_exercise WHERE workoutId = :workoutId)
        """,
    )
    suspend fun deleteIncompleteSets(workoutId: Long)

    @Query(
        """
        DELETE FROM workout_exercise WHERE workoutId = :workoutId
            AND id NOT IN (SELECT workoutExerciseId FROM set_entry)
        """,
    )
    suspend fun deleteExercisesWithoutSets(workoutId: Long)

    @Query("SELECT COUNT(*) FROM workout_exercise WHERE workoutId = :workoutId")
    suspend fun exerciseCount(workoutId: Long): Int

    /**
     * Drops sets that were never ticked, then closes the workout.
     * Returns false, and deletes the workout, if nothing was completed.
     */
    @Transaction
    suspend fun finishWorkout(workoutId: Long, finishedAt: Long): Boolean {
        if (!pruneWorkout(workoutId)) return false
        setFinishedAt(workoutId, finishedAt)
        return true
    }

    /**
     * Drops unticked sets and exercises left with none. Returns false, and deletes the
     * workout, if nothing remains. Also used when saving edits to a finished workout.
     */
    @Transaction
    suspend fun pruneWorkout(workoutId: Long): Boolean {
        deleteIncompleteSets(workoutId)
        deleteExercisesWithoutSets(workoutId)
        if (exerciseCount(workoutId) == 0) {
            deleteWorkout(workoutId)
            return false
        }
        return true
    }

    /** Adds an exercise with one empty set per set done last time (three if it is new). */
    @Transaction
    suspend fun addExerciseToWorkout(workoutId: Long, exerciseId: Long) {
        val workoutExerciseId = insertWorkoutExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = exerciseId,
                position = maxPosition(workoutId) + 1,
            ),
        )
        insertPlannedSets(workoutExerciseId, exerciseId, workoutId, targetWorkingSets = null)
    }

    // --- program-based workouts ---

    @Query("SELECT * FROM program_day_exercise WHERE programDayId = :dayId ORDER BY position")
    suspend fun dayExercises(dayId: Long): List<ProgramDayExercise>

    @Query("SELECT * FROM program_day_exercise WHERE programDayId = :dayId")
    fun observeDayTargets(dayId: Long): Flow<List<ProgramDayExercise>>

    /**
     * Starts a workout pre-filled from a program day, one empty set per target set.
     * If a workout is already in progress, returns that one instead of starting another.
     */
    @Transaction
    suspend fun startWorkoutFromDay(dayId: Long, startedAt: Long): Long {
        activeWorkoutId()?.let { return it }
        val workoutId = insertWorkout(Workout(programDayId = dayId, startedAt = startedAt))
        dayExercises(dayId).forEachIndexed { index, target ->
            val workoutExerciseId = insertWorkoutExercise(
                WorkoutExercise(workoutId = workoutId, exerciseId = target.exerciseId, position = index),
            )
            // The target counts working sets; warm-ups done last time are added in front.
            insertPlannedSets(workoutExerciseId, target.exerciseId, workoutId, target.targetSets)
        }
        return workoutId
    }

    /** Creates the rows for an exercise, laid out and pre-filled with last time's weights. */
    suspend fun insertPlannedSets(workoutExerciseId: Long, exerciseId: Long, workoutId: Long, targetWorkingSets: Int?) {
        val previous = previousSets(exerciseId, workoutId).map { PlannedSet(it.isWarmup, it.weightKg) }
        insertSets(
            planSets(previous, targetWorkingSets, DEFAULT_SET_COUNT).mapIndexed { index, planned ->
                SetEntry(
                    workoutExerciseId = workoutExerciseId,
                    setNumber = index + 1,
                    weightKg = planned.weightKg,
                    isWarmup = planned.isWarmup,
                )
            },
        )
    }

    @Query("UPDATE workout_exercise SET position = :position WHERE id = :id")
    suspend fun setWorkoutExercisePosition(id: Long, position: Int)

    @Transaction
    suspend fun swapWorkoutExercises(first: WorkoutExercise, second: WorkoutExercise) {
        setWorkoutExercisePosition(first.id, second.position)
        setWorkoutExercisePosition(second.id, first.position)
    }

    private companion object {
        const val DEFAULT_SET_COUNT = 3
    }
}
