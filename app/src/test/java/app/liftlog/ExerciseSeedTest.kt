package app.liftlog

import app.liftlog.data.ExerciseSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the bundled exercise library itself, since it is written by hand. */
class ExerciseSeedTest {
    private val exercises = ExerciseSeed.parse(File("src/main/assets/${ExerciseSeed.ASSET_NAME}").readText())

    @Test
    fun `every line parses and the library is large`() {
        assertTrue("Expected at least 300 exercises, got ${exercises.size}", exercises.size >= 300)
    }

    @Test
    fun `names are unique ignoring case`() {
        val duplicates = exercises.groupBy { it.name.lowercase() }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet<String>(), duplicates)
    }

    @Test
    fun `primary muscle is never repeated as a secondary`() {
        val offenders = exercises.filter { it.primaryMuscle in it.secondaryMuscles }.map { it.name }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `seed entries are not marked custom`() {
        assertFalse(exercises.any { it.isCustom })
    }
}
