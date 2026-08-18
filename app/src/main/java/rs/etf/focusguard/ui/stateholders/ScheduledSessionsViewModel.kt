package rs.etf.focusguard.ui.stateholders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rs.etf.focusguard.alarms.SessionAlarmScheduler
import rs.etf.focusguard.data.SessionRepository
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.util.isMissed
import java.time.Instant
import javax.inject.Inject

/** A scheduled session plus whether its moment has already passed. */
data class ScheduledSessionItem(val session: Session, val isMissed: Boolean)

@HiltViewModel
class ScheduledSessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val alarmScheduler: SessionAlarmScheduler,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * Ticked rather than read once, so a session that becomes missed while the screen is open
     * moves without the user having to leave and come back.
     */
    private val clock = flow {
        while (true) {
            emit(Instant.now())
            delay(CLOCK_TICK_MILLIS)
        }
    }

    /**
     * Upcoming sessions first, soonest first; missed ones after them, most recent first.
     *
     * Sorting purely by time put a session missed last week above one starting in an hour,
     * which is backwards for a list whose job is to say what is coming up.
     */
    val scheduledSessions = combine(
        sessionRepository.scheduledSessions,
        clock,
    ) { sessions, now ->
        sessions
            .map { ScheduledSessionItem(it, it.isMissed(now)) }
            .sortedWith(
                compareBy<ScheduledSessionItem> { it.isMissed }
                    .thenBy { item ->
                        val millis = (item.session.scheduledAt ?: Instant.EPOCH).toEpochMilli()
                        if (item.isMissed) -millis else millis
                    }
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The session whose detail dialog is open, if any. Survives rotation. */
    val selectedSessionId = savedStateHandle.getStateFlow<Long?>(SELECTED_KEY, null)

    fun select(sessionId: Long) {
        savedStateHandle[SELECTED_KEY] = sessionId
    }

    fun dismissDialog() {
        savedStateHandle[SELECTED_KEY] = null
    }

    /**
     * Removes a scheduled session and its reminders. Alarms outlive the row that created
     * them, so cancelling them explicitly is what stops a deleted session from still ringing.
     */
    fun cancel(session: Session) = viewModelScope.launch {
        alarmScheduler.cancel(session)
        sessionRepository.deleteSession(session)
        dismissDialog()
    }

    private companion object {
        const val SELECTED_KEY = "selectedScheduledSession"
        const val CLOCK_TICK_MILLIS = 30_000L
    }
}
