package app.liftlog

import app.liftlog.ui.home.nextDayIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class NextDayTest {
    private val week = listOf(10L, 20L, 30L, 40L, 50L)

    @Test
    fun `starts at the first day when nothing has been trained`() {
        assertEquals(0, nextDayIndex(week, null))
    }

    @Test
    fun `moves to the day after the last one trained`() {
        assertEquals(1, nextDayIndex(week, 10L))
        assertEquals(4, nextDayIndex(week, 40L))
    }

    @Test
    fun `wraps from the last day back to the first`() {
        assertEquals(0, nextDayIndex(week, 50L))
    }

    @Test
    fun `falls back to the first day when the last trained day was deleted`() {
        assertEquals(0, nextDayIndex(week, 999L))
    }

    @Test
    fun `copes with an empty program and a single-day program`() {
        assertEquals(0, nextDayIndex(emptyList(), 10L))
        assertEquals(0, nextDayIndex(listOf(10L), 10L))
    }
}
