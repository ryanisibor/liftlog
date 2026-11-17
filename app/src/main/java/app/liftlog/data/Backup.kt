package app.liftlog.data

import android.content.Context
import android.net.Uri
import app.liftlog.data.db.BackupDao
import app.liftlog.data.db.Exercise
import app.liftlog.data.db.ExerciseDao
import app.liftlog.data.db.Program
import app.liftlog.data.db.ProgramDay
import app.liftlog.data.db.ProgramDayExercise
import app.liftlog.data.db.SetEntry
import app.liftlog.data.db.Workout
import app.liftlog.data.db.WorkoutExercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything in the database, as written to a backup file.
 *
 * [formatVersion] must be raised, and [BackupCodec.decode] taught to read the old shape,
 * whenever a change here or in the entities would stop an older file from loading.
 */
@Serializable
data class Backup(
    val formatVersion: Int = BackupCodec.FORMAT_VERSION,
    val exportedAt: Long = 0,
    val exercises: List<Exercise> = emptyList(),
    val programs: List<Program> = emptyList(),
    val programDays: List<ProgramDay> = emptyList(),
    val programDayExercises: List<ProgramDayExercise> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val workoutExercises: List<WorkoutExercise> = emptyList(),
    val sets: List<SetEntry> = emptyList(),
)

class InvalidBackupException(message: String) : Exception(message)

/** Turns a [Backup] into JSON text and back, validating what comes in. */
object BackupCodec {
    const val FORMAT_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /** Throws [InvalidBackupException] with a message suitable for showing to the user. */
    fun decode(text: String): Backup {
        val backup = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (_: IllegalArgumentException) {
            // SerializationException extends this; also covers unknown muscle or equipment names.
            throw InvalidBackupException("This file is not a LiftLog backup.")
        }
        if (backup.formatVersion > FORMAT_VERSION) {
            throw InvalidBackupException("This backup was made by a newer version of LiftLog.")
        }
        if (backup.formatVersion < 1 || backup.exercises.isEmpty()) {
            throw InvalidBackupException("This file is not a LiftLog backup.")
        }
        validate(backup)
        return backup
    }

    private fun validate(backup: Backup) {
        val problem = when {
            backup.exercises.any { it.name.isBlank() || it.name.length > MAX_NAME_LENGTH } -> "an exercise name"
            backup.sets.any { set -> set.weightKg?.let { it.isNaN() || it < 0 || it > MAX_WEIGHT_KG } == true } -> "a weight"
            backup.sets.any { set -> set.reps?.let { it < 0 || it > MAX_REPS } == true } -> "a rep count"
            backup.workouts.any { it.startedAt <= 0 || (it.finishedAt != null && it.finishedAt < it.startedAt) } -> "a workout time"
            else -> return
        }
        throw InvalidBackupException("This backup contains invalid data ($problem).")
    }

    private const val MAX_NAME_LENGTH = 200
    private const val MAX_WEIGHT_KG = 2000.0
    private const val MAX_REPS = 1000
}

/** Reads and writes backup files chosen by the user through the system file picker. */
class BackupRepository(
    private val context: Context,
    private val backupDao: BackupDao,
    private val exerciseDao: ExerciseDao,
) {
    /** Returns the number of workouts written. */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val backup = backupDao.snapshot().copy(exportedAt = System.currentTimeMillis())
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw java.io.IOException("Could not open the chosen file")
        stream.bufferedWriter().use { it.write(BackupCodec.encode(backup)) }
        backup.workouts.size
    }

    /**
     * Replaces all data with the contents of the file. Returns the number of workouts loaded.
     * Throws [InvalidBackupException] if the file is unusable; existing data is then untouched.
     */
    suspend fun importFrom(uri: Uri): Int = withContext(Dispatchers.IO) {
        val backup = BackupCodec.decode(readLimited(uri))
        try {
            backupDao.replaceAll(backup)
        } catch (_: android.database.sqlite.SQLiteException) {
            throw InvalidBackupException("This backup is damaged or inconsistent. Nothing was changed.")
        }
        // A backup from an older app version may lack exercises added to the library since.
        val seed = context.assets.open(ExerciseSeed.ASSET_NAME).bufferedReader().use { it.readText() }
        exerciseDao.insertIgnoringExisting(ExerciseSeed.parse(seed))
        backup.workouts.size
    }

    private fun readLimited(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw InvalidBackupException("Could not open the chosen file.")
        stream.use {
            val bytes = it.readNBytes(MAX_FILE_BYTES + 1)
            if (bytes.size > MAX_FILE_BYTES) throw InvalidBackupException("This file is too large to be a LiftLog backup.")
            return bytes.toString(Charsets.UTF_8)
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 50 * 1024 * 1024
    }
}
