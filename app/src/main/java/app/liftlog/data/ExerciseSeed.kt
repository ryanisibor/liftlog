package app.liftlog.data

import app.liftlog.data.db.Exercise

/**
 * Parser for the bundled exercise library (`assets/exercises.txt`).
 *
 * One exercise per line: `Name|PRIMARY_MUSCLE|SECONDARY,SECONDARY|EQUIPMENT`.
 * Blank lines and lines starting with `#` are ignored.
 */
object ExerciseSeed {
    /** Raise this whenever exercises.txt gains entries, so existing installs pick them up. */
    const val VERSION = 1

    const val ASSET_NAME = "exercises.txt"

    fun parse(text: String): List<Exercise> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map(::parseLine)
            .toList()

    private fun parseLine(line: String): Exercise {
        val parts = line.split("|")
        require(parts.size == 4) { "Expected 4 fields in seed line: $line" }
        val (name, primary, secondary, equipment) = parts.map { it.trim() }
        require(name.isNotEmpty()) { "Missing name in seed line: $line" }
        return Exercise(
            name = name,
            primaryMuscle = Muscle.valueOf(primary),
            secondaryMuscles = secondary.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { Muscle.valueOf(it) },
            equipment = Equipment.valueOf(equipment),
        )
    }
}
