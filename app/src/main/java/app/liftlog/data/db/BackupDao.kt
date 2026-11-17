package app.liftlog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.liftlog.data.Backup

/** Whole-database read and replace, used only by export and import. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM exercise") suspend fun exercises(): List<Exercise>
    @Query("SELECT * FROM program") suspend fun programs(): List<Program>
    @Query("SELECT * FROM program_day") suspend fun programDays(): List<ProgramDay>
    @Query("SELECT * FROM program_day_exercise") suspend fun programDayExercises(): List<ProgramDayExercise>
    @Query("SELECT * FROM workout") suspend fun workouts(): List<Workout>
    @Query("SELECT * FROM workout_exercise") suspend fun workoutExercises(): List<WorkoutExercise>
    @Query("SELECT * FROM set_entry") suspend fun sets(): List<SetEntry>

    @Insert suspend fun insertExercises(rows: List<Exercise>)
    @Insert suspend fun insertPrograms(rows: List<Program>)
    @Insert suspend fun insertProgramDays(rows: List<ProgramDay>)
    @Insert suspend fun insertProgramDayExercises(rows: List<ProgramDayExercise>)
    @Insert suspend fun insertWorkouts(rows: List<Workout>)
    @Insert suspend fun insertWorkoutExercises(rows: List<WorkoutExercise>)
    @Insert suspend fun insertSets(rows: List<SetEntry>)

    @Query("DELETE FROM set_entry WHERE 1 = 1") suspend fun clearSets()
    @Query("DELETE FROM workout_exercise WHERE 1 = 1") suspend fun clearWorkoutExercises()
    @Query("DELETE FROM workout WHERE 1 = 1") suspend fun clearWorkouts()
    @Query("DELETE FROM program_day_exercise WHERE 1 = 1") suspend fun clearProgramDayExercises()
    @Query("DELETE FROM program_day WHERE 1 = 1") suspend fun clearProgramDays()
    @Query("DELETE FROM program WHERE 1 = 1") suspend fun clearPrograms()
    @Query("DELETE FROM exercise WHERE 1 = 1") suspend fun clearExercises()

    @Transaction
    suspend fun snapshot(): Backup = Backup(
        exercises = exercises(),
        programs = programs(),
        programDays = programDays(),
        programDayExercises = programDayExercises(),
        workouts = workouts(),
        workoutExercises = workoutExercises(),
        sets = sets(),
    )

    /**
     * Replaces everything with [backup]. Runs in one transaction, so if the file's contents
     * are inconsistent (a foreign key fails) nothing changes and the existing data survives.
     */
    @Transaction
    suspend fun replaceAll(backup: Backup) {
        // Children before parents.
        clearSets()
        clearWorkoutExercises()
        clearWorkouts()
        clearProgramDayExercises()
        clearProgramDays()
        clearPrograms()
        clearExercises()
        // Parents before children.
        insertExercises(backup.exercises)
        insertPrograms(backup.programs)
        insertProgramDays(backup.programDays)
        insertProgramDayExercises(backup.programDayExercises)
        insertWorkouts(backup.workouts)
        insertWorkoutExercises(backup.workoutExercises)
        insertSets(backup.sets)
    }
}
