package app.liftlog

import app.liftlog.domain.PlannedSet
import app.liftlog.domain.planSets
import org.junit.Assert.assertEquals
import org.junit.Test

class SetPlanTest {
    private fun warmup(kg: Double?) = PlannedSet(true, kg)
    private fun work(kg: Double?) = PlannedSet(false, kg)

    @Test
    fun `a new exercise gets empty default sets`() {
        assertEquals(listOf(work(null), work(null), work(null)), planSets(emptyList(), targetWorkingSets = null))
    }

    @Test
    fun `repeats last time's layout and weights when there is no target`() {
        val last = listOf(warmup(40.0), work(80.0), work(82.5))
        assertEquals(last, planSets(last, targetWorkingSets = null))
    }

    @Test
    fun `fills extra target sets with the last weight used`() {
        val last = listOf(work(80.0), work(82.5))
        assertEquals(
            listOf(work(80.0), work(82.5), work(82.5), work(82.5)),
            planSets(last, targetWorkingSets = 4),
        )
    }

    @Test
    fun `drops sets beyond the target but keeps warm-ups`() {
        val last = listOf(warmup(40.0), warmup(60.0), work(80.0), work(80.0), work(75.0))
        assertEquals(
            listOf(warmup(40.0), warmup(60.0), work(80.0), work(80.0)),
            planSets(last, targetWorkingSets = 2),
        )
    }

    @Test
    fun `bodyweight history stays without a weight`() {
        assertEquals(listOf(work(null), work(null)), planSets(listOf(work(null), work(null)), targetWorkingSets = 2))
    }
}
