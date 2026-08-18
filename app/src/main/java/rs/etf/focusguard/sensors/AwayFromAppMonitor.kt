package rs.etf.focusguard.sensors

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rs.etf.focusguard.FocusGuardActivity
import rs.etf.focusguard.FocusSessionService
import rs.etf.focusguard.LOG_TAG
import rs.etf.focusguard.R
import rs.etf.focusguard.data.SessionEngine
import rs.etf.focusguard.util.formatMinutesSeconds
import javax.inject.Inject

/**
 * Notices when the user leaves the app mid-session, and says something about it.
 *
 * The full-screen "Stop Using The Phone!" warning is the right answer only while Focus Guard
 * is the screen in front of the user. The moment they open something else it is hidden behind
 * that app, so the one distraction the app exists to prevent became the one it could not
 * mention. This is the part that reaches them anyway: a buzz and a heads-up notification.
 *
 * Attached to the session service's lifecycle like the sensor monitors, so it exists exactly
 * as long as a session does.
 */
class AwayFromAppMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionEngine: SessionEngine,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** Seconds of absence at which the last nudge was sent, so each one fires once. */
    private var lastNudgeAtSeconds = 0

    override fun onCreate(owner: LifecycleOwner) {
        createChannel()
        job = scope.launch {
            while (isActive) {
                delay(CHECK_EVERY_MILLIS)
                check()
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        job?.cancel()
        job = null
        clearNotification()
    }

    private fun check() {
        val state = sessionEngine.state.value

        // Back in the app, on a break, or no session: nothing to say.
        if (state == null || !state.isAway) {
            if (lastNudgeAtSeconds != 0) {
                lastNudgeAtSeconds = 0
                clearNotification()
            }
            return
        }

        val away = state.awaySeconds
        val due = when {
            lastNudgeAtSeconds == 0 -> away >= FIRST_NUDGE_SECONDS
            else -> away - lastNudgeAtSeconds >= REPEAT_EVERY_SECONDS
        }
        if (!due) return

        lastNudgeAtSeconds = away
        // Each reminder is a little more insistent than the last.
        val escalated = away >= FIRST_NUDGE_SECONDS + REPEAT_EVERY_SECONDS
        notify(state.name, away, escalated)
        vibrate(escalated)
        Log.d(LOG_TAG, "AwayFromAppMonitor nudge after ${away}s away")
    }

    private fun notify(sessionName: String, awaySeconds: Int, escalated: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, FocusGuardActivity::class.java)
                .apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_session_notification)
            .setContentTitle(
                context.getString(
                    if (escalated) R.string.away_title_escalated else R.string.away_title
                )
            )
            .setContentText(
                context.getString(
                    R.string.away_text,
                    sessionName,
                    formatMinutesSeconds(awaySeconds),
                )
            )
            .setContentIntent(openApp)
            // The same two controls the session notification offers, because the honest
            // answer to "you have been away four minutes" is sometimes "yes, I know — take a
            // break" or "yes, I am done". Making them come back just to say so is a nag.
            //
            // Pausing stops time away accruing, so this notification clears itself a moment
            // later; ending stops the service, which does the same.
            .addAction(
                0,
                context.getString(R.string.action_pause),
                serviceAction(FocusSessionService.ACTION_TOGGLE_PAUSE, PAUSE_REQUEST_CODE),
            )
            .addAction(
                0,
                context.getString(R.string.action_end_session),
                serviceAction(FocusSessionService.ACTION_END, END_REQUEST_CODE),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Deliberately not dismissible by a swipe: the point is that it stays in front of
            // them until they come back, which is the only thing that clears it.
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, FocusSessionService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun clearNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun vibrate(escalated: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return

        val pattern = if (escalated) ESCALATED_PATTERN else FIRST_PATTERN
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    /**
     * Its own channel, at high importance, so it can raise a heads-up banner over whatever
     * the user has opened. The session's own notification stays on the quiet channel — a
     * timer that buzzes every minute would be unbearable.
     */
    private fun createChannel() {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.away_channel_name))
            .setDescription(context.getString(R.string.away_channel_description))
            .setVibrationEnabled(true)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "focus_guard_away"
        const val NOTIFICATION_ID = 2

        // Distinct from the session notification's own codes, so the two sets of buttons
        // cannot collapse into one another.
        const val REQUEST_CODE = 3
        const val PAUSE_REQUEST_CODE = 4
        const val END_REQUEST_CODE = 5

        const val CHECK_EVERY_MILLIS = 1_000L

        /** Long enough to ignore a glance at a notification, short enough to still matter. */
        const val FIRST_NUDGE_SECONDS = 15

        const val REPEAT_EVERY_SECONDS = 45

        /** Two short taps. */
        val FIRST_PATTERN = longArrayOf(0, 220, 140, 220)

        /** Longer, and one more of them. */
        val ESCALATED_PATTERN = longArrayOf(0, 420, 160, 420, 160, 420)
    }
}
