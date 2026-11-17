package app.liftlog.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class DayExerciseWithExercise(
    @Embedded val item: ProgramDayExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
)

data class DayWithExercises(
    @Embedded val day: ProgramDay,
    @Relation(entity = ProgramDayExercise::class, parentColumn = "id", entityColumn = "programDayId")
    val unsortedItems: List<DayExerciseWithExercise>,
) {
    val items: List<DayExerciseWithExercise> get() = unsortedItems.sortedBy { it.item.position }
    val totalSets: Int get() = unsortedItems.sumOf { it.item.targetSets }
}

/** The heaviest completed set of an exercise in the most recent workout that included it. */
data class LastWeight(val exerciseId: Long, val weightKg: Double?)

@Dao
interface ProgramDao {
    // --- programs ---

    @Query("SELECT * FROM program ORDER BY name COLLATE NOCASE")
    fun observePrograms(): Flow<List<Program>>

    @Query("SELECT * FROM program WHERE id = :id")
    fun observeProgram(id: Long): Flow<Program?>

    @Query("SELECT * FROM program WHERE isActive = 1 LIMIT 1")
    fun observeActiveProgram(): Flow<Program?>

    @Query("SELECT COUNT(*) FROM program")
    suspend fun programCount(): Int

    @Insert
    suspend fun insertProgram(program: Program): Long

    @Query("UPDATE program SET name = :name WHERE id = :id")
    suspend fun renameProgram(id: Long, name: String)

    /** Exactly one program is active: the one shown on the Home screen. */
    @Query("UPDATE program SET isActive = (id = :id)")
    suspend fun setActiveProgram(id: Long)

    @Query("DELETE FROM program WHERE id = :id")
    suspend fun deleteProgram(id: Long)

    // --- days ---

    @Transaction
    @Query("SELECT * FROM program_day WHERE programId = :programId ORDER BY position")
    fun observeDays(programId: Long): Flow<List<DayWithExercises>>

    @Transaction
    @Query("SELECT * FROM program_day WHERE id = :dayId")
    fun observeDay(dayId: Long): Flow<DayWithExercises?>

    @Query("SELECT COALESCE(MAX(position), -1) FROM program_day WHERE programId = :programId")
    suspend fun maxDayPosition(programId: Long): Int

    @Insert
    suspend fun insertDay(day: ProgramDay): Long

    @Query("UPDATE program_day SET name = :name WHERE id = :id")
    suspend fun renameDay(id: Long, name: String)

    @Query("UPDATE program_day SET position = :position WHERE id = :id")
    suspend fun setDayPosition(id: Long, position: Int)

    @Query("DELETE FROM program_day WHERE id = :id")
    suspend fun deleteDay(id: Long)

    @Transaction
    suspend fun swapDays(first: ProgramDay, second: ProgramDay) {
        setDayPosition(first.id, second.position)
        setDayPosition(second.id, first.position)
    }

    // --- exercises within a day ---

    @Query("SELECT COALESCE(MAX(position), -1) FROM program_day_exercise WHERE programDayId = :dayId")
    suspend fun maxItemPosition(dayId: Long): Int

    @Insert
    suspend fun insertItem(item: ProgramDayExercise): Long

    @Query("UPDATE program_day_exercise SET targetSets = :sets, repMin = :repMin, repMax = :repMax WHERE id = :id")
    suspend fun setItemTargets(id: Long, sets: Int, repMin: Int, repMax: Int)

    @Query("UPDATE program_day_exercise SET position = :position WHERE id = :id")
    suspend fun setItemPosition(id: Long, position: Int)

    @Query("DELETE FROM program_day_exercise WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Transaction
    suspend fun swapItems(first: ProgramDayExercise, second: ProgramDayExercise) {
        setItemPosition(first.id, second.position)
        setItemPosition(second.id, first.position)
    }

    // --- what the Home screen needs to know about past training ---

    /** The program day of the most recently finished workout from this program, if any. */
    @Query(
        """
        SELECT w.programDayId FROM workout w
        JOIN program_day d ON d.id = w.programDayId
        WHERE d.programId = :programId AND w.finishedAt IS NOT NULL
        ORDER BY w.startedAt DESC LIMIT 1
        """,
    )
    fun observeLastTrainedDayId(programId: Long): Flow<Long?>

    @Query(
        """
        SELECT DISTINCT programDayId FROM workout
        WHERE programDayId IS NOT NULL AND finishedAt IS NOT NULL AND startedAt >= :since
        """,
    )
    fun observeDayIdsTrainedSince(since: Long): Flow<List<Long>>

    @Query(
        """
        SELECT we.exerciseId AS exerciseId, MAX(s.weightKg) AS weightKg
        FROM set_entry s
        JOIN workout_exercise we ON s.workoutExerciseId = we.id
        WHERE s.completed = 1 AND we.workoutId = (
            SELECT w2.id FROM workout w2
            JOIN workout_exercise we2 ON we2.workoutId = w2.id
            WHERE we2.exerciseId = we.exerciseId AND w2.finishedAt IS NOT NULL
            ORDER BY w2.startedAt DESC LIMIT 1
        )
        GROUP BY we.exerciseId
        """,
    )
    fun observeLastWeights(): Flow<List<LastWeight>>
}
