package app.liftlog

import app.liftlog.data.Backup
import app.liftlog.data.BackupCodec
import app.liftlog.data.Equipment
import app.liftlog.data.InvalidBackupException
import app.liftlog.data.Muscle
import app.liftlog.data.db.Exercise
import app.liftlog.data.db.SetEntry
import app.liftlog.data.db.Workout
import app.liftlog.data.db.WorkoutExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val sample = Backup(
        exportedAt = 1_800_000_000_000,
        exercises = listOf(
            Exercise(1, "Barbell Bench Press", Muscle.CHEST, listOf(Muscle.TRICEPS, Muscle.SHOULDERS), Equipment.BARBELL),
            Exercise(2, "My \"odd\" lift, v2", Muscle.FULL_BODY, emptyList(), Equipment.OTHER, isCustom = true),
        ),
        workouts = listOf(
            Workout(id = 1, startedAt = 1_799_990_000_000, finishedAt = 1_799_993_600_000, notes = "felt good"),
            Workout(id = 2, startedAt = 1_799_999_000_000),
        ),
        workoutExercises = listOf(WorkoutExercise(id = 1, workoutId = 1, exerciseId = 1, position = 0)),
        sets = listOf(
            SetEntry(id = 1, workoutExerciseId = 1, setNumber = 1, weightKg = 61.23496995, reps = 8, completed = true),
            SetEntry(id = 2, workoutExerciseId = 1, setNumber = 2, weightKg = null, reps = 12, rpe = 8.5, isWarmup = true),
        ),
    )

    @Test
    fun `a backup survives a round trip unchanged`() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample)))
    }

    @Test
    fun `rejects files that are not backups`() {
        for (text in listOf("", "hello", "[]", "{}", """{"formatVersion":1}""", """{"exercises":"nope"}""")) {
            assertThrows(text, InvalidBackupException::class.java) { BackupCodec.decode(text) }
        }
    }

    @Test
    fun `rejects a backup from a newer app`() {
        val newer = BackupCodec.encode(sample.copy(formatVersion = BackupCodec.FORMAT_VERSION + 1))
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(newer) }
    }

    @Test
    fun `rejects an unknown muscle name`() {
        val text = BackupCodec.encode(sample).replace("\"CHEST\"", "\"WINGS\"")
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(text) }
    }

    @Test
    fun `rejects out-of-range values`() {
        val badWeight = sample.copy(sets = listOf(sample.sets[0].copy(weightKg = -5.0)))
        val badReps = sample.copy(sets = listOf(sample.sets[0].copy(reps = 100_000)))
        val badTimes = sample.copy(workouts = listOf(Workout(id = 1, startedAt = 2000, finishedAt = 1000)))
        for (bad in listOf(badWeight, badReps, badTimes)) {
            assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(BackupCodec.encode(bad)) }
        }
    }

    @Test
    fun `ignores fields added by a later version of the same format`() {
        val text = BackupCodec.encode(sample).replaceFirst("{", """{"somethingNew":true,""")
        assertEquals(sample, BackupCodec.decode(text))
    }
}
