package rs.etf.focusguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.etf.focusguard.data.LocalSessionRater
import rs.etf.focusguard.data.SessionSummary

class LocalSessionRaterTest {

    private fun summary(
        goalMinutes: Int = 45,
        focusedMinutes: Int = 45,
        plannedPauseCount: Int = 0,
        plannedPausesTaken: Int = 0,
        unplannedPauseCount: Int = 0,
        unplannedPauseSeconds: Int = 0,
        awaySeconds: Int = 0,
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
        unplannedPauseSeconds = unplannedPauseSeconds,
        awaySeconds = awaySeconds,
        darkFraction = darkFraction,
        loudFraction = loudFraction,
        movementFraction = movementFraction,
    )

    @Test
    fun `a long clean session scores full marks`() {
        assertEquals(100, LocalSessionRater.score(summary()))
    }

    @Test
    fun `exceeding the goal is never penalised`() {
        assertEquals(
            LocalSessionRater.score(summary(focusedMinutes = 45)),
            LocalSessionRater.score(summary(focusedMinutes = 70)),
        )
    }

    // --- session length -----------------------------------------------------------------

    @Test
    fun `a four minute session cannot score highly however clean it is`() {
        val tiny = LocalSessionRater.score(summary(goalMinutes = 4, focusedMinutes = 4))

        assertTrue("a 4-minute session scored $tiny", tiny <= 60)
    }

    @Test
    fun `the length ceiling relaxes as sessions get longer`() {
        val four = LocalSessionRater.score(summary(goalMinutes = 4, focusedMinutes = 4))
        val eight = LocalSessionRater.score(summary(goalMinutes = 8, focusedMinutes = 8))
        val fifteen = LocalSessionRater.score(summary(goalMinutes = 15, focusedMinutes = 15))
        val thirty = LocalSessionRater.score(summary(goalMinutes = 30, focusedMinutes = 30))

        assertTrue(four < eight)
        assertTrue(eight < fifteen)
        assertTrue(fifteen < thirty)
        assertEquals(100, thirty)
    }

    @Test
    fun `a short session is told to aim longer`() {
        val rating = LocalSessionRater.rate(summary(goalMinutes = 4, focusedMinutes = 4))

        assertTrue(rating.comment.contains("short", ignoreCase = true))
        assertTrue(rating.analysis.contains("20 to 25 minutes"))
    }

    // --- planned pauses -----------------------------------------------------------------

    @Test
    fun `a sensible number of planned pauses costs nothing`() {
        val none = LocalSessionRater.score(summary(plannedPauseCount = 0))
        val two = LocalSessionRater.score(summary(plannedPauseCount = 2, plannedPausesTaken = 2))

        assertEquals(none, two)
    }

    @Test
    fun `too many planned pauses for the goal length lowers the score`() {
        // The stated example: 45 minutes with no breaks versus 45 minutes chopped into five.
        val clean = LocalSessionRater.score(summary(plannedPauseCount = 0))
        val chopped = LocalSessionRater.score(summary(plannedPauseCount = 5, plannedPausesTaken = 5))

        assertTrue("5 breaks in 45 minutes should cost", chopped < clean)
        assertEquals(15, clean - chopped)
    }

    @Test
    fun `skipping planned pauses costs discipline points`() {
        val took = LocalSessionRater.score(summary(plannedPauseCount = 2, plannedPausesTaken = 2))
        val skipped = LocalSessionRater.score(summary(plannedPauseCount = 2, plannedPausesTaken = 0))

        assertEquals(15, took - skipped)
    }

    // --- unplanned pauses ---------------------------------------------------------------

    @Test
    fun `a brief unplanned pause is forgiven`() {
        val clean = LocalSessionRater.score(summary())
        val quickCall = LocalSessionRater.score(
            summary(unplannedPauseCount = 1, unplannedPauseSeconds = 90),
        )

        assertEquals(clean, quickCall)
    }

