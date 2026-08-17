package rs.etf.focusguard.ui.stateholders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

    private val _showBigWarning = MutableStateFlow(false)
    val showBigWarning = _showBigWarning.asStateFlow()

    private var nextToastId = 0L

    init {
        viewModelScope.launch {
            environmentMonitor.warnings.collect { kind ->
                if (kind == WarningKind.MOVEMENT) {
                    _showBigWarning.value = true
                } else {
                    showToast(kind)
                }
            }
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
        _showBigWarning.value = false
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
    }
}
