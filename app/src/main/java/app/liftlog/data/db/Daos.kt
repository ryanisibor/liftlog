package app.liftlog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
        SELECT w.id, w.startedAt, w.finishedAt,
            (SELECT COUNT(*) FROM workout_exercise we WHERE we.workoutId = w.id) AS exerciseCount,
            (SELECT COUNT(*) FROM set_entry s
                JOIN workout_exercise we ON s.workoutExerciseId = we.id
                WHERE we.workoutId = w.id AND s.completed = 1) AS setCount
        FROM workout w
        WHERE w.finishedAt IS NOT NULL
        ORDER BY w.startedAt DESC
        """,
    )
    fun observeFinishedWorkouts(): Flow<List<WorkoutSummary>>

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

    /** Completed sets of [exerciseId] from the most recent finished workout other than the current one. */
    @Query(
        """
        SELECT s.* FROM set_entry s
        WHERE s.completed = 1 AND s.workoutExerciseId = (
            SELECT we.id FROM workout_exercise we
            JOIN workout w ON w.id = we.workoutId
            WHERE we.exerciseId = :exerciseId
                AND w.finishedAt IS NOT NULL
                AND w.id != :currentWorkoutId
            ORDER BY w.startedAt DESC
            LIMIT 1
        )
        ORDER BY s.setNumber
        """,
    )
    suspend fun previousSets(exerciseId: Long, currentWorkoutId: Long): List<SetEntry>

    @Query(
        """
        SELECT w.id AS workoutId, w.startedAt, s.setNumber, s.weightKg, s.reps
        FROM set_entry s
        JOIN workout_exercise we ON s.workoutExerciseId = we.id
        JOIN workout w ON w.id = we.workoutId
        WHERE we.exerciseId = :exerciseId AND s.completed = 1 AND w.finishedAt IS NOT NULL
        ORDER BY w.startedAt DESC, s.setNumber
        """,
    )
    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseHistoryRow>>

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
        deleteIncompleteSets(workoutId)
        deleteExercisesWithoutSets(workoutId)
        if (exerciseCount(workoutId) == 0) {
            deleteWorkout(workoutId)
            return false
        }
        setFinishedAt(workoutId, finishedAt)
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
        val setCount = previousSets(exerciseId, workoutId).size.takeIf { it > 0 } ?: DEFAULT_SET_COUNT
        insertSets(
            (1..setCount).map { SetEntry(workoutExerciseId = workoutExerciseId, setNumber = it) },
        )
    }

    private companion object {
        const val DEFAULT_SET_COUNT = 3
    }
}
