package rs.etf.focusguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.etf.focusguard.sensors.EnvironmentThresholds
import rs.etf.focusguard.sensors.PatternDetectors
import rs.etf.focusguard.sensors.ReadingWindow

/**
 * The pattern rules — how a condition behaves over time, rather than what it reads right now.
 *
 * Time is passed in, so a lamp can be made to flicker for thirty seconds in a millisecond.
 */
class PatternDetectorsTest {

    private fun window(seconds: Long) = ReadingWindow(seconds)

    private fun ReadingWindow.feed(values: List<Float>, startAt: Long = 0L, everySeconds: Long = 1L) {
        values.forEachIndexed { index, value -> add(startAt + index * everySeconds, value) }
    }

    // --- the window itself ----------------------------------------------------------------

    @Test
    fun `readings fall out of the window as time passes`() {
        val light = window(10)
        light.feed(listOf(100f, 100f, 100f), startAt = 0, everySeconds = 1)

        assertEquals(3, light.size)

        light.prune(20)
        assertEquals("everything older than the window is dropped", 0, light.size)
    }

    @Test
    fun `spread is the distance between the loudest and quietest reading`() {
        val noise = window(20)
        noise.feed(listOf(30f, 45f, 33f, 51f))

        assertEquals(21f, noise.spread(), 0.001f)
    }

    // --- flickering light -----------------------------------------------------------------

    @Test
    fun `a steady room is not flickering`() {
        val light = window(EnvironmentThresholds.FLICKER_WINDOW_SECONDS)
        light.feed(listOf(300f, 305f, 298f, 302f, 299f, 301f))

        assertFalse(PatternDetectors.isFlickering(light))
    }

    @Test
    fun `a lamp switched off once is not flickering`() {
        val light = window(EnvironmentThresholds.FLICKER_WINDOW_SECONDS)
        light.feed(listOf(300f, 300f, 4f, 4f, 4f))

        assertFalse("one change is a decision, not a fault", PatternDetectors.isFlickering(light))
    }

    @Test
    fun `a lamp that keeps cutting in and out is flickering`() {
        val light = window(EnvironmentThresholds.FLICKER_WINDOW_SECONDS)
        light.feed(listOf(300f, 5f, 290f, 6f, 310f, 4f))

        assertTrue(PatternDetectors.isFlickering(light))
    }

    @Test
    fun `flicker is judged relatively so bright rooms are not penalised for drifting`() {
        val light = window(EnvironmentThresholds.FLICKER_WINDOW_SECONDS)
        // A 60-lux drift is large in absolute terms and trivial at this brightness.
        light.feed(listOf(600f, 660f, 610f, 655f, 620f, 640f))

        assertFalse(PatternDetectors.isFlickering(light))
    }

    @Test
    fun `flickering stops counting once the swings age out of the window`() {
        val light = window(EnvironmentThresholds.FLICKER_WINDOW_SECONDS)
        light.feed(listOf(300f, 5f, 290f, 6f, 310f, 4f))
        assertTrue(PatternDetectors.isFlickering(light))

        light.prune(EnvironmentThresholds.FLICKER_WINDOW_SECONDS + 100)
        assertFalse("a room that settled is no longer flickering", PatternDetectors.isFlickering(light))
    }

    // --- restless noise -------------------------------------------------------------------

    @Test
    fun `a steady hum is not restless however loud it is`() {
        val noise = window(EnvironmentThresholds.RESTLESS_WINDOW_SECONDS)
        noise.feed(listOf(55f, 56f, 54f, 55f, 57f, 55f))

        assertFalse("a fan is easy to work through", PatternDetectors.isRestless(noise))
    }

    @Test
    fun `speech starting and stopping is restless`() {
        val noise = window(EnvironmentThresholds.RESTLESS_WINDOW_SECONDS)
        noise.feed(listOf(32f, 58f, 31f, 61f, 34f, 57f))

        assertTrue(PatternDetectors.isRestless(noise))
    }

    @Test
    fun `a silent room is never restless`() {
        val noise = window(EnvironmentThresholds.RESTLESS_WINDOW_SECONDS)
        // Below the floor: whatever variation there is here is measurement noise.
        noise.feed(listOf(2f, 14f, 3f, 15f, 1f, 16f))

        assertFalse(PatternDetectors.isRestless(noise))
    }

    @Test
    fun `a room that simply becomes louder is not restless`() {
        val noise = window(EnvironmentThresholds.RESTLESS_WINDOW_SECONDS)
        // A fan switched on: a big spread, but the level steps once and then stays there.
        // Found on the emulator, where going from silence to a steady 40 was reported as
        // voices.
        noise.feed(listOf(14f, 14f, 14f, 40f, 40f, 40f, 40f))

        assertFalse("one step is a change of scene, not a conversation", PatternDetectors.isRestless(noise))
    }

    @Test
    fun `crossings count how often the level changes its mind`() {
        val noise = window(EnvironmentThresholds.RESTLESS_WINDOW_SECONDS)
        noise.feed(listOf(30f, 60f, 30f, 60f))

        assertEquals(3, noise.midpointCrossings())
    }

    // --- fidgeting ------------------------------------------------------------------------

    @Test
    fun `a single pick-up is not fidgeting`() {
        val events = window(EnvironmentThresholds.FIDGET_WINDOW_SECONDS)
        events.add(0, 1f)

        assertFalse(PatternDetectors.isFidgeting(events))
    }

    @Test
    fun `repeated pick-ups within a few minutes are fidgeting`() {
        val events = window(EnvironmentThresholds.FIDGET_WINDOW_SECONDS)
        repeat(EnvironmentThresholds.FIDGET_EVENTS) { events.add(it * 30L, 1f) }

        assertTrue(PatternDetectors.isFidgeting(events))
    }

    @Test
    fun `pick-ups spread over a long session do not count as fidgeting`() {
        val events = window(EnvironmentThresholds.FIDGET_WINDOW_SECONDS)
        // The same number of pick-ups, but hours apart rather than minutes.
        repeat(EnvironmentThresholds.FIDGET_EVENTS) { events.add(it * 3_600L, 1f) }
        events.prune(EnvironmentThresholds.FIDGET_EVENTS * 3_600L)

        assertFalse(PatternDetectors.isFidgeting(events))
    }
}
