package rs.etf.focusguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.data.room.SessionStatus
import rs.etf.focusguard.util.ScheduleWindow
import rs.etf.focusguard.util.isMissed
import java.time.Duration
import java.time.Instant

/**
 * The window in which a scheduled session may be started.
 *
 * Two screens depend on this agreeing with itself: the home screen offers the session while
 * it is due, and the scheduled list marks it missed once it is not. A session shown as
 * missed while still being offered would be worse than either behaviour alone.
 */
class ScheduleWindowTest {

    private val start: Instant = Instant.parse("2026-09-10T09:00:00Z")

    private fun scheduled(at: Instant = start) = Session(
        id = 1,
        name = "Algebra",
        goalMinutes = 45,
        plannedPauseCount = 2,
        plannedPauseMinutes = 5,
        status = SessionStatus.SCHEDULED,
        scheduledAt = at,
    )

    @Test
    fun `the window opens five minutes early`() {
        assertEquals(Duration.ofMinutes(5), ScheduleWindow.DUE_BEFORE)
    }

    @Test
    fun `the window stays open fifteen minutes afterwards`() {
        assertEquals(Duration.ofMinutes(15), ScheduleWindow.DUE_AFTER)
    }

    @Test
    fun `a session is due at its exact start`() {
        assertTrue(ScheduleWindow.isDue(start, start))
    }

    @Test
    fun `a session is due four minutes early`() {
        assertTrue(ScheduleWindow.isDue(start, start.minus(Duration.ofMinutes(4))))
    }

    @Test
    fun `a session is not yet due six minutes early`() {
        assertFalse(ScheduleWindow.isDue(start, start.minus(Duration.ofMinutes(6))))
    }

    @Test
    fun `a session is still due fourteen minutes late`() {
        assertTrue(ScheduleWindow.isDue(start, start.plus(Duration.ofMinutes(14))))
    }

    @Test
    fun `a session is no longer due sixteen minutes late`() {
        assertFalse(ScheduleWindow.isDue(start, start.plus(Duration.ofMinutes(16))))
    }

    @Test
    fun `a session is not due a day early`() {
        assertFalse(ScheduleWindow.isDue(start, start.minus(Duration.ofDays(1))))
    }

    @Test
    fun `a session is not due a day late`() {
        assertFalse(ScheduleWindow.isDue(start, start.plus(Duration.ofDays(1))))
    }

    @Test
    fun `a session is not missed while it is still due`() {
        assertFalse(ScheduleWindow.isMissed(start, start.plus(Duration.ofMinutes(10))))
    }

    @Test
    fun `a session is missed once the window closes`() {
        assertTrue(ScheduleWindow.isMissed(start, start.plus(Duration.ofMinutes(16))))
    }

    @Test
    fun `a session is not missed before it was due to start`() {
        assertFalse(ScheduleWindow.isMissed(start, start.minus(Duration.ofHours(2))))
    }

    @Test
    fun `due and missed are never both true`() {
        var moment = start.minus(Duration.ofMinutes(30))
        repeat(120) {
            val due = ScheduleWindow.isDue(start, moment)
            val missed = ScheduleWindow.isMissed(start, moment)
            assertFalse("both at $moment", due && missed)
            moment = moment.plus(Duration.ofSeconds(30))
        }
    }

    @Test
    fun `a scheduled session past its window reports itself missed`() {
        assertTrue(scheduled().isMissed(start.plus(Duration.ofMinutes(20))))
    }

    @Test
    fun `a scheduled session inside its window does not report itself missed`() {
        assertFalse(scheduled().isMissed(start.plus(Duration.ofMinutes(2))))
    }

    @Test
    fun `a completed session is never missed`() {
        val finished = scheduled().copy(status = SessionStatus.COMPLETED)

        assertFalse(finished.isMissed(start.plus(Duration.ofDays(3))))
    }

    @Test
    fun `a running session is never missed`() {
        val running = scheduled().copy(status = SessionStatus.RUNNING)

        assertFalse(running.isMissed(start.plus(Duration.ofDays(3))))
    }

    @Test
    fun `a session without a scheduled moment is never missed`() {
        val adHoc = scheduled().copy(scheduledAt = null)

        assertFalse(adHoc.isMissed(start.plus(Duration.ofDays(3))))
    }
}
