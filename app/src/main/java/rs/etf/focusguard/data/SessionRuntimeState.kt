package rs.etf.focusguard.data

import rs.etf.focusguard.data.room.PauseType

/**
 * Everything the running-session UI and the notification need, recomputed each tick.
 *
 * Times are derived from the clock rather than counted, so a delayed or coalesced tick
 * cannot make the session drift.
 */
data class SessionRuntimeState(
    val sessionId: Long,
    val name: String,
    val goalSeconds: Int,

    /** Focus time only; pauses excluded. Keeps growing past [goalSeconds]. */
    val focusedSeconds: Int,

    /** Null while focusing. */
    val activePauseType: PauseType? = null,

    val pauseElapsedSeconds: Int = 0,

    /** Countdown for a planned pause; null for unplanned ones, which run until resumed. */
    val pauseRemainingSeconds: Int? = null,

    /** Focus seconds until the next planned pause, or null when none is upcoming. */
    val nextPauseInSeconds: Int? = null,

    val hasPlannedPauses: Boolean = false,

    val plannedPausesRemaining: Int = 0,

    /** Seconds spent in another app while this session was supposed to be focusing. */
    val awaySeconds: Int = 0,

    /** True while the user is in another app during focus time. */
    val isAway: Boolean = false,
) {
    val isPaused: Boolean get() = activePauseType != null

    /** Passing the goal is a success state, not an end state — the session keeps running. */
    val isPastGoal: Boolean get() = focusedSeconds >= goalSeconds

    /** 0..1, clamped, for the progress ring. */
    val progress: Float
        get() = if (goalSeconds <= 0) 1f else (focusedSeconds.toFloat() / goalSeconds).coerceIn(0f, 1f)
}
