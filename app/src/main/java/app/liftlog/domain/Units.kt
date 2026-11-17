package app.liftlog.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Weights are stored in kg; this only affects how they are shown and entered. */
/** [step] is the usual smallest plate jump in that unit, used by the quick-adjust buttons. */
enum class WeightUnit(val label: String, val kgPerUnit: Double, val step: Double) {
    KG("kg", 1.0, 2.5),
    LB("lb", 0.45359237, 5.0),
}

/** Converts a stored kg value to [unit] and formats it with up to two decimals, e.g. "62.5". */
fun formatWeight(weightKg: Double, unit: WeightUnit): String =
    BigDecimal(weightKg / unit.kgPerUnit)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .let { if (it.scale() < 0) it.setScale(0) else it }
        .toPlainString()

/**
 * Parses what the user typed, in [unit], into kg. Accepts a comma or a dot as the decimal
 * separator. Returns null for blank, invalid, negative or implausibly large input.
 */
fun parseWeightToKg(text: String, unit: WeightUnit): Double? {
    val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (value.isNaN() || value < 0 || value > MAX_WEIGHT_INPUT) return null
    return value * unit.kgPerUnit
}

/** Parses a rep count. Returns null for blank, invalid or out-of-range input. */
fun parseReps(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 0..MAX_REPS }

/** "60 × 8", or "8 reps" when there is no weight (bodyweight). */
fun formatSet(weightKg: Double?, reps: Int?, unit: WeightUnit): String = when {
    reps == null -> "–"
    weightKg == null -> "$reps reps"
    else -> "${formatWeight(weightKg, unit)} × $reps"
}

/**
 * The weight box's new text after a quick-adjust button adds [delta] (in [unit], may be
 * negative). Starts from what is typed, else from [fallbackKg] (last time's weight), else
 * from zero, and never goes below zero.
 */
fun stepWeight(text: String, fallbackKg: Double?, delta: Double, unit: WeightUnit): String {
    val baseKg = parseWeightToKg(text, unit) ?: fallbackKg ?: 0.0
    val steppedKg = maxOf(0.0, baseKg + delta * unit.kgPerUnit)
    return formatWeight(steppedKg, unit)
}

/**
 * Labels for a list of sets given which are warm-ups: "W" for a warm-up, and working sets
 * numbered among themselves, so two warm-ups then three sets read W, W, 1, 2, 3.
 */
fun setLabels(isWarmup: List<Boolean>): List<String> {
    var working = 0
    return isWarmup.map { warmup -> if (warmup) "W" else (++working).toString() }
}

private const val MAX_WEIGHT_INPUT = 2000.0
private const val MAX_REPS = 1000
