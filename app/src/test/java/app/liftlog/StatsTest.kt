package app.liftlog

import app.liftlog.domain.LoggedSet
import app.liftlog.domain.WorkoutTotals
import app.liftlog.domain.monthStats
import app.liftlog.domain.estimateOneRepMax
import app.liftlog.domain.personalRecords
import app.liftlog.domain.sessionStats
import app.liftlog.domain.weeklyCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsTest {
    @Test
    fun `one rep max follows Epley and is exact for a single`() {
        assertEquals(100.0, estimateOneRepMax(100.0, 1)!!, 1e-9)
        assertEquals(100.0 * (1 + 10 / 30.0), estimateOneRepMax(100.0, 10)!!, 1e-9)
    }

    @Test
    fun `one rep max is not estimated from high reps or nonsense input`() {
        assertNull(estimateOneRepMax(50.0, 13))
        assertNull(estimateOneRepMax(50.0, 0))
        assertNull(estimateOneRepMax(0.0, 5))
    }

    @Test
    fun `session stats summarise weighted sets`() {
        val stats = sessionStats(
            startedAt = 1L,
            sets = listOf(LoggedSet(60.0, 10), LoggedSet(80.0, 5), LoggedSet(80.0, 6), LoggedSet(40.0, 20)),
        )
        assertEquals(80.0, stats.topWeightKg!!, 1e-9)
        assertEquals(6, stats.topWeightReps)
        assertEquals(80.0 * (1 + 6 / 30.0), stats.bestOneRepMaxKg!!, 1e-9)
        assertEquals(60.0 * 10 + 80.0 * 5 + 80.0 * 6 + 40.0 * 20, stats.volumeKg, 1e-9)
        assertEquals(20, stats.topReps)
    }

    @Test
    fun `a bodyweight session has reps but no weight figures`() {
        val stats = sessionStats(1L, listOf(LoggedSet(null, 12), LoggedSet(null, 9), LoggedSet(20.0, null)))
        assertNull(stats.topWeightKg)
        assertNull(stats.bestOneRepMaxKg)
        assertEquals(0.0, stats.volumeKg, 1e-9)
        assertEquals(12, stats.topReps)
    }

    @Test
    fun `records keep the first date a best was reached`() {
        val sessions = listOf(
            sessionStats(100L, listOf(LoggedSet(80.0, 6))),
            sessionStats(200L, listOf(LoggedSet(85.0, 3), LoggedSet(60.0, 12))),
            sessionStats(300L, listOf(LoggedSet(85.0, 3))),
        )
        val records = personalRecords(sessions)
        assertEquals(85.0, records.heaviest!!.value, 1e-9)
        assertEquals(200L, records.heaviest!!.achievedAt)
        assertEquals(3, records.heaviest!!.reps)
        assertEquals(200L, records.bestVolume!!.achievedAt)
        assertEquals(12.0, records.mostReps!!.value, 1e-9)
        // 80 x 6 estimates to 96, higher than 85 x 3 at 93.5, so the 1RM record stays with the first session.
        assertEquals(100L, records.bestOneRepMax!!.achievedAt)
    }

    @Test
    fun `month stats total and average the workouts`() {
        val stats = monthStats(
            workouts = listOf(WorkoutTotals(3600, 10_000.0), WorkoutTotals(5400, 14_000.0)),
            daysCounted = 28,
        )
        assertEquals(2, stats.workouts)
        assertEquals(0.5, stats.perWeek, 1e-9)
        assertEquals(9000L, stats.totalSeconds)
        assertEquals(4500L, stats.averageSeconds)
        assertEquals(24_000.0, stats.totalVolumeKg, 1e-9)
        assertEquals(12_000.0, stats.averageVolumeKg, 1e-9)
    }

    @Test
    fun `an empty month has zero averages rather than dividing by zero`() {
        val stats = monthStats(emptyList(), daysCounted = 0)
        assertEquals(0, stats.workouts)
        assertEquals(0.0, stats.perWeek, 1e-9)
        assertEquals(0L, stats.averageSeconds)
        assertEquals(0.0, stats.averageVolumeKg, 1e-9)
    }

    @Test
    fun `no sessions means no records`() {
        val records = personalRecords(emptyList())
        assertNull(records.heaviest)
        assertNull(records.mostReps)
    }

    @Test
    fun `weekly counts cover empty weeks and ignore older workouts`() {
        val zone = ZoneOffset.UTC
        fun at(date: String) = LocalDate.parse(date).atTime(18, 0).toInstant(zone).toEpochMilli()
        // Tuesday 17 Nov 2026; its week starts Monday 16 Nov.
        val counts = weeklyCounts(
            startTimes = listOf(at("2026-11-16"), at("2026-11-17"), at("2026-11-08"), at("2026-11-02"), at("2026-09-01")),
            weeks = 4,
            today = LocalDate.parse("2026-11-17"),
            zone = zone,
        )
        assertEquals(listOf("2026-10-26", "2026-11-02", "2026-11-09", "2026-11-16"), counts.map { it.weekStart.toString() })
        // Sunday 8 Nov belongs to the week starting 2 Nov.
        assertEquals(listOf(0, 2, 0, 2), counts.map { it.count })
    }
}