    @Test
    fun `unplanned pauses cost in proportion to their length`() {
        val short = LocalSessionRater.score(
            summary(unplannedPauseCount = 1, unplannedPauseSeconds = 5 * 60),
        )
        val long = LocalSessionRater.score(
            summary(unplannedPauseCount = 1, unplannedPauseSeconds = 20 * 60),
        )

        assertTrue("a longer break should cost more", long < short)
    }

    @Test
    fun `many short breaks are worse than one of the same total length`() {
        val one = LocalSessionRater.score(
            summary(unplannedPauseCount = 1, unplannedPauseSeconds = 10 * 60),
        )
        val many = LocalSessionRater.score(
            summary(unplannedPauseCount = 6, unplannedPauseSeconds = 10 * 60),
        )

        assertTrue("a fragmented session should score lower", many < one)
    }

    // --- environment --------------------------------------------------------------------

    @Test
    fun `a brief environmental problem is ignored`() {
        val clean = LocalSessionRater.score(summary())
        val steppedOut = LocalSessionRater.score(summary(movementFraction = 0.15))

        assertEquals(clean, steppedOut)
    }

    @Test
    fun `movement matters more than noise and noise more than light`() {
        val clean = LocalSessionRater.score(summary())
        val moved = clean - LocalSessionRater.score(summary(movementFraction = 1.0))
        val loud = clean - LocalSessionRater.score(summary(loudFraction = 1.0))
        val dark = clean - LocalSessionRater.score(summary(darkFraction = 1.0))

        assertTrue("movement $moved should outweigh noise $loud", moved > loud)
        assertTrue("noise $loud should outweigh light $dark", loud > dark)
        assertTrue("light should barely matter, cost $dark", dark <= 5)
    }

    // --- bounds -------------------------------------------------------------------------

    @Test
    fun `the score always stays within bounds`() {
        val worst = LocalSessionRater.score(
            summary(
                goalMinutes = 60,
                focusedMinutes = 0,
                plannedPauseCount = 8,
                plannedPausesTaken = 0,
                unplannedPauseCount = 20,
                unplannedPauseSeconds = 90 * 60,
                darkFraction = 1.0,
                loudFraction = 1.0,
                movementFraction = 1.0,
            )
        )
        assertEquals(0, worst)
    }

    @Test
    fun `a session with no goal does not divide by zero`() {
        val score = LocalSessionRater.score(summary(goalMinutes = 0, focusedMinutes = 0))
        assertTrue("got $score", score in 0..100)
    }

    @Test
    fun `partial minutes still count towards the goal`() {
        val almost = SessionSummary(
            name = "Test",
            goalMinutes = 1,
            focusedSeconds = 44,
            plannedPauseCount = 0,
            plannedPausesTaken = 0,
            unplannedPauseCount = 0,
            unplannedPauseSeconds = 0,
            awaySeconds = 0,
            darkFraction = 0.0,
            loudFraction = 0.0,
            movementFraction = 0.0,
        )

        // Most of a one-minute goal, but far too short to score well.
        assertTrue(LocalSessionRater.score(almost) in 1..60)
    }

    @Test
    fun `a glance at another app is forgiven`() {
        val clean = summary()
        val glanced = summary(awaySeconds = 25)

        assertEquals(LocalSessionRater.score(clean), LocalSessionRater.score(glanced))
    }

    @Test
    fun `time spent in another app lowers the score`() {
        val focused = summary()
        val distracted = summary(awaySeconds = 10 * 60)

        assertTrue(
            "expected time away to cost points",
            LocalSessionRater.score(distracted) < LocalSessionRater.score(focused),
        )
    }

    @Test
    fun `the away penalty is capped`() {
        // Even an absurd amount of time away cannot wipe out a completed goal entirely.
        val hopeless = summary(awaySeconds = 60 * 60)

        assertTrue(LocalSessionRater.score(hopeless) >= 40)
    }
}
