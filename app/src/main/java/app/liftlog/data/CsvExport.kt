package app.liftlog.data

import app.liftlog.domain.estimateOneRepMax
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One completed set with everything a spreadsheet row needs. */
data class SetExportRow(
    val startedAt: Long,
    val dayName: String?,
    val exercise: String,
    val primaryMuscle: Muscle,
    val equipment: Equipment,
    val exercisePosition: Int,
    val setNumber: Int,
    val isWarmup: Boolean,
    val weightKg: Double?,
    val reps: Int?,
)

/**
 * Builds the spreadsheet export: one row per completed set, oldest first, as CSV that
 * Excel and Google Sheets open directly. Weights are always in kg so columns can be summed.
 */
object CsvExport {
    private val header = listOf(
        "date", "time", "workout", "exercise", "muscle", "equipment",
        "exercise_order", "set", "warmup", "weight_kg", "reps", "volume_kg", "est_1rm_kg",
    )
    private val date = DateTimeFormatter.ISO_LOCAL_DATE
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    fun build(rows: List<SetExportRow>, zone: ZoneId): String = buildString {
        appendLine(header.joinToString(","))
        for (row in rows) {
            val started = Instant.ofEpochMilli(row.startedAt).atZone(zone)
            val weight = row.weightKg
            val reps = row.reps
            val fields = listOf(
                started.format(date),
                started.format(time),
                row.dayName ?: "Workout",
                row.exercise,
                row.primaryMuscle.label,
                row.equipment.label,
                (row.exercisePosition + 1).toString(),
                row.setNumber.toString(),
                if (row.isWarmup) "yes" else "no",
                weight?.let(::number) ?: "",
                reps?.toString() ?: "",
                if (weight != null && reps != null) number(weight * reps) else "",
                if (weight != null && reps != null) estimateOneRepMax(weight, reps)?.let(::number) ?: "" else "",
            )
            appendLine(fields.joinToString(",", transform = ::escape))
        }
    }

    private fun number(value: Double): String =
        BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
            .let { if (it.scale() < 0) it.setScale(0) else it }
            .toPlainString()

    /**
     * Quotes a field when needed, and defuses spreadsheet formulas: a name the user typed
     * that starts with =, +, - or @ would otherwise be run as a formula when the file is opened.
     */
    internal fun escape(field: String): String {
        val safe = if (field.isNotEmpty() && field[0] in FORMULA_STARTS) "'$field" else field
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private const val FORMULA_STARTS = "=+-@\t\r"
}
