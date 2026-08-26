package rs.etf.focusguard.ui.stateholders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rs.etf.focusguard.FocusSessionService
import rs.etf.focusguard.data.SessionEngine
import rs.etf.focusguard.sensors.EnvironmentMonitor
import rs.etf.focusguard.sensors.WarningKind
import javax.inject.Inject

/** A toast currently on screen. The id lets it be dismissed or expired individually. */
data class ActiveToast(val id: Long, val kind: WarningKind)

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionEngine: SessionEngine,
    environmentMonitor: EnvironmentMonitor,
) : ViewModel() {

    val state = sessionEngine.state

    private val _toasts = MutableStateFlow<List<ActiveToast>>(emptyList())
    val toasts = _toasts.asStateFlow()

    /** Which full-screen warning is showing, or null. Its kind decides the wording. */
    private val _bigWarning = MutableStateFlow<WarningKind?>(null)
    val bigWarning = _bigWarning.asStateFlow()

    private var nextToastId = 0L
    private var bigWarningJob: Job? = null

    init {
        viewModelScope.launch {
            environmentMonitor.warnings.collect { kind ->
                // Reaching for the phone is the failure the app exists to prevent, so both
                // ways of saying it interrupt. Everything else advises quietly.
                if (kind == WarningKind.MOVEMENT || kind == WarningKind.FIDGETING) {
                    showBigWarning(kind)
                } else {
                    showToast(kind)
                }
            }
        }
    }

    /**
     * The overlay covers the whole screen and swallows the tap that dismisses it, which is
     * right — a tap meant to clear a warning must not also press End Session underneath.
     * That makes it a trap if it never goes away on its own: during testing it sat there for
     * three minutes and turned the next two taps into the wrong actions entirely. So it now
     * clears itself, and a fresh warning restarts the clock rather than stacking.
     */
    private fun showBigWarning(kind: WarningKind) {
        _bigWarning.value = kind
        bigWarningJob?.cancel()
        bigWarningJob = viewModelScope.launch {
            delay(BIG_WARNING_VISIBLE_MILLIS)
            _bigWarning.value = null
        }
    }

    private fun showToast(kind: WarningKind) {
        val toast = ActiveToast(id = nextToastId++, kind = kind)
        _toasts.value = _toasts.value + toast

        viewModelScope.launch {
            delay(TOAST_VISIBLE_MILLIS)
            dismissToast(toast.id)
        }
    }

    fun dismissToast(id: Long) {
        _toasts.value = _toasts.value.filterNot { it.id == id }
    }

    fun dismissBigWarning() {
        bigWarningJob?.cancel()
        bigWarningJob = null
        _bigWarning.value = null
    }

    fun togglePause() = sessionEngine.togglePause()

    /**
     * Ends the session and stops the service. Navigation is deliberately not triggered here:
     * the screen reacts to the engine's state going null, which happens on the main thread.
     */
    fun endSession() = viewModelScope.launch {
        sessionEngine.endSession()
        FocusSessionService.stop(context)
    }

    private companion object {
        const val TOAST_VISIBLE_MILLIS = 4_500L

        /**
         * Long enough to be unmissable and to interrupt the reach for the phone, short enough
         * that an unattended phone is not left behind a wall.
         */
        const val BIG_WARNING_VISIBLE_MILLIS = 12_000L
    }
}
