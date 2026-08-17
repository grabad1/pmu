package rs.etf.focusguard.ui.stateholders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import rs.etf.focusguard.alarms.SessionAlarmScheduler
import rs.etf.focusguard.data.SessionRepository
import rs.etf.focusguard.data.room.Session
import javax.inject.Inject

@HiltViewModel
class ScheduledSessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val alarmScheduler: SessionAlarmScheduler,
) : ViewModel() {

    val scheduledSessions = sessionRepository.scheduledSessions

    /**
     * Removes a scheduled session and its reminders. Alarms outlive the row that created
     * them, so cancelling them explicitly is what stops a deleted session from still ringing.
     */
    fun cancel(session: Session) = viewModelScope.launch {
        alarmScheduler.cancel(session)
        sessionRepository.deleteSession(session)
    }
}
