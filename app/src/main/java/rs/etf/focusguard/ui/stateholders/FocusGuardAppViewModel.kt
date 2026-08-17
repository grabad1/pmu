package rs.etf.focusguard.ui.stateholders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rs.etf.focusguard.FocusSessionService
import rs.etf.focusguard.alarms.SessionAlarmScheduler
import rs.etf.focusguard.data.SessionEngine
import rs.etf.focusguard.data.SessionRepository
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.data.room.SessionStatus
import java.time.Duration
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

    /** Sessions the user has waved away this run, so the prompt does not nag. */
    private val dismissed = MutableStateFlow<Set<Long>>(emptySet())

    private val clock = flow {
        while (true) {
            emit(Instant.now())
            delay(CLOCK_TICK_MILLIS)
        }
    }

    val dueSession = combine(
        sessionRepository.scheduledSessions,
        clock,
        dismissed,
    ) { scheduled, now, ignored ->
        scheduled.firstOrNull { session ->
            session.id !in ignored && session.scheduledAt?.isDue(now) == true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Due from shortly before the start until a grace period after, so a session is still
     * offered to someone who reaches their desk a few minutes late.
     */
    private fun Instant.isDue(now: Instant): Boolean {
        val from = minus(DUE_BEFORE)
        val until = plus(DUE_AFTER)
        return now.isAfter(from) && now.isBefore(until)
    }

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
        val DUE_BEFORE: Duration = Duration.ofMinutes(5)
        val DUE_AFTER: Duration = Duration.ofMinutes(15)
        const val CLOCK_TICK_MILLIS = 10_000L
    }
}
