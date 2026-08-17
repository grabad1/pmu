package rs.etf.focusguard.ui.stateholders

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import rs.etf.focusguard.data.SessionEngine
import javax.inject.Inject

/**
 * App-level state. Exists so navigation can send the user straight back to a session that is
 * still running — otherwise reopening the app during a session lands on Home with the timer
 * reachable only through the notification.
 */
@HiltViewModel
class FocusGuardAppViewModel @Inject constructor(
    sessionEngine: SessionEngine,
) : ViewModel() {

    val runningSession = sessionEngine.state
}
