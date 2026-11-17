package app.liftlog

import app.liftlog.data.BackupCodec
import app.liftlog.data.Equipment
import app.liftlog.data.InvalidBackupException
import app.liftlog.data.InvalidProgramFileException
import app.liftlog.data.Muscle
import app.liftlog.data.ProgramCodec
import app.liftlog.data.ProgramFile
import app.liftlog.data.ProgramFileDay
import app.liftlog.data.ProgramFileExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProgramCodecTest {
    private fun exercise(sets: Int = 4, repMin: Int = 8, repMax: Int = 12, rest: Int = 120) = ProgramFileExercise(
        exercise = "Barbell Bench Press",
        primaryMuscle = Muscle.CHEST,
        secondaryMuscles = listOf(Muscle.TRICEPS),
        equipment = Equipment.BARBELL,
        sets = sets,
        repMin = repMin,
        repMax = repMax,
        restSeconds = rest,
    )

    private val sample = ProgramFile(
        name = "Upper / Lower",
        days = listOf(ProgramFileDay("Upper Body A", listOf(exercise())), ProgramFileDay("Rest day ideas")),
    )

    @Test
    fun `a program survives a round trip unchanged`() {
        assertEquals(sample, ProgramCodec.decode(ProgramCodec.encode(sample)))
    }

    @Test
    fun `rejects files that are not programs`() {
        for (text in listOf("", "hello", "{}", """{"name":"x"}""", """{"kind":"something-else","name":"x"}""")) {
            assertThrows(text, InvalidProgramFileException::class.java) { ProgramCodec.decode(text) }
        }
    }

    @Test
    fun `a program file is not accepted as a full backup, nor a backup as a program`() {
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(ProgramCodec.encode(sample)) }
        assertThrows(InvalidProgramFileException::class.java) {
            ProgramCodec.decode("""{"formatVersion":1,"exercises":[],"workouts":[]}""")
        }
    }

    @Test
    fun `rejects blank names and a newer format`() {
        assertThrows(InvalidProgramFileException::class.java) { ProgramCodec.decode(ProgramCodec.encode(sample.copy(name = " "))) }
        assertThrows(InvalidProgramFileException::class.java) {
            ProgramCodec.decode(ProgramCodec.encode(sample.copy(formatVersion = ProgramCodec.FORMAT_VERSION + 1)))
        }
    }

    @Test
    fun `pulls out-of-range targets back into range`() {
        val wild = sample.copy(days = listOf(ProgramFileDay("Day", listOf(exercise(sets = 99, repMin = 20, repMax = 5, rest = 1)))))
        val item = ProgramCodec.decode(ProgramCodec.encode(wild)).days.single().exercises.single()
        assertEquals(12, item.sets)
        assertEquals(20, item.repMin)
        assertEquals(20, item.repMax)
        assertEquals(15, item.restSeconds)
    }

    @Test
    fun `file names are made safe`() {
        assertEquals("liftlog-program-upper-lower.json", ProgramCodec.fileName("Upper / Lower"))
        assertEquals("liftlog-program-plan.json", ProgramCodec.fileName("../../"))
    }
}
