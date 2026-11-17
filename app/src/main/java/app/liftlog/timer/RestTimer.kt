package app.liftlog.timer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import app.liftlog.LiftLogApplication
import app.liftlog.MainActivity
import app.liftlog.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Rest countdown between sets. The end time is a wall-clock timestamp and the "rest over"
 * alert is an alarm, so it still fires with the screen off or the app in the background.
 */
class RestTimer(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private val _endsAt = MutableStateFlow<Long?>(null)

    /** Epoch millis when the current rest ends, or null when no rest is running. */
    val endsAt: StateFlow<Long?> = _endsAt

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts you when your rest between sets is over"
                enableVibration(true)
            },
        )
    }

    fun start(seconds: Int) = scheduleFor(System.currentTimeMillis() + seconds * 1000L)

    fun addSeconds(seconds: Int) {
        val current = _endsAt.value ?: return
        scheduleFor(current + seconds * 1000L)
    }

    fun cancel() {
        alarmManager.cancel(alarmIntent())
        notificationManager.cancel(NOTIFICATION_ID)
        _endsAt.value = null
    }

    private fun scheduleFor(endsAt: Long) {
        notificationManager.cancel(NOTIFICATION_ID)
        _endsAt.value = endsAt
        // USE_EXACT_ALARM covers Android 13+. On 12 the user may have revoked exact alarms.
        val canBeExact = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || alarmManager.canScheduleExactAlarms()
        if (canBeExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, alarmIntent())
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, alarmIntent())
        }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RestTimerReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun onAlarmFired() {
        _endsAt.value = null
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rest over")
            .setContentText("Time for your next set")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
            .setContentIntent(openApp)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "rest_timer"
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_TIMEOUT_MS = 60_000L
    }
}

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as LiftLogApplication).container.restTimer.onAlarmFired()
    }
}
