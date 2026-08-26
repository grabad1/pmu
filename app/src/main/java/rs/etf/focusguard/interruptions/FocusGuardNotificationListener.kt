package rs.etf.focusguard.interruptions

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import rs.etf.focusguard.data.InterruptionRecorder
import javax.inject.Inject

/**
 * Watches notifications from other apps while a session is running, so the user can be told
 * afterwards which app kept interrupting them.
 *
 * Only the source app and the moment are kept — never titles or message text. The point is
 * to be able to say "Instagram interrupted you six times", which needs nothing more, and
 * storing message contents would be a far larger privacy cost for no gain.
 *
 * The system binds and unbinds this service on its own schedule, so it must not assume it is
 * running only during a session: [InterruptionRecorder] makes that decision.
 */
@AndroidEntryPoint
class FocusGuardNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var recorder: InterruptionRecorder

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isRealInterruption(sbn)) return
        recorder.recordNotification(packageName = sbn.packageName, notificationKey = sbn.key)
    }

    /**
     * Filters out the notifications that never actually disturbed anyone.
     *
     * Ongoing notifications are the persistent kind — a music player, a navigation route,
     * another app's foreground service — which sit in the shade without ever announcing
     * themselves. Group summaries would double-count a conversation that already posted its
     * own notification.
     */
    private fun isRealInterruption(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val flags = notification.flags

        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
        return sbn.isClearable
    }

    companion object {

        /** Whether the user has granted notification access in Settings. */
        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)

        /**
         * Notification access cannot be requested with a permission dialog — it is a special
         * access the user has to switch on themselves, so all the app can do is take them to
         * the right screen.
         */
        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
