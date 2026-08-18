package rs.etf.focusguard.util

import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.data.room.SessionStatus
import java.time.Duration
import java.time.Instant

/**
 * When a scheduled session counts as due, and when it has been missed.
 *
 * Gathered here because two screens have to agree: the app offers to start a session inside
 * this window, and the scheduled list marks one as missed the moment it closes. Two separate
 * definitions would drift, and a session that was still being offered while already showing as
 * missed would be worse than either.
 */
object ScheduleWindow {

    /** Offered a little early, so a session can be started as the user sits down. */
    val DUE_BEFORE: Duration = Duration.ofMinutes(5)

    /** And for a while afterwards, so arriving a few minutes late is not punished. */
    val DUE_AFTER: Duration = Duration.ofMinutes(15)

    fun isDue(scheduledAt: Instant, now: Instant): Boolean =
        now.isAfter(scheduledAt.minus(DUE_BEFORE)) && now.isBefore(scheduledAt.plus(DUE_AFTER))

    /** Past its window and never started. Still stored, but no longer going to happen. */
    fun isMissed(scheduledAt: Instant, now: Instant): Boolean =
        now.isAfter(scheduledAt.plus(DUE_AFTER))
}

/** True when this scheduled session's moment has passed without it ever being started. */
fun Session.isMissed(now: Instant = Instant.now()): Boolean {
    if (status != SessionStatus.SCHEDULED) return false
    val at = scheduledAt ?: return false
    return ScheduleWindow.isMissed(at, now)
}
