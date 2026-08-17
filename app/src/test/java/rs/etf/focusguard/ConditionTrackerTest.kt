package rs.etf.focusguard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.etf.focusguard.sensors.ConditionTracker

class ConditionTrackerTest {

    private fun tracker(sustain: Long = 10, cooldown: Long = 60) =
        ConditionTracker(sustainSeconds = sustain, cooldownSeconds = cooldown)

    @Test
    fun `a brief violation never warns`() {
        val tracker = tracker(sustain = 10)

        assertFalse(tracker.update(violating = true, nowSeconds = 0))
        assertFalse(tracker.update(violating = true, nowSeconds = 5))
        assertFalse(tracker.update(violating = false, nowSeconds = 6))
        assertFalse(tracker.update(violating = true, nowSeconds = 12))
    }

    @Test
    fun `warns once the condition has been sustained`() {
        val tracker = tracker(sustain = 10)

        assertFalse(tracker.update(violating = true, nowSeconds = 0))
        assertFalse(tracker.update(violating = true, nowSeconds = 9))
        assertTrue(tracker.update(violating = true, nowSeconds = 10))
    }

    @Test
    fun `does not warn again during the cooldown`() {
        val tracker = tracker(sustain = 5, cooldown = 60)

        assertFalse(tracker.update(violating = true, nowSeconds = 0))
        assertTrue(tracker.update(violating = true, nowSeconds = 5))

        // Still bad, but too soon to say so again.
        assertFalse(tracker.update(violating = true, nowSeconds = 20))
        assertFalse(tracker.update(violating = true, nowSeconds = 60))
    }

    @Test
    fun `warns again once the cooldown has elapsed and the problem persists`() {
        val tracker = tracker(sustain = 5, cooldown = 60)

        assertFalse(tracker.update(violating = true, nowSeconds = 0))
        assertTrue(tracker.update(violating = true, nowSeconds = 5))
        assertTrue(tracker.update(violating = true, nowSeconds = 70))
    }

    @Test
    fun `recovering resets the sustain window`() {
        val tracker = tracker(sustain = 10)

        assertFalse(tracker.update(violating = true, nowSeconds = 0))
        assertFalse(tracker.update(violating = true, nowSeconds = 9))
        // Conditions improve, so the clock starts over.
        assertFalse(tracker.update(violating = false, nowSeconds = 10))
        assertFalse(tracker.update(violating = true, nowSeconds = 11))
        assertFalse(tracker.update(violating = true, nowSeconds = 19))
        assertTrue(tracker.update(violating = true, nowSeconds = 21))
    }

    @Test
    fun `reset clears both the sustain window and the cooldown`() {
        val tracker = tracker(sustain = 5, cooldown = 60)

        assertFalse(tracker.update(violating = true, nowSeconds = 0))
        assertTrue(tracker.update(violating = true, nowSeconds = 5))

        tracker.reset()

        assertFalse(tracker.update(violating = true, nowSeconds = 6))
        assertTrue(tracker.update(violating = true, nowSeconds = 11))
    }

    @Test
    fun `a zero sustain window warns on the first violating reading`() {
        // Movement is configured this way: handling a phone is a burst, not a state.
        val tracker = tracker(sustain = 0, cooldown = 60)

        assertTrue(tracker.update(violating = true, nowSeconds = 100))
        assertFalse(tracker.update(violating = true, nowSeconds = 130))
        assertTrue(tracker.update(violating = true, nowSeconds = 160))
    }

    @Test
    fun `a continuing problem does not warn on every reading`() {
        val tracker = tracker(sustain = 5, cooldown = 30)
        var warnings = 0

        // One reading per second for two minutes of an unbroken problem.
        for (second in 0..120) {
            if (tracker.update(violating = true, nowSeconds = second.toLong())) warnings++
        }

        // First at 5s, then every 30s after: 5, 35, 65, 95.
        assertTrue("expected a handful of warnings, got $warnings", warnings in 3..5)
    }
}
