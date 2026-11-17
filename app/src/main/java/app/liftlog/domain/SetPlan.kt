package app.liftlog.domain

/** A set as it was done last time, or as it is laid out for this time. */
data class PlannedSet(val isWarmup: Boolean, val weightKg: Double?)

/**
 * Lays out the sets for an exercise at the start of a workout, with last time's weights
 * already filled in so only changes need typing.
 *
 * Warm-ups from last time come first, with their weights. Working sets follow:
 * [targetWorkingSets] of them when the program sets a target, otherwise as many as last
 * time, or [defaultSets] for an exercise never done before. Each working set takes the
 * weight of the matching set last time; extra sets beyond last time's take the last weight used.
 */
fun planSets(previous: List<PlannedSet>, targetWorkingSets: Int?, defaultSets: Int = 3): List<PlannedSet> {
    val warmups = previous.filter { it.isWarmup }
    val working = previous.filter { !it.isWarmup }
    val count = targetWorkingSets ?: working.size.takeIf { it > 0 } ?: defaultSets
    return warmups + List(count) { index ->
        PlannedSet(isWarmup = false, weightKg = (working.getOrNull(index) ?: working.lastOrNull())?.weightKg)
    }
}
