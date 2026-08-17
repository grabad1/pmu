package rs.etf.focusguard.alarms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rs.etf.focusguard.FocusGuardActivity
import rs.etf.focusguard.LOG_TAG
import rs.etf.focusguard.R
import rs.etf.focusguard.data.SessionRepository
import rs.etf.focusguard.data.room.SessionStatus
import javax.inject.Inject

/**
 * Turns a booked alarm into a notification.
 *
 * The session is re-read from the database rather than carried in the intent, so a reminder
 * for a session that has since been cancelled, started or rescheduled is simply dropped.
 */
@AndroidEntryPoint
class SessionAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SESSION_REMINDER = "rs.etf.focusguard.alarm.SESSION_REMINDER"
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_ALARM_KIND = "alarmKind"

        const val NOTIFICATION_CHANNEL_ID = "session-reminders-channel"

        /** Distinct per session and reminder, so the three never overwrite one another. */
        private fun notificationId(sessionId: Long, kind: SessionAlarmKind): Int =
            (sessionId * 10 + kind.ordinal).toInt()
    }

    @Inject
    lateinit var sessionRepository: SessionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SESSION_REMINDER) return

        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        val kind = intent.getStringExtra(EXTRA_ALARM_KIND)
            ?.let { runCatching { SessionAlarmKind.valueOf(it) }.getOrNull() }
            ?: return

        // The receiver returns immediately, so the work is held open explicitly.
        val pendingResult = goAsync()
        scope.launch {
            try {
                val session = sessionRepository.getSession(sessionId)
                if (session == null || session.status != SessionStatus.SCHEDULED) {
                    Log.d(LOG_TAG, "Reminder $kind dropped; session $sessionId is not scheduled")
                    return@launch
                }
                notify(context, session.name, kind, notificationId(sessionId, kind))
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun notify(context: Context, name: String, kind: SessionAlarmKind, id: Int) {
        createChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, FocusGuardActivity::class.java)
                .apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val text = when (kind) {
            SessionAlarmKind.ONE_HOUR_BEFORE -> context.getString(R.string.reminder_one_hour, name)
            SessionAlarmKind.FIVE_MINUTES_BEFORE ->
                context.getString(R.string.reminder_five_minutes, name)

            SessionAlarmKind.AT_START -> context.getString(R.string.reminder_starting_now, name)
        }

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_session_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).let { manager ->
            if (manager.areNotificationsEnabled()) manager.notify(id, notification)
            else Log.w(LOG_TAG, "Notifications disabled; reminder $kind not shown")
        }
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannelCompat
            .Builder(NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.reminder_channel_name))
            .setDescription(context.getString(R.string.reminder_channel_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }
}
