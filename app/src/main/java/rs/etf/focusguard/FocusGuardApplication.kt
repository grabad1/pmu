package rs.etf.focusguard

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rs.etf.focusguard.data.SessionRepository
import javax.inject.Inject

const val LOG_TAG = "FocusGuard"

@HiltAndroidApp
class FocusGuardApplication : Application() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        resumeInterruptedSession()
    }

    /**
     * A session left RUNNING means the process died — force-stopped, or killed in the
     * background — without the session being ended. Restarting the service from here keeps
     * restoration in one place, so it cannot race with the UI.
     */
    private fun resumeInterruptedSession() {
        scope.launch {
            val running = sessionRepository.getRunningSession() ?: return@launch
            Log.d(LOG_TAG, "Resuming interrupted session ${running.id}")
            FocusSessionService.start(this@FocusGuardApplication, running.id)
        }
    }
}
