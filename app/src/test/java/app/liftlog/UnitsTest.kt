package app.liftlog

import app.liftlog.domain.WeightUnit
import app.liftlog.domain.formatSet
import app.liftlog.domain.formatWeight
import app.liftlog.domain.parseReps
import app.liftlog.domain.parseWeightToKg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitsTest {
    @Test
    fun `formats kg without trailing zeros`() {
        assertEquals("60", formatWeight(60.0, WeightUnit.KG))
        assertEquals("62.5", formatWeight(62.5, WeightUnit.KG))
        assertEquals("1.25", formatWeight(1.25, WeightUnit.KG))
        assertEquals("100", formatWeight(100.0, WeightUnit.KG))
        assertEquals("0", formatWeight(0.0, WeightUnit.KG))
    }

    @Test
    fun `a weight typed in lb reads back as the same number`() {
        for (typed in listOf("135", "45", "2.5", "225", "317.5", "1000")) {
            val kg = parseWeightToKg(typed, WeightUnit.LB)!!
            assertEquals(typed, formatWeight(kg, WeightUnit.LB))
        }
    }

    @Test
    fun `converts kg to lb for display`() {
        assertEquals("220.46", formatWeight(100.0, WeightUnit.LB))
    }

    @Test
    fun `parses comma or dot decimals`() {
        assertEquals(62.5, parseWeightToKg("62,5", WeightUnit.KG)!!, 1e-9)
        assertEquals(62.5, parseWeightToKg(" 62.5 ", WeightUnit.KG)!!, 1e-9)
    }

    @Test
    fun `rejects invalid weights`() {
        assertNull(parseWeightToKg("", WeightUnit.KG))
        assertNull(parseWeightToKg("abc", WeightUnit.KG))
        assertNull(parseWeightToKg("-5", WeightUnit.KG))
        assertNull(parseWeightToKg("1.2.3", WeightUnit.KG))
        assertNull(parseWeightToKg("99999", WeightUnit.KG))
    }

    @Test
    fun `parses reps`() {
        assertEquals(8, parseReps("8"))
        assertNull(parseReps(""))
        assertNull(parseReps("8.5"))
        assertNull(parseReps("-1"))
        assertNull(parseReps("5000"))
    }

    @Test
    fun `formats a set`() {
        assertEquals("60 × 8", formatSet(60.0, 8, WeightUnit.KG))
        assertEquals("12 reps", formatSet(null, 12, WeightUnit.KG))
        assertEquals("–", formatSet(60.0, null, WeightUnit.KG))
    }
}
