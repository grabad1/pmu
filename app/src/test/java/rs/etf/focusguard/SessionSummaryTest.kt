package rs.etf.focusguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.etf.focusguard.data.SessionSummary
import rs.etf.focusguard.data.room.InterruptionCount

/**
 * The facts a rating is built from.
 *
 * Everything here works in seconds rather than whole minutes. Rounding a duration down to
 * minutes destroys short sessions: a fifty-second block becomes "0 minutes", and a rating
 * built on that figure is correct about the wrong data.
 */
class SessionSummaryTest {

    private fun summary(
        goal: Int = 45,
        focused: Int = 2700,
        plannedCount: Int = 2,
        plannedTaken: Int = 2,
        unplannedCount: Int = 0,
        unplannedSeconds: Int = 0,
        away: Int = 0,
        dark: Double = 0.0,
        loud: Double = 0.0,
        movement: Double = 0.0,
        interruptions: List<InterruptionCount> = emptyList(),
    ) = SessionSummary(
        name = "Algebra",
        goalMinutes = goal,
        focusedSeconds = focused,
        plannedPauseCount = plannedCount,
        plannedPausesTaken = plannedTaken,
        unplannedPauseCount = unplannedCount,
        unplannedPauseSeconds = unplannedSeconds,
        awaySeconds = away,
        darkFraction = dark,
        loudFraction = loud,
        movementFraction = movement,
        interruptions = interruptions,
    )

    private fun app(label: String, calls: Int = 0, notifications: Int = 0) =
        InterruptionCount("com.example.$label", label, calls, notifications)

    // --- durations -------------------------------------------------------

    @Test
    fun `a session under a minute is described in seconds`() {
        assertEquals("52 seconds", summary(focused = 52).focusedDescription)
    }

    @Test
    fun `a single second is not pluralised`() {
        assertEquals("1 second", summary(focused = 1).focusedDescription)
    }

    @Test
    fun `a whole number of minutes omits the seconds`() {
        assertEquals("3 minutes", summary(focused = 180).focusedDescription)
    }

    @Test
    fun `a single minute is not pluralised`() {
        assertEquals("1 minute", summary(focused = 60).focusedDescription)
    }

    @Test
    fun `minutes and seconds are both stated when both are present`() {
        assertEquals("3 minutes 12 seconds", summary(focused = 192).focusedDescription)
    }

    @Test
    fun `one minute and one second avoid both plural traps at once`() {
        assertEquals("1 minute 1 second", summary(focused = 61).focusedDescription)
    }

    @Test
    fun `zero is described as seconds rather than as nothing`() {
        assertEquals("0 seconds", summary(focused = 0).focusedDescription)
    }

    @Test
    fun `a negative duration is treated as zero`() {
        assertEquals("0 seconds", summary(focused = -30).focusedDescription)
    }

    @Test
    fun `whole minutes are still available for display`() {
        assertEquals(45, summary(focused = 2700).focusedMinutes)
        assertEquals(0, summary(focused = 52).focusedMinutes)
    }

    // --- goal ------------------------------------------------------------

    @Test
    fun `meeting the goal exactly counts as reached`() {
        assertTrue(summary(goal = 45, focused = 2700).goalReached)
    }

    @Test
    fun `one second short does not count as reached`() {
        assertFalse(summary(goal = 45, focused = 2699).goalReached)
    }

    @Test
    fun `goal completion is measured in seconds not minutes`() {
        assertEquals(44.0 / 60, summary(goal = 1, focused = 44).goalCompletion, 0.0001)
    }

    @Test
    fun `goal completion passes one during overtime`() {
        assertEquals(2.0, summary(goal = 30, focused = 3600).goalCompletion, 0.0001)
    }

    @Test
    fun `a goal of zero does not divide by zero`() {
        assertEquals(1.0, summary(goal = 0, focused = 100).goalCompletion, 0.0001)
    }

    // --- pauses ----------------------------------------------------------

    @Test
    fun `unplanned time is measured against desk time`() {
        val s = summary(focused = 1800, unplannedCount = 1, unplannedSeconds = 200)

        assertEquals(200.0 / 2000, s.unplannedShare, 0.0001)
    }

