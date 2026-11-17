package app.liftlog.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.liftlog.data.Equipment
import app.liftlog.data.Muscle
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "exercise", indices = [Index(value = ["name"], unique = true)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryMuscle: Muscle,
    val secondaryMuscles: List<Muscle> = emptyList(),
    val equipment: Equipment,
    val isCustom: Boolean = false,
    val archived: Boolean = false,
)

@Serializable
@Entity(tableName = "program")
data class Program(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean = false,
)

@Serializable
@Entity(
    tableName = "program_day",
    foreignKeys = [
        ForeignKey(
            entity = Program::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("programId")],
)
data class ProgramDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val position: Int,
    val name: String,
)

@Serializable
@Entity(
    tableName = "program_day_exercise",
    foreignKeys = [
        ForeignKey(
            entity = ProgramDay::class,
            parentColumns = ["id"],
            childColumns = ["programDayId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("programDayId"), Index("exerciseId")],
)
data class ProgramDayExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programDayId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

/** [programDayId] is null for an ad-hoc session. [finishedAt] is null while in progress. */
@Serializable
@Entity(
    tableName = "workout",
    foreignKeys = [
        ForeignKey(
            entity = ProgramDay::class,
            parentColumns = ["id"],
            childColumns = ["programDayId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("programDayId"), Index("startedAt")],
)
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programDayId: Long? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val notes: String = "",
)

@Serializable
@Entity(
    tableName = "workout_exercise",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    val notes: String = "",
)

/** Weight is always stored in kg. Null weight means bodyweight or not yet entered. */
@Serializable
@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExercise::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setNumber: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    val completed: Boolean = false,
)

data class WorkoutExerciseWithSets(
    @Embedded val workoutExercise: WorkoutExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val unsortedSets: List<SetEntry>,
) {
    val sets: List<SetEntry> get() = unsortedSets.sortedBy { it.setNumber }
}

data class WorkoutSummary(
    val id: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val exerciseCount: Int,
    val setCount: Int,
)

/** An exercise that has been logged at least once, for the Progress list. */
data class TrainedExercise(
    val exerciseId: Long,
    val name: String,
    val lastTrainedAt: Long,
    val sessionCount: Int,
    val maxWeightKg: Double?,
)

/** One completed set of an exercise, with the session it belongs to. */
data class ExerciseHistoryRow(
    val workoutId: Long,
    val startedAt: Long,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
)
