package rs.etf.focusguard.ui.stateholders

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.parcelize.Parcelize
import rs.etf.focusguard.data.SessionRepository
import javax.inject.Inject

/** Which detail modal, if any, is showing — and for which session. */
@Parcelize
data class PreviousSessionsUiState(
    val pauseLogSessionId: Long? = null,
    val analysisSessionId: Long? = null,
) : Parcelable

@HiltViewModel
class PreviousSessionsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    sessionRepository: SessionRepository,
) : ViewModel() {

    companion object {
        private const val UI_STATE_KEY = "uiState"
    }

    val uiState = savedStateHandle.getStateFlow(UI_STATE_KEY, PreviousSessionsUiState())

    val sessions = sessionRepository.completedSessionsWithPauses

    fun showPauseLog(sessionId: Long) {
        savedStateHandle[UI_STATE_KEY] = uiState.value.copy(pauseLogSessionId = sessionId)
    }

    fun showAnalysis(sessionId: Long) {
        savedStateHandle[UI_STATE_KEY] = uiState.value.copy(analysisSessionId = sessionId)
    }

    fun dismissDialogs() {
        savedStateHandle[UI_STATE_KEY] =
            uiState.value.copy(pauseLogSessionId = null, analysisSessionId = null)
    }
}
