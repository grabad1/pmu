package rs.etf.focusguard.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import rs.etf.focusguard.LOG_TAG
import rs.etf.focusguard.data.room.Session
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Books the reminders for scheduled sessions.
 *
 * `AlarmManager` rather than `WorkManager` because the reminders are only useful if they are
 * punctual: WorkManager is deliberately inexact and Doze can defer it by many minutes, which
 * would make "five minutes before" meaningless.
 */
@Singleton
class SessionAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * True when the system will honour exact timing. When it will not, reminders still fire
     * but may drift, so callers can tell the user rather than leaving them puzzled.
     */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Books every reminder for [session] whose moment is still in the future. */
    fun schedule(session: Session) {
        val startsAt = session.scheduledAt ?: return
        val now = Instant.now()

        SessionAlarmKind.entries.forEach { kind ->
            val fireAt = startsAt.minus(kind.leadTime)
            if (fireAt.isBefore(now)) return@forEach
            scheduleOne(session.id, kind, fireAt)
        }
    }

    fun cancel(session: Session) = cancel(session.id)

    fun cancel(sessionId: Long) {
        SessionAlarmKind.entries.forEach { kind ->
            alarmManager.cancel(pendingIntent(sessionId, kind, mutable = false))
        }
        Log.d(LOG_TAG, "Cancelled alarms for session $sessionId")
    }

    private fun scheduleOne(sessionId: Long, kind: SessionAlarmKind, fireAt: Instant) {
        val pendingIntent = pendingIntent(sessionId, kind, mutable = false)
        val triggerAtMillis = fireAt.toEpochMilli()

        // setExactAndAllowWhileIdle is the only variant that survives Doze, which is exactly
        // the state a phone is in when someone has left it alone to focus later.
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            // Better a late reminder than none; the user is told exact alarms are off.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
        Log.d(LOG_TAG, "Scheduled $kind for session $sessionId at $fireAt")
    }

    private fun pendingIntent(
        sessionId: Long,
        kind: SessionAlarmKind,
        mutable: Boolean,
    ): PendingIntent {
        val intent = Intent(context, SessionAlarmReceiver::class.java).apply {
            action = SessionAlarmReceiver.ACTION_SESSION_REMINDER
            putExtra(SessionAlarmReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(SessionAlarmReceiver.EXTRA_ALARM_KIND, kind.name)
            // The extras are not part of a PendingIntent's identity, so without a unique URI
            // all three reminders would collapse into one.
            data = "focusguard://session/$sessionId/${kind.name}".toUri()
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}
