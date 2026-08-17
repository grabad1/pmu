package rs.etf.focusguard.ui.stateholders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import rs.etf.focusguard.data.SessionRepository
import rs.etf.focusguard.data.room.Session
import javax.inject.Inject

@HiltViewModel
class ScheduledSessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val scheduledSessions = sessionRepository.scheduledSessions

    fun cancel(session: Session) = viewModelScope.launch {
        sessionRepository.deleteSession(session)
    }
}
