package rs.etf.focusguard.ui.stateholders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rs.etf.focusguard.FocusSessionService
import rs.etf.focusguard.alarms.SessionAlarmScheduler
import rs.etf.focusguard.data.SessionEngine
import rs.etf.focusguard.data.SessionRepository
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.data.room.SessionStatus
import rs.etf.focusguard.util.ScheduleWindow
import java.time.Instant
import javax.inject.Inject

/**
 * App-level state: whether a session is running, and whether a scheduled one is due.
 *
 * Being due is derived from the clock and the stored schedule rather than from the reminder
 * notification, so opening the app at the right moment offers the session whether or not the
 * notification was seen, tapped or dismissed.
 */
@HiltViewModel
class FocusGuardAppViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val sessionEngine: SessionEngine,
    private val alarmScheduler: SessionAlarmScheduler,
) : ViewModel() {

    val runningSession = sessionEngine.state

    /**
     * The session just finished, shown as a result card. Starts as the stored row with no
     * score, then updates when the rating lands, so the dialog can show progress rather than
     * making the user wait for the network before leaving the timer.
     */
    private val _finishedSessionId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val finishedSession = _finishedSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else sessionRepository.getSessionAsFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The finished session's full history, loaded once the session ends.
     *
     * Re-read whenever the session row changes, because the row changes when the rating
     * lands, and the tabs should not go stale behind a score that has just arrived.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val finishedSessionDetail = _finishedSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else sessionRepository.getSessionAsFlow(id).map { sessionRepository.getSessionDetail(id) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            sessionEngine.finishedSessions.collect { _finishedSessionId.value = it }
        }
    }

    fun dismissFinishedSession() {
        _finishedSessionId.value = null
    }

    /** Sessions the user has waved away this run, so the prompt does not nag. */
    private val dismissed = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Ticks only while something is collecting, which means only while the app is on screen —
     * see the `WhileSubscribed` below. Nothing here runs in the background; the reminders are
     * the alarm manager's job, and this is just the app noticing on reopening.
     *
     * Comparing two instants every ten seconds costs nothing measurable, and the window it is
     * looking for is twenty minutes wide, so the cadence could be far slower still.
     */
    private val clock = flow {
        while (true) {
            emit(Instant.now())
            delay(CLOCK_TICK_MILLIS)
        }
    }

    /**
     * The scheduled session that is due right now, if any.
     *
     * Deliberately *not* filtered by what the user has dismissed: this feeds the button on the
     * home screen, which must stay available for the whole window. Waving away the prompt
     * should mean "not this second", not "never mention it again" — the session was found
     * unjoinable after dismissing the dialog once, which is how a scheduled session could be
     * missed while the app sat open in front of the user.
     */
    val dueSession = combine(sessionRepository.scheduledSessions, clock) { scheduled, now ->
        scheduled.firstOrNull { session ->
            val at = session.scheduledAt
            at != null && ScheduleWindow.isDue(at, now)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The same session, unless the prompt for it has been dismissed this run. */
    val duePrompt = combine(dueSession, dismissed) { session, ignored ->
        session?.takeIf { it.id !in ignored }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissDueSession(sessionId: Long) {
        dismissed.value = dismissed.value + sessionId
    }

    /** Promotes a scheduled session to running and hands it to the service. */
    fun joinSession(session: Session) = viewModelScope.launch {
        alarmScheduler.cancel(session)
        sessionRepository.updateSession(
            session.copy(status = SessionStatus.RUNNING, startedAt = Instant.now()),
        )
        FocusSessionService.start(context, session.id)
    }

    private companion object {
        const val CLOCK_TICK_MILLIS = 10_000L
    }
}
