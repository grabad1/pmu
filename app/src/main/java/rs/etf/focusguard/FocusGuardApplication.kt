package rs.etf.focusguard

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rs.etf.focusguard.data.SessionEngine
import rs.etf.focusguard.data.SessionRepository
import javax.inject.Inject

const val LOG_TAG = "FocusGuard"

@HiltAndroidApp
class FocusGuardApplication : Application() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var sessionEngine: SessionEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        resumeInterruptedSession()
        rateSessionsLeftUnscored()
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

    /**
     * The same process death can also strand a session that *did* end: rating happens after
     * the session is stored, so a few seconds of bad luck used to leave it unscored for ever.
     * Starting the app is the natural moment to go back for those.
     */
    private fun rateSessionsLeftUnscored() {
        scope.launch { sessionEngine.rateOutstandingSessions() }
    }
}
