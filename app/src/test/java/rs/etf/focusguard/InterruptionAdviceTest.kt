package rs.etf.focusguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.etf.focusguard.data.LocalSessionRater
import rs.etf.focusguard.data.SessionSummary
import rs.etf.focusguard.data.room.InterruptionCount

/**
 * Interruptions are recorded so the user can be advised, never so they can be punished.
 * These tests pin both halves of that promise.
 */
class InterruptionAdviceTest {

    private fun app(label: String, notifications: Int = 0, calls: Int = 0) = InterruptionCount(
        packageName = "com.example.${label.lowercase()}",
        appLabel = label,
        calls = calls,
        notifications = notifications,
    )

    private fun summary(
        interruptions: List<InterruptionCount> = emptyList(),
        goalMinutes: Int = 45,
        focusedMinutes: Int = 45,
    ) = SessionSummary(
        name = "Test",
        goalMinutes = goalMinutes,
        focusedSeconds = focusedMinutes * 60,
        plannedPauseCount = 0,
        plannedPausesTaken = 0,
        unplannedPauseCount = 0,
        unplannedPauseSeconds = 0,
        awaySeconds = 0,
        darkFraction = 0.0,
        loudFraction = 0.0,
        movementFraction = 0.0,
        interruptions = interruptions,
    )

    // --- the promise: never scored ------------------------------------------------------

    @Test
    fun `interruptions do not change the score`() {
        val clean = summary()
        val hounded = summary(
            interruptions = listOf(
                app("WhatsApp", notifications = 9),
                app("Instagram", notifications = 6),
                app("Phone", calls = 3),
            ),
        )

        assertEquals(
            "an interruption is not something the user did",
            LocalSessionRater.score(clean),
            LocalSessionRater.score(hounded),
        )
    }

    // --- the point: useful advice -------------------------------------------------------

    @Test
    fun `a quiet session gets no advice`() {
        assertNull(summary().interruptionAdvice)
    }

    @Test
    fun `one or two interruptions are treated as ordinary life`() {
        assertNull(summary(interruptions = listOf(app("WhatsApp", notifications = 2))).interruptionAdvice)
    }

    @Test
    fun `a dominant app is named so it can be muted`() {
        val advice = summary(
            interruptions = listOf(app("WhatsApp", notifications = 6), app("Gmail", notifications = 1)),
        ).interruptionAdvice

        assertNotNull(advice)
        assertTrue("advice was: $advice", advice!!.contains("WhatsApp"))
        assertTrue("advice was: $advice", advice.contains("6"))
    }

    @Test
    fun `interruptions spread across apps suggest Do Not Disturb instead`() {
        val advice = summary(
            interruptions = listOf(
                app("WhatsApp", notifications = 2),
                app("Gmail", notifications = 2),
                app("Slack", notifications = 2),
            ),
        ).interruptionAdvice

        assertNotNull(advice)
        assertTrue("advice was: $advice", advice!!.contains("Do Not Disturb"))
    }

    @Test
    fun `calls and notifications both count towards the total`() {
        val session = summary(
            interruptions = listOf(app("Phone", calls = 2), app("Gmail", notifications = 2)),
        )

        assertEquals(4, session.interruptionCount)
        assertEquals(2, session.callCount)
    }

    @Test
    fun `the worst interrupter needs at least two to stand out`() {
        val onlyOnes = summary(
            interruptions = listOf(app("Gmail", notifications = 1), app("Slack", notifications = 1)),
        )

        assertNull("a single notification does not make an app the culprit", onlyOnes.worstInterrupter)
    }
}
