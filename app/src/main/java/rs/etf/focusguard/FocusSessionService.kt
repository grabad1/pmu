package rs.etf.focusguard

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import rs.etf.focusguard.data.SessionEngine
import rs.etf.focusguard.data.SessionRepository
import rs.etf.focusguard.data.SessionRuntimeState
import rs.etf.focusguard.data.room.PauseType
import rs.etf.focusguard.util.formatHoursMinutesSeconds
import javax.inject.Inject

/**
 * Keeps the process alive for the duration of a session and surfaces its state in a
 * notification. Timing lives in [SessionEngine]; this service only reflects it, so the
 * session is unaffected by the service being restarted.
 */
@AndroidEntryPoint
class FocusSessionService : LifecycleService() {

    companion object {
        const val ACTION_START = "rs.etf.focusguard.session.START"
        const val ACTION_TOGGLE_PAUSE = "rs.etf.focusguard.session.TOGGLE_PAUSE"
        const val ACTION_END = "rs.etf.focusguard.session.END"

        const val EXTRA_SESSION_ID = "sessionId"

        private const val NOTIFICATION_CHANNEL_ID = "focus-session-channel"
        private const val NOTIFICATION_ID = 21

        fun start(context: Context, sessionId: Long) {
            val intent = Intent(context, FocusSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusSessionService::class.java))
        }
    }

    @Inject
    lateinit var sessionEngine: SessionEngine

    private var isObservingState = false

    /**
     * Guards against stopping before the session has been loaded. The engine starts out with
     * no state, so the first emission is null even on a healthy start.
     */
    private var hasSeenSession = false

    override fun onCreate() {
        super.onCreate()
        Log.d(LOG_TAG, "FocusSessionService.onCreate()")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(LOG_TAG, "FocusSessionService.onStartCommand(${intent?.action})")

        startForegroundCompat(buildNotification(sessionEngine.state.value))

        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                if (sessionId <= 0) {
                    Log.w(LOG_TAG, "START without a session id; stopping service")
                    stopSelf()
                } else {
                    sessionEngine.attach(sessionId)
                }
            }

            ACTION_TOGGLE_PAUSE -> sessionEngine.togglePause()

            ACTION_END -> lifecycleScope.launch { sessionEngine.endSession() }

            // Restarted by the system after the process was killed mid-session.
            else -> sessionEngine.attachRunning()
        }

        observeStateOnce()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    /**
     * Mirrors engine state into the notification, and shuts the service down once a session
     * that was actually running has finished, so the notification cannot outlive it.
     */
    private fun observeStateOnce() {
        if (isObservingState) return
        isObservingState = true

        lifecycleScope.launch {
            sessionEngine.state
                .map { it?.toNotificationText() }
                .distinctUntilChanged()
                .collect {
                    val state = sessionEngine.state.value
                    if (state != null) {
                        hasSeenSession = true
                        NotificationManagerCompat.from(this@FocusSessionService)
                            .notify(NOTIFICATION_ID, buildNotification(state))
                    } else if (hasSeenSession) {
                        Log.d(LOG_TAG, "Session finished; stopping FocusSessionService")
                        stopForegroundAndSelf()
                    }
                }
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat
            .Builder(NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.session_notification_channel_name))
            .setDescription(getString(R.string.session_notification_channel_description))
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun buildNotification(state: SessionRuntimeState?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, FocusGuardActivity::class.java)
                .apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_session_notification)
            .setContentTitle(state?.name ?: getString(R.string.app_name))
            .setContentText(state?.toNotificationText() ?: getString(R.string.session_starting))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColorized(true)
            .setColor(ContextCompat.getColor(this, R.color.accent))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (state != null) {
            builder.addAction(
                0,
                getString(if (state.isPaused) R.string.action_resume else R.string.action_pause),
                servicePendingIntent(ACTION_TOGGLE_PAUSE, requestCode = 1),
            )
            builder.addAction(
                0,
                getString(R.string.action_end_session),
                servicePendingIntent(ACTION_END, requestCode = 2),
            )
        }

        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, FocusSessionService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun SessionRuntimeState.toNotificationText(): String {
        val elapsed = formatHoursMinutesSeconds(focusedSeconds)
        return when {
            activePauseType == PauseType.PLANNED ->
                getString(R.string.session_notification_planned_pause, elapsed)

            activePauseType == PauseType.UNPLANNED ->
                getString(R.string.session_notification_unplanned_pause, elapsed)

            isPastGoal -> getString(R.string.session_notification_past_goal, elapsed)

            else -> getString(R.string.session_notification_focusing, elapsed)
        }
    }
}
