package rs.etf.focusguard.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import rs.etf.focusguard.util.AppForegroundMonitor
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
    private val ratingRepository: SessionRatingRepository,
    private val appForegroundMonitor: AppForegroundMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<SessionRuntimeState?>(null)
    val state = _state.asStateFlow()

    /** Emits a session id once it has been stored and rated, so the UI can show the result. */
    private val _ratedSessions = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val ratedSessions = _ratedSessions.asSharedFlow()

    /** Emits as soon as a session ends, before the rating is known. */
    private val _finishedSessions = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val finishedSessions = _finishedSessions.asSharedFlow()

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
     * Seconds spent in another app while the session was supposed to be focusing.
     *
     * Counted the same way as focus time — from timestamps, not by counting ticks — and only
     * while actually focusing: leaving the phone during a planned break is the point of the
     * break, not a lapse.
     */
    private var awayBanked: Int = 0
    private var awaySegmentStart: Instant? = null

    init {
        // Leaving the app during a session is the one distraction the sensors cannot see, so
        // the engine watches for it directly.
        scope.launch {
            appForegroundMonitor.isForeground.collect { foreground ->
                mutex.withLock {
                    if (foreground) endAwaySegment() else startAwaySegment()
                }
                recompute()
            }
        }
    }

    /** Must be called with [mutex] held. */
    private fun startAwaySegment() {
        if (session == null || pauseType != null) return
        if (awaySegmentStart == null) awaySegmentStart = Instant.now()
    }

    /** Must be called with [mutex] held. */
    private fun endAwaySegment() {
        val start = awaySegmentStart ?: return
        awayBanked += Duration.between(start, Instant.now()).seconds.toInt().coerceAtLeast(0)
        awaySegmentStart = null
    }

    /** Must be called with [mutex] held. */
    private fun computeAwaySeconds(): Int {
        val start = awaySegmentStart ?: return awayBanked
        return awayBanked + Duration.between(start, Instant.now()).seconds.toInt().coerceAtLeast(0)
    }

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

            // Time away carries over from what was already stored, so a session recovered
            // after process death does not forget that the user had wandered off.
            awayBanked = loaded.awaySeconds
            awaySegmentStart = null
            if (pauseType == null && !appForegroundMonitor.isForeground.value) {
                awaySegmentStart = Instant.now()
            }
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
            endAwaySegment()
            val finished = current.copy(
                status = SessionStatus.COMPLETED,
                endedAt = Instant.now(),
                focusedSeconds = focused,
                awaySeconds = awayBanked,
            )
            session = null
            focusSegmentStart = null
            finished
        }

        repository.updateSession(ended)
        ticker?.cancel()
        ticker = null
        _state.value = null
        Log.d(
            LOG_TAG,
            "SessionEngine.endSession(${ended.id}) focused=${ended.focusedSeconds} " +
                "away=${ended.awaySeconds}",
        )
        _finishedSessions.tryEmit(ended.id)

        // Rating runs after the session is stored and the UI has been released, so a slow
        // network cannot hold up leaving the timer screen.
        scope.launch { rate(ended.id) }
    }

    private suspend fun rate(sessionId: Long) {
        val withPauses = repository.getSessionWithPauses(sessionId) ?: return
        val samples = repository.getSensorSamples(sessionId)
        val summary = SessionSummary.from(withPauses, samples)

        // How this user's earlier sessions on the same topic went, so the comment can place
        // this one against their own history rather than judging it in isolation. Narrowest
        // useful grouping: the topic if there is one, otherwise the category.
        val session = withPauses.session
        val baseline = if (session.topic != null || session.category != null) {
            repository.summarise(
                category = session.category,
                topic = session.topic,
                excludeSessionId = sessionId,
            )
        } else {
            null
        }

        val rating = ratingRepository.rate(summary, baseline)
        repository.saveRating(sessionId, rating)
        Log.d(LOG_TAG, "Rated session $sessionId: ${rating.score} — ${rating.comment}")
        _ratedSessions.tryEmit(sessionId)
    }

    /**
     * Scores any finished session that never got a score, and is called once when the app
     * starts.
     *
     * [endSession] launches the rating and returns immediately, so that leaving the timer is
     * never held up by the network. The cost of that is a window of up to half a minute in
     * which the process can die — the user swipes the app away, or Android reclaims it — and
     * take the in-flight rating with it. Testing found three sessions stranded that way, and
     * without this nothing ever went back for them: they stayed unscored for good.
     *
     * Rating is idempotent, since only rows with a null score are considered.
     */
    suspend fun rateOutstandingSessions() {
        val unrated = repository.getUnratedSessions()
        if (unrated.isEmpty()) return

        Log.d(LOG_TAG, "Found ${unrated.size} finished session(s) with no score; rating them")
        unrated.forEach { session ->
            runCatching { rate(session.id) }
                .onFailure { Log.w(LOG_TAG, "Could not rate session ${session.id}: ${it.message}") }
        }
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
            // A break is time the user is entitled to spend anywhere, so time away stops
            // accruing the moment one starts.
            endAwaySegment()

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
            // If they are still in another app when the break ends, they are away again.
            if (!appForegroundMonitor.isForeground.value) awaySegmentStart = now

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
                awaySeconds = computeAwaySeconds(),
                isAway = awaySegmentStart != null,
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
