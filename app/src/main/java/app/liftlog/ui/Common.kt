package app.liftlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.liftlog.AppContainer
import app.liftlog.LiftLogApplication
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as LiftLogApplication).container

/** "1 set", "3 sets": a count with its noun, pluralised by adding an s. */
fun countOf(count: Int, singular: String): String = if (count == 1) "1 $singular" else "$count ${singular}s"

private val dateTimeFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

fun formatDateTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateTimeFormat)

fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateFormat)

/** "1:05:09" or "5:09". */
fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "1 h 5 min" or "45 min", for finished workouts. */
fun formatDurationCoarse(totalSeconds: Long): String {
    val minutes = (totalSeconds.coerceAtLeast(0) + 30) / 60
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}
