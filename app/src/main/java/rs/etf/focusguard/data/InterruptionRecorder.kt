package rs.etf.focusguard.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rs.etf.focusguard.LOG_TAG
import rs.etf.focusguard.data.room.Interruption
import rs.etf.focusguard.data.room.InterruptionKind
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place that decides whether something counts as an interruption, and records it.
 *
 * Both sources — the notification listener and the call monitor — go through here, so the
 * rules live in one place rather than being duplicated and drifting apart.
 *
 * Nothing is ever recorded when:
 *
 * - **no session is running.** The notification listener is bound by the system and runs
 *   whether or not the app is being used; without this check it would quietly log every
 *   notification the phone ever receives, which is both useless and an unpleasant amount of
 *   data to be keeping.
 * - **the session is paused.** A break is time the user is entitled to spend on their phone,
 *   exactly as with the sensors.
 * - **the notification is our own.** The session timer and the away nudge are notifications
 *   too, so without this Focus Guard would report itself as the user's worst distraction.
 */
@Singleton
class InterruptionRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionEngine: SessionEngine,
    private val repository: SessionRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Notification keys already counted, so an app editing a notification in place — a
     * download progress bar, a music player, a chat updating its unread count — is counted
     * once rather than dozens of times.
     */
    private val countedKeys = mutableSetOf<String>()

    private var keysBelongToSession: Long? = null

    /**
     * The app whose call was recorded last, and when.
     *
     * An incoming call also makes the dialer post a notification, so without this a single
     * call is counted twice — once from telephony and once from the shade — and the result
     * reads "Phone ×2" for one ringing phone.
     */
    private var lastCallPackage: String? = null
    private var lastCallAt: Instant? = null

    fun recordNotification(packageName: String, notificationKey: String) {
        val sessionId = activeSessionId() ?: return
        if (packageName == context.packageName) return
        if (isCallNotification(packageName)) return

        synchronized(countedKeys) {
            if (keysBelongToSession != sessionId) {
                countedKeys.clear()
                keysBelongToSession = sessionId
            }
            if (!countedKeys.add(notificationKey)) return
        }

        store(sessionId, InterruptionKind.NOTIFICATION, packageName)
    }

    fun recordCall(packageName: String) {
        val sessionId = activeSessionId() ?: return
        synchronized(countedKeys) {
            lastCallPackage = packageName
            lastCallAt = Instant.now()
        }
        store(sessionId, InterruptionKind.CALL, packageName)
    }

    /** The dialer's own notification about a call already counted as a call. */
    private fun isCallNotification(packageName: String): Boolean = synchronized(countedKeys) {
        val at = lastCallAt ?: return false
        packageName == lastCallPackage &&
            Duration.between(at, Instant.now()) < CALL_NOTIFICATION_WINDOW
    }

    private fun activeSessionId(): Long? {
        val state = sessionEngine.state.value ?: return null
        return if (state.isPaused) null else state.sessionId
    }

    private fun store(sessionId: Long, kind: InterruptionKind, packageName: String) {
        scope.launch {
            val label = appLabel(packageName)
            repository.insertInterruption(
                Interruption(
                    sessionId = sessionId,
                    kind = kind,
                    packageName = packageName,
                    appLabel = label,
                    occurredAt = Instant.now(),
                )
            )
            Log.d(LOG_TAG, "Interruption during session $sessionId: $kind from $label")
        }
    }

    /**
     * Resolved now rather than when the history is read, so a session still names the app
     * correctly after it has been uninstalled or renamed.
     */
    private fun appLabel(packageName: String): String = runCatching {
        val manager = context.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
    }.getOrElse {
        if (it is PackageManager.NameNotFoundException) packageName else packageName
    }

    private companion object {
        /**
         * How long after a call the dialer's own notification is treated as part of that
         * call rather than a separate interruption. Generous, because the notification can
         * arrive either side of the telephony callback.
         */
        val CALL_NOTIFICATION_WINDOW: Duration = Duration.ofSeconds(60)
    }
}
