package rs.etf.focusguard.ui.stateholders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import rs.etf.focusguard.FocusSessionService
import rs.etf.focusguard.data.SessionEngine
import javax.inject.Inject

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionEngine: SessionEngine,
) : ViewModel() {

    val state = sessionEngine.state

    fun togglePause() = sessionEngine.togglePause()

    fun endSession(onEnded: () -> Unit) = viewModelScope.launch {
        sessionEngine.endSession { onEnded() }
        FocusSessionService.stop(context)
    }
}
