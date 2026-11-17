package app.liftlog

import app.liftlog.data.CsvExport
import app.liftlog.data.Equipment
import app.liftlog.data.Muscle
import app.liftlog.data.SetExportRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class CsvExportTest {
    private val zone = ZoneOffset.UTC
    private val start = LocalDateTime.parse("2026-11-16T18:05:00").toInstant(zone).toEpochMilli()

    private fun row(
        exercise: String = "Barbell Bench Press",
        dayName: String? = "Upper Body A",
        weightKg: Double? = 80.0,
        reps: Int? = 6,
        isWarmup: Boolean = false,
    ) = SetExportRow(start, dayName, exercise, Muscle.CHEST, Equipment.BARBELL, 0, 1, isWarmup, weightKg, reps)

    @Test
    fun `writes a header and one row per set`() {
        val lines = CsvExport.build(listOf(row(), row(weightKg = 82.5, reps = 5)), zone).trim().lines()
        assertEquals(3, lines.size)
        assertEquals(
            "date,time,workout,exercise,muscle,equipment,exercise_order,set,warmup,weight_kg,reps,volume_kg,est_1rm_kg",
            lines[0],
        )
        assertEquals("2026-11-16,18:05,Upper Body A,Barbell Bench Press,Chest,Barbell,1,1,no,80,6,480,96", lines[1])
        assertEquals("2026-11-16,18:05,Upper Body A,Barbell Bench Press,Chest,Barbell,1,1,no,82.5,5,412.5,96.25", lines[2])
    }

    @Test
    fun `bodyweight and ad-hoc rows leave weight columns empty`() {
        val line = CsvExport.build(listOf(row(dayName = null, weightKg = null, reps = 12, isWarmup = true)), zone).trim().lines()[1]
        assertEquals("2026-11-16,18:05,Workout,Barbell Bench Press,Chest,Barbell,1,1,yes,,12,,", line)
    }

    @Test
    fun `quotes commas and doubles quotes`() {
        assertEquals("\"Press, incline\"", CsvExport.escape("Press, incline"))
        assertEquals("\"My \"\"odd\"\" lift\"", CsvExport.escape("My \"odd\" lift"))
        assertEquals("Plain", CsvExport.escape("Plain"))
    }

    @Test
    fun `defuses names that a spreadsheet would run as formulas`() {
        assertEquals("'=SUM(A1:A9)", CsvExport.escape("=SUM(A1:A9)"))
        assertEquals("'+cmd", CsvExport.escape("+cmd"))
        assertEquals("'-1+1", CsvExport.escape("-1+1"))
        assertEquals("'@name", CsvExport.escape("@name"))
        assertEquals("\"'=HYPERLINK(\"\"x\"\",\"\"y\"\")\"", CsvExport.escape("=HYPERLINK(\"x\",\"y\")"))
    }
}
