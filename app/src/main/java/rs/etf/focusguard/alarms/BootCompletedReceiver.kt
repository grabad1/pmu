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
 * Re-books reminders after a reboot.
 *
 * Android drops every alarm when the device restarts, so without this a session scheduled
 * before a reboot would silently never remind anyone.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var alarmScheduler: SessionAlarmScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val scheduled = sessionRepository.scheduledSessions.first()
                scheduled.forEach(alarmScheduler::schedule)
                Log.d(LOG_TAG, "Re-booked reminders for ${scheduled.size} sessions after boot")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
