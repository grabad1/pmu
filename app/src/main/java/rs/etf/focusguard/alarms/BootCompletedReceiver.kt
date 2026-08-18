package rs.etf.focusguard.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import rs.etf.focusguard.LOG_TAG
import rs.etf.focusguard.data.SessionRepository
import javax.inject.Inject

/**
 * Re-books reminders whenever Android has thrown its alarms away.
 *
 * Three things do that, and all of them arrive here:
 *
 * - **A reboot.** Android drops every alarm when the device restarts.
 * - **A force-stop.** From Android 15 the system cancels a stopped app's alarms and pending
 *   intents outright, and then re-sends `ACTION_BOOT_COMPLETED` the next time the user opens
 *   the app. So a boot broadcast without a boot is not a bug — it is the only notice the app
 *   gets that its reminders no longer exist, and re-booking them is exactly the right answer.
 * - **An update.** Replacing the package cancels its alarms too.
 *
 * Re-booking is idempotent: each reminder's [android.app.PendingIntent] has a stable identity,
 * so scheduling one that already exists replaces it rather than duplicating it.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var alarmScheduler: SessionAlarmScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                val scheduled = sessionRepository.scheduledSessions.first()
                scheduled.forEach(alarmScheduler::schedule)
                Log.d(LOG_TAG, "Re-booked reminders for ${scheduled.size} sessions after $action")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
