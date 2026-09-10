package rs.etf.focusguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.etf.focusguard.data.SessionRuntimeState
import rs.etf.focusguard.data.room.PauseType

/**
 * The state the running-session screen and the notification both read.
 *
 * Passing the goal is a success state rather than an end state, so the progress ring has to
 * stop growing while the session keeps counting. Those two behaviours pull in opposite
 * directions and are easy to break together.
 */
class SessionRuntimeStateTest {

    private fun state(
        focused: Int = 0,
        goal: Int = 45 * 60,
        pause: PauseType? = null,
        pauseElapsed: Int = 0,
        pauseRemaining: Int? = null,
        away: Int = 0,
        isAway: Boolean = false,
    ) = SessionRuntimeState(
        sessionId = 1,
        name = "Algebra",
        goalSeconds = goal,
        focusedSeconds = focused,
        activePauseType = pause,
        pauseElapsedSeconds = pauseElapsed,
        pauseRemainingSeconds = pauseRemaining,
        awaySeconds = away,
        isAway = isAway,
    )

    @Test
    fun `a session with no active pause is not paused`() {
        assertFalse(state().isPaused)
    }

    @Test
    fun `a planned pause counts as paused`() {
        assertTrue(state(pause = PauseType.PLANNED).isPaused)
    }

    @Test
    fun `an unplanned pause counts as paused`() {
        assertTrue(state(pause = PauseType.UNPLANNED).isPaused)
    }

    @Test
    fun `a fresh session has not reached its goal`() {
        assertFalse(state(focused = 0).isPastGoal)
    }

    @Test
    fun `one second short of the goal is not past it`() {
        assertFalse(state(focused = 45 * 60 - 1).isPastGoal)
    }

    @Test
    fun `reaching the goal exactly counts as past it`() {
        assertTrue(state(focused = 45 * 60).isPastGoal)
    }

    @Test
    fun `exceeding the goal counts as past it`() {
        assertTrue(state(focused = 60 * 60).isPastGoal)
    }

    @Test
    fun `progress starts at zero`() {
        assertEquals(0f, state(focused = 0).progress, 0.0001f)
    }

    @Test
    fun `progress is half way at half the goal`() {
        assertEquals(0.5f, state(focused = 45 * 30).progress, 0.0001f)
    }

    @Test
    fun `progress reaches one at the goal`() {
        assertEquals(1f, state(focused = 45 * 60).progress, 0.0001f)
    }

    @Test
    fun `progress never exceeds one during overtime`() {
        assertEquals(1f, state(focused = 90 * 60).progress, 0.0001f)
    }

    @Test
    fun `progress is never negative`() {
        assertTrue(state(focused = -30).progress >= 0f)
    }

    @Test
    fun `a goal of zero reports full progress rather than dividing by zero`() {
        assertEquals(1f, state(focused = 0, goal = 0).progress, 0.0001f)
    }

    @Test
    fun `a negative goal also avoids dividing by zero`() {
        assertEquals(1f, state(focused = 10, goal = -60).progress, 0.0001f)
    }

    @Test
    fun `a planned pause carries a countdown`() {
        val paused = state(pause = PauseType.PLANNED, pauseElapsed = 40, pauseRemaining = 260)

        assertEquals(260, paused.pauseRemainingSeconds)
        assertEquals(40, paused.pauseElapsedSeconds)
    }

    @Test
    fun `an unplanned pause has no countdown because it runs until resumed`() {
        val paused = state(pause = PauseType.UNPLANNED, pauseElapsed = 40)

        assertEquals(null, paused.pauseRemainingSeconds)
    }

    @Test
    fun `time away is carried on the state`() {
        assertEquals(75, state(away = 75, isAway = true).awaySeconds)
        assertTrue(state(away = 75, isAway = true).isAway)
    }

    @Test
    fun `being away is independent of being paused`() {
        val away = state(pause = PauseType.UNPLANNED, isAway = false)

        assertTrue(away.isPaused)
        assertFalse(away.isAway)
    }
}
