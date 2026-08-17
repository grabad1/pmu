package rs.etf.focusguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.etf.focusguard.data.LocalSessionRater
import rs.etf.focusguard.data.SessionSummary

class LocalSessionRaterTest {

    private fun summary(
        goalMinutes: Int = 60,
        focusedMinutes: Int = 60,
        plannedPauseCount: Int = 2,
        plannedPausesTaken: Int = 2,
        unplannedPauseCount: Int = 0,
        unplannedPauseMinutes: Int = 0,
        darkFraction: Double = 0.0,
        loudFraction: Double = 0.0,
        movementFraction: Double = 0.0,
    ) = SessionSummary(
        name = "Test",
        goalMinutes = goalMinutes,
        focusedSeconds = focusedMinutes * 60,
        plannedPauseCount = plannedPauseCount,
        plannedPausesTaken = plannedPausesTaken,
        unplannedPauseCount = unplannedPauseCount,
        unplannedPauseMinutes = unplannedPauseMinutes,
        darkFraction = darkFraction,
        loudFraction = loudFraction,
        movementFraction = movementFraction,
    )

    @Test
    fun `a flawless session scores full marks`() {
        assertEquals(100, LocalSessionRater.score(summary()))
    }

    @Test
    fun `exceeding the goal is not penalised`() {
        val met = LocalSessionRater.score(summary(focusedMinutes = 60))
        val exceeded = LocalSessionRater.score(summary(focusedMinutes = 95))

        assertEquals(met, exceeded)
    }

    @Test
    fun `stopping halfway roughly halves the goal component`() {
        val full = LocalSessionRater.score(summary(focusedMinutes = 60))
        val half = LocalSessionRater.score(summary(focusedMinutes = 30))

        // 60 points ride on the goal, so half of it costs about 30.
        assertEquals(30, full - half)
    }

    @Test
    fun `unplanned pauses lower the score and planned ones do not`() {
        val clean = LocalSessionRater.score(summary(unplannedPauseCount = 0))
        val one = LocalSessionRater.score(summary(unplannedPauseCount = 1))
        val three = LocalSessionRater.score(summary(unplannedPauseCount = 3))

        assertTrue("one unplanned pause should cost", one < clean)
        assertTrue("more unplanned pauses should cost more", three < one)

        // Taking every planned pause is the baseline, not a penalty.
        val allPlannedTaken = LocalSessionRater.score(
            summary(plannedPauseCount = 4, plannedPausesTaken = 4),
        )
        assertEquals(clean, allPlannedTaken)
    }

    @Test
    fun `skipping planned pauses costs discipline points`() {
        val took = LocalSessionRater.score(summary(plannedPauseCount = 2, plannedPausesTaken = 2))
        val skipped = LocalSessionRater.score(summary(plannedPauseCount = 2, plannedPausesTaken = 0))

        assertEquals(10, took - skipped)
    }

    @Test
    fun `a poor environment lowers the score`() {
        val comfortable = LocalSessionRater.score(summary())
        val dark = LocalSessionRater.score(summary(darkFraction = 1.0))
        val awful = LocalSessionRater.score(
            summary(darkFraction = 1.0, loudFraction = 1.0, movementFraction = 1.0),
        )

        assertEquals(10, comfortable - dark)
        assertEquals(30, comfortable - awful)
    }

    @Test
    fun `the score always stays within bounds`() {
        val worst = LocalSessionRater.score(
            summary(
                focusedMinutes = 0,
                plannedPausesTaken = 0,
                unplannedPauseCount = 20,
                unplannedPauseMinutes = 90,
                darkFraction = 1.0,
                loudFraction = 1.0,
                movementFraction = 1.0,
            )
        )
        assertTrue("got $worst", worst in 0..100)
        assertEquals(0, worst)
    }

    @Test
    fun `a session with no goal does not divide by zero`() {
        val score = LocalSessionRater.score(summary(goalMinutes = 0, focusedMinutes = 0))
        assertTrue("got $score", score in 0..100)
    }

    @Test
    fun `partial minutes still count towards the goal`() {
        // 44 seconds of a one-minute goal is most of the way there; whole-minute arithmetic
        // would score it as nothing.
        val almost = SessionSummary(
            name = "Test",
            goalMinutes = 1,
            focusedSeconds = 44,
            plannedPauseCount = 0,
            plannedPausesTaken = 0,
            unplannedPauseCount = 0,
            unplannedPauseMinutes = 0,
            darkFraction = 0.0,
            loudFraction = 0.0,
            movementFraction = 0.0,
        )

        // 44/60 of the 60 goal points is 44, plus 10 discipline and 30 environment.
        assertEquals(84, LocalSessionRater.score(almost))
    }

    @Test
    fun `the wording reflects the outcome`() {
        val exceeded = LocalSessionRater.rate(summary(focusedMinutes = 95))
        assertTrue(exceeded.comment.contains("Exceptional"))
        assertTrue(exceeded.analysis.contains("No unplanned pauses"))

        val struggled = LocalSessionRater.rate(
            summary(focusedMinutes = 8, unplannedPauseCount = 3, movementFraction = 0.9),
        )
        assertTrue(struggled.analysis.contains("unplanned"))
        assertTrue(struggled.analysis.contains("phone was handled"))
    }
}