    @Test
    fun `unplanned share is zero when nothing was interrupted`() {
        assertEquals(0.0, summary().unplannedShare, 0.0001)
    }

    @Test
    fun `unplanned share does not divide by zero on an empty session`() {
        assertEquals(0.0, summary(focused = 0).unplannedShare, 0.0001)
    }

    @Test
    fun `unplanned minutes round down for display`() {
        assertEquals(3, summary(unplannedSeconds = 200).unplannedPauseMinutes)
    }

    @Test
    fun `about one break per twenty minutes is reasonable`() {
        assertEquals(3, summary(goal = 60).reasonablePauseCount)
        assertEquals(2, summary(goal = 45).reasonablePauseCount)
    }

    @Test
    fun `even a very short goal warrants at least one break`() {
        assertEquals(1, summary(goal = 5).reasonablePauseCount)
        assertEquals(1, summary(goal = 1).reasonablePauseCount)
    }

    @Test
    fun `planning within the reasonable count is no excess`() {
        assertEquals(0, summary(goal = 60, plannedCount = 3).excessPlannedPauses)
    }

    @Test
    fun `planning more breaks than the goal warrants is excess`() {
        assertEquals(2, summary(goal = 60, plannedCount = 5).excessPlannedPauses)
    }

    @Test
    fun `planning fewer breaks is never counted as excess`() {
        assertEquals(0, summary(goal = 60, plannedCount = 0).excessPlannedPauses)
    }

    // --- time away -------------------------------------------------------

    @Test
    fun `time away is measured against the focus it displaced`() {
        assertEquals(0.1, summary(focused = 1000, away = 100).awayShare, 0.0001)
    }

    @Test
    fun `time away does not divide by zero`() {
        assertEquals(0.0, summary(focused = 0, away = 100).awayShare, 0.0001)
    }

    @Test
    fun `time away is described in the same wording as focus time`() {
        assertEquals("1 minute 5 seconds", summary(away = 65).awayDescription)
    }

    // --- interruptions ---------------------------------------------------

    @Test
    fun `interruptions are counted across every app`() {
        val s = summary(interruptions = listOf(app("Chat", notifications = 4), app("Phone", calls = 2)))

        assertEquals(6, s.interruptionCount)
        assertEquals(2, s.callCount)
    }

    @Test
    fun `a session with no interruptions counts none`() {
        assertEquals(0, summary().interruptionCount)
        assertEquals(0, summary().callCount)
    }

    @Test
    fun `a single interrupting app is not yet worth naming`() {
        assertNull(summary(interruptions = listOf(app("Chat", notifications = 1))).worstInterrupter)
    }

    @Test
    fun `an app interrupting twice is worth naming`() {
        val s = summary(interruptions = listOf(app("Chat", notifications = 2)))

        assertEquals("Chat", s.worstInterrupter?.appLabel)
    }

    @Test
    fun `the busiest app is the one named`() {
        val s = summary(
            interruptions = listOf(app("Quiet", notifications = 2), app("Loud", notifications = 9)),
        )

        assertEquals("Loud", s.worstInterrupter?.appLabel)
    }

    @Test
    fun `being interrupted once or twice draws no advice`() {
        assertNull(summary(interruptions = listOf(app("Chat", notifications = 2))).interruptionAdvice)
    }

    @Test
    fun `one dominant app is named in the advice`() {
        val s = summary(interruptions = listOf(app("Chat", notifications = 6), app("Mail", notifications = 1)))

        assertTrue(s.interruptionAdvice!!.contains("Chat"))
    }

    @Test
    fun `interruptions spread thin suggest do not disturb instead`() {
        val s = summary(
            interruptions = listOf(
                app("One", notifications = 1),
                app("Two", notifications = 1),
                app("Three", notifications = 1),
                app("Four", notifications = 1),
            ),
        )

        assertTrue(s.interruptionAdvice!!.contains("Do Not Disturb"))
    }

    @Test
    fun `advice never claims a penalty was applied`() {
        val s = summary(interruptions = listOf(app("Chat", notifications = 8)))

        assertFalse(s.interruptionAdvice!!.contains("score"))
    }
}
