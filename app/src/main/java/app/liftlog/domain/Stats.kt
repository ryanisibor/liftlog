package app.liftlog.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** A completed set, reduced to what the statistics need. */
data class LoggedSet(val weightKg: Double?, val reps: Int?)

/** One session of one exercise, summarised. Weight figures are null for bodyweight-only sessions. */
data class SessionStats(
    val startedAt: Long,
    val topWeightKg: Double?,
    /** Reps done at [topWeightKg]; the most reps if that weight was used more than once. */
    val topWeightReps: Int?,
    val bestOneRepMaxKg: Double?,
    /** Sum of weight × reps over all weighted sets. */
    val volumeKg: Double,
    /** Most reps in any single set. */
    val topReps: Int,
)

/**
 * Estimated one-rep max by the Epley formula: weight × (1 + reps / 30).
 * Returns null above [MAX_REPS_FOR_ESTIMATE] reps, where the estimate stops being meaningful.
 */
fun estimateOneRepMax(weightKg: Double, reps: Int): Double? = when {
    reps < 1 || reps > MAX_REPS_FOR_ESTIMATE || weightKg <= 0 -> null
    reps == 1 -> weightKg
    else -> weightKg * (1 + reps / 30.0)
}

fun sessionStats(startedAt: Long, sets: List<LoggedSet>): SessionStats {
    val counted = sets.filter { (it.reps ?: 0) > 0 }
    val weighted = counted.filter { (it.weightKg ?: 0.0) > 0 }
    val topWeight = weighted.maxOfOrNull { it.weightKg!! }
    return SessionStats(
        startedAt = startedAt,
        topWeightKg = topWeight,
        topWeightReps = weighted.filter { it.weightKg == topWeight }.maxOfOrNull { it.reps!! },
        bestOneRepMaxKg = weighted.mapNotNull { estimateOneRepMax(it.weightKg!!, it.reps!!) }.maxOrNull(),
        volumeKg = weighted.sumOf { it.weightKg!! * it.reps!! },
        topReps = counted.maxOfOrNull { it.reps!! } ?: 0,
    )
}

/** A best-ever figure and when it was first achieved. */
data class Record(val value: Double, val achievedAt: Long, val reps: Int? = null)

data class PersonalRecords(
    val heaviest: Record? = null,
    val bestOneRepMax: Record? = null,
    val bestVolume: Record? = null,
    val mostReps: Record? = null,
)

/** Bests across [sessions], which must be in chronological order so ties keep the earliest date. */
fun personalRecords(sessions: List<SessionStats>): PersonalRecords {
    var heaviest: Record? = null
    var oneRepMax: Record? = null
    var volume: Record? = null
    var reps: Record? = null
    for (session in sessions) {
        session.topWeightKg?.let {
            if (it > (heaviest?.value ?: 0.0)) heaviest = Record(it, session.startedAt, session.topWeightReps)
        }
        session.bestOneRepMaxKg?.let {
            if (it > (oneRepMax?.value ?: 0.0)) oneRepMax = Record(it, session.startedAt)
        }
        if (session.volumeKg > (volume?.value ?: 0.0)) volume = Record(session.volumeKg, session.startedAt)
        if (session.topReps > (reps?.value ?: 0.0)) reps = Record(session.topReps.toDouble(), session.startedAt)
    }
    return PersonalRecords(heaviest, oneRepMax, volume, reps)
}

/** How many workouts started in one Monday-to-Sunday week. */
data class WeekCount(val weekStart: LocalDate, val count: Int)

/**
 * Workouts per week for the [weeks] weeks ending with the one containing [today],
 * oldest first, including weeks with none.
 */
fun weeklyCounts(startTimes: List<Long>, weeks: Int, today: LocalDate, zone: ZoneId): List<WeekCount> {
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val firstWeek = thisWeek.minusWeeks((weeks - 1).toLong())
    val counts = IntArray(weeks)
    for (time in startTimes) {
        val week = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val index = ChronoUnit.WEEKS.between(firstWeek, week).toInt()
        if (index in 0 until weeks) counts[index]++
    }
    return counts.mapIndexed { index, count -> WeekCount(firstWeek.plusWeeks(index.toLong()), count) }
}

private const val MAX_REPS_FOR_ESTIMATE = 12
