package app.liftlog.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import app.liftlog.data.Muscle

class Converters {
    @TypeConverter
    fun musclesToString(muscles: List<Muscle>): String = muscles.joinToString(",") { it.name }

    @TypeConverter
    fun stringToMuscles(value: String): List<Muscle> =
        if (value.isEmpty()) emptyList() else value.split(",").map { Muscle.valueOf(it) }
}

/**
 * This database is the user's training history. Schema changes need a real migration;
 * never add fallbackToDestructiveMigration.
 */
@Database(
    entities = [
        Exercise::class,
        Program::class,
        ProgramDay::class,
        ProgramDayExercise::class,
        Workout::class,
        WorkoutExercise::class,
        SetEntry::class,
    ],
    version = 2,
    exportSchema = true,
    // Version 2 adds exercise.notes. Auto-migrations are generated from the files in app/schemas.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun backupDao(): BackupDao
    abstract fun programDao(): ProgramDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "liftlog.db").build()
    }
}
