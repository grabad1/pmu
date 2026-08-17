package rs.etf.focusguard.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rs.etf.focusguard.LOG_TAG
import rs.etf.focusguard.data.room.Pause
import rs.etf.focusguard.data.room.PauseType
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.data.room.SessionStatus
import rs.etf.focusguard.util.plannedPauseOffsetsSeconds
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the clock and rules of a running session.
 *
 * A singleton rather than service state so the UI can observe it directly, and so a
 * configuration change or a trip through the launcher cannot restart the session. The
 * foreground service exists to keep the process alive and show the notification; it does
 * not own the timing.
 *
 * Elapsed time is always computed from timestamps. Ticks only trigger recomputation, so a
 * late tick shows the correct time rather than a time that has silently fallen behind.
 */
@Singleton
class SessionEngine @Inject constructor(
    private val repository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<SessionRuntimeState?>(null)
    val state = _state.asStateFlow()

    private var ticker: Job? = null

    private var session: Session? = null
    private var plannedOffsets: List<Int> = emptyList()

    /** Focus seconds banked by segments that have already ended. */
    private var bankedFocusSeconds: Int = 0

    /** When the current focus segment began; null while paused. */
    private var focusSegmentStart: Instant? = null

    private var pauseStart: Instant? = null
    private var pauseType: PauseType? = null
    private var openPauseId: Long? = null
    private var plannedPausesTaken: Int = 0

    /**
     * Begins or resumes timing [sessionId]. Idempotent: attaching to the session already
     * running is a no-op, so the service, a sticky restart and a cold start cannot fight
     * over it.
     *
     * State is always rebuilt from what is stored, which makes a fresh start and a recovery
     * after process death the same code path — a brand-new session simply has no pauses and
     * a `startedAt` of now, so it reconstructs to zero.
     */
    fun attach(sessionId: Long) {
        scope.launch { attachInternal(sessionId) }
    }

    /** Re-attaches to whatever session the database still considers RUNNING. */
    fun attachRunning() {
        scope.launch {
            val running = repository.getRunningSession() ?: return@launch
            attachInternal(running.id)
        }
    }

    private suspend fun attachInternal(sessionId: Long) {
        if (mutex.withLock { session?.id == sessionId }) return

        val loaded = repository.getSession(sessionId) ?: return
        if (loaded.status != SessionStatus.RUNNING) return
        val startedAt = loaded.startedAt ?: return

        val pauses = repository.getPauses(sessionId)
        val open = pauses.firstOrNull { it.endedAt == null }

        mutex.withLock {
            session = loaded
            plannedOffsets = plannedPauseOffsetsSeconds(
                goalMinutes = loaded.goalMinutes,
                pauseCount = loaded.plannedPauseCount,
            )
            plannedPausesTaken = pauses.count { it.type == PauseType.PLANNED }

            if (open != null) {
                // Focus stopped the moment the pause began.
                bankedFocusSeconds = open.startOffsetSeconds
                focusSegmentStart = null
                pauseStart = open.startedAt
                pauseType = open.type
                openPauseId = open.id
            } else {
                val wallSeconds = Duration.between(startedAt, Instant.now()).seconds.toInt()
                val pausedSeconds = pauses.sumOf { it.durationSeconds }
                bankedFocusSeconds = (wallSeconds - pausedSeconds).coerceAtLeast(0)
                focusSegmentStart = Instant.now()
                pauseStart = null
                pauseType = null
                openPauseId = null
            }
            Log.d(
                LOG_TAG,
                "SessionEngine.attach($sessionId) focus=$bankedFocusSeconds " +
                    "offsets=$plannedOffsets paused=${pauseType != null}",
            )
        }
        recompute()
        startTicking()
    }

    /** Pause button: resume if paused, otherwise begin an unplanned pause. */
    fun togglePause() {
        scope.launch {
            val shouldResume = mutex.withLock { pauseType != null }
            if (shouldResume) endCurrentPause() else beginPause(PauseType.UNPLANNED)
            recompute()
        }
    }

    /**
     * Finishes the session and stores it. Suspends until the write completes so callers can
     * safely act afterwards; navigation must never be triggered from the engine's own
     * background scope.
     */
    suspend fun endSession() {
        if (pauseType != null) endCurrentPause()

        val ended = mutex.withLock {
            val current = session ?: return
            val focused = computeFocusedSeconds()
            val finished = current.copy(
                status = SessionStatus.COMPLETED,
                endedAt = Instant.now(),
                focusedSeconds = focused,
            )
            session = null
            focusSegmentStart = null
            finished
        }

        repository.updateSession(ended)
        ticker?.cancel()
        ticker = null
        _state.value = null
        Log.d(LOG_TAG, "SessionEngine.endSession(${ended.id}) focused=${ended.focusedSeconds}")
    }

    private fun startTicking() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                onTick()
            }
        }
    }

    private suspend fun onTick() {
        val triggerPlanned = mutex.withLock {
            if (session == null) return@withLock false
            pauseType == null && nextPlannedOffset() != null &&
                computeFocusedSeconds() >= nextPlannedOffset()!! && !isPastGoalLocked()
        }
        if (triggerPlanned) beginPause(PauseType.PLANNED)

        val shouldEndPlanned = mutex.withLock {
            val duration = session?.plannedPauseMinutes?.times(60) ?: 0
            pauseType == PauseType.PLANNED && currentPauseSeconds() >= duration
        }
        if (shouldEndPlanned) endCurrentPause()

        persistProgressPeriodically()
        recompute()
    }

    private suspend fun beginPause(type: PauseType) {
        val pause = mutex.withLock {
            val current = session ?: return
            if (pauseType != null) return

            val offset = computeFocusedSeconds()
            bankedFocusSeconds = offset
            focusSegmentStart = null
            pauseStart = Instant.now()
            pauseType = type
            if (type == PauseType.PLANNED) plannedPausesTaken++

            Pause(
                sessionId = current.id,
                type = type,
                startOffsetSeconds = offset,
                startedAt = pauseStart!!,
            )
        }
        val id = repository.insertPause(pause)
        mutex.withLock { openPauseId = id }
        Log.d(LOG_TAG, "SessionEngine pause started: ${pause.type} at ${pause.startOffsetSeconds}s")
    }

    private suspend fun endCurrentPause() {
        val finished = mutex.withLock {
            val start = pauseStart ?: return
            val type = pauseType ?: return
            val id = openPauseId ?: return
            val now = Instant.now()
            val seconds = Duration.between(start, now).seconds.toInt().coerceAtLeast(0)

            pauseStart = null
            pauseType = null
            openPauseId = null
            focusSegmentStart = now

            Pause(
                id = id,
                sessionId = session?.id ?: return,
                type = type,
                startOffsetSeconds = bankedFocusSeconds,
                startedAt = start,
                endedAt = now,
                durationSeconds = seconds,
            )
        }
        repository.updatePause(finished)
        Log.d(LOG_TAG, "SessionEngine pause ended after ${finished.durationSeconds}s")
    }

    /**
     * Keeps the stored focus time roughly current so a process death loses seconds, not
     * minutes. Writing every tick would be wasteful, so this only writes once a minute.
     */
    private suspend fun persistProgressPeriodically() {
        val toSave = mutex.withLock {
            val current = session ?: return
            val focused = computeFocusedSeconds()
            if (focused % PERSIST_EVERY_SECONDS != 0 || focused == current.focusedSeconds) {
                null
            } else {
                current.copy(focusedSeconds = focused).also { session = it }
            }
        } ?: return
        repository.updateSession(toSave)
    }

    private suspend fun recompute() {
        _state.value = mutex.withLock {
            val current = session ?: return@withLock null
            val focused = computeFocusedSeconds()
            val pauseDuration = current.plannedPauseMinutes * 60
            val elapsedPause = currentPauseSeconds()

            SessionRuntimeState(
                sessionId = current.id,
                name = current.name,
                goalSeconds = current.goalMinutes * 60,
                focusedSeconds = focused,
                activePauseType = pauseType,
                pauseElapsedSeconds = elapsedPause,
                pauseRemainingSeconds = if (pauseType == PauseType.PLANNED) {
                    (pauseDuration - elapsedPause).coerceAtLeast(0)
                } else {
                    null
                },
                nextPauseInSeconds = nextPlannedOffset()
                    ?.takeIf { pauseType == null && !isPastGoalLocked() }
                    ?.let { (it - focused).coerceAtLeast(0) },
                hasPlannedPauses = plannedOffsets.isNotEmpty(),
                plannedPausesRemaining = (plannedOffsets.size - plannedPausesTaken).coerceAtLeast(0),
            )
        }
    }

    /** Caller must hold [mutex]. */
    private fun computeFocusedSeconds(): Int {
        val start = focusSegmentStart ?: return bankedFocusSeconds
        return bankedFocusSeconds + Duration.between(start, Instant.now()).seconds.toInt()
    }

    /** Caller must hold [mutex]. */
    private fun currentPauseSeconds(): Int {
        val start = pauseStart ?: return 0
        return Duration.between(start, Instant.now()).seconds.toInt().coerceAtLeast(0)
    }

    /** Caller must hold [mutex]. */
    private fun nextPlannedOffset(): Int? = plannedOffsets.getOrNull(plannedPausesTaken)

    /** Caller must hold [mutex]. */
    private fun isPastGoalLocked(): Boolean {
        val goal = session?.goalMinutes?.times(60) ?: return false
        return computeFocusedSeconds() >= goal
    }

    companion object {
        private const val TICK_MILLIS = 500L
        private const val PERSIST_EVERY_SECONDS = 60
    }
}
