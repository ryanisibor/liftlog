package app.liftlog.data

import android.content.Context
import android.net.Uri
import app.liftlog.data.db.ProgramDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/*
 * A program on its own, without any workout history, for copying a training plan to another
 * phone. Exercises are identified by name, since ids differ between phones; one the other
 * phone lacks (a custom exercise) is created there from the details carried in the file.
 */

@Serializable
data class ProgramFileExercise(
    val exercise: String,
    val primaryMuscle: Muscle,
    val secondaryMuscles: List<Muscle> = emptyList(),
    val equipment: Equipment,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

@Serializable
data class ProgramFileDay(val name: String, val exercises: List<ProgramFileExercise> = emptyList())

@Serializable
data class ProgramFile(
    val name: String,
    val days: List<ProgramFileDay> = emptyList(),
    val formatVersion: Int = ProgramCodec.FORMAT_VERSION,
    /**
     * Marks the file as a program. Deliberately has no default when reading: a file
     * without it is rejected, so stray JSON that happens to have a "name" is not imported.
     */
    val kind: String,
) {
    constructor(name: String, days: List<ProgramFileDay>) : this(name, days, ProgramCodec.FORMAT_VERSION, ProgramCodec.KIND)
}

class InvalidProgramFileException(message: String) : Exception(message)

object ProgramCodec {
    const val KIND = "liftlog-program"
    const val FORMAT_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(file: ProgramFile): String = json.encodeToString(ProgramFile.serializer(), file)

    /**
     * Reads a program file, rejecting anything else and tidying out-of-range numbers.
     * Throws [InvalidProgramFileException] with a message suitable for showing to the user.
     */
    fun decode(text: String): ProgramFile {
        val file = try {
            json.decodeFromString(ProgramFile.serializer(), text)
        } catch (_: IllegalArgumentException) {
            throw InvalidProgramFileException("This file is not a LiftLog program.")
        }
        if (file.kind != KIND) throw InvalidProgramFileException("This file is not a LiftLog program.")
        if (file.formatVersion > FORMAT_VERSION) {
            throw InvalidProgramFileException("This program was exported by a newer version of LiftLog.")
        }
        val tooBig = file.days.size > MAX_DAYS || file.days.any { it.exercises.size > MAX_EXERCISES_PER_DAY }
        val badNames = file.name.isBlank() || file.name.length > MAX_NAME ||
            file.days.any { day ->
                day.name.isBlank() || day.name.length > MAX_NAME ||
                    day.exercises.any { it.exercise.isBlank() || it.exercise.length > MAX_EXERCISE_NAME }
            }
        if (tooBig || badNames) throw InvalidProgramFileException("This program file contains invalid data.")

        return file.copy(
            name = file.name.trim(),
            days = file.days.map { day ->
                day.copy(
                    name = day.name.trim(),
                    exercises = day.exercises.map { item ->
                        val repMin = item.repMin.coerceIn(1, 50)
                        item.copy(
                            exercise = item.exercise.trim(),
                            sets = item.sets.coerceIn(1, 12),
                            repMin = repMin,
                            repMax = item.repMax.coerceIn(repMin, 50),
                            restSeconds = item.restSeconds.coerceIn(15, 600),
                        )
                    },
                )
            },
        )
    }

    /** A safe file name for a program: letters, digits and hyphens only. */
    fun fileName(programName: String): String {
        val slug = programName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        return "liftlog-program-${slug.ifEmpty { "plan" }}.json"
    }

    private const val MAX_DAYS = 30
    private const val MAX_EXERCISES_PER_DAY = 60
    private const val MAX_NAME = 40
    private const val MAX_EXERCISE_NAME = 200
}

/** Writes one program to a file, and adds a program from a file without touching anything else. */
class ProgramTransfer(private val context: Context, private val programDao: ProgramDao) {

    suspend fun exportTo(programId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        val file = programDao.programFile(programId) ?: throw IOException("Program not found")
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the chosen file")
        stream.bufferedWriter().use { it.write(ProgramCodec.encode(file)) }
    }

    /** Returns the id of the new program. Existing programs and all history are left as they are. */
    suspend fun importFrom(uri: Uri): Long = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw InvalidProgramFileException("Could not open the chosen file.")
        val text = stream.use {
            val bytes = it.readNBytes(MAX_FILE_BYTES + 1)
            if (bytes.size > MAX_FILE_BYTES) throw InvalidProgramFileException("This file is too large to be a LiftLog program.")
            bytes.toString(Charsets.UTF_8)
        }
        programDao.addProgramFromFile(ProgramCodec.decode(text))
    }

    private companion object {
        const val MAX_FILE_BYTES = 1024 * 1024
    }
}
