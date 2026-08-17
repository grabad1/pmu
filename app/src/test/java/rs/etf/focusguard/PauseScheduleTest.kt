package rs.etf.focusguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.etf.focusguard.util.plannedPauseOffsetsSeconds

class PauseScheduleTest {

    @Test
    fun `single pause lands halfway through the goal`() {
        assertEquals(listOf(30 * 60), plannedPauseOffsetsSeconds(goalMinutes = 60, pauseCount = 1))
    }

    @Test
    fun `six pauses in sixty minutes break every eight and a half minutes`() {
        val offsets = plannedPauseOffsetsSeconds(goalMinutes = 60, pauseCount = 6)

        assertEquals(6, offsets.size)
        // 3600 / 7 = 514.28..., floored and accumulated
        assertEquals(listOf(514, 1028, 1542, 2057, 2571, 3085), offsets)
    }

    @Test
    fun `no pause is scheduled at or after the goal`() {
        val goalSeconds = 45 * 60
        val offsets = plannedPauseOffsetsSeconds(goalMinutes = 45, pauseCount = 5)

        assertTrue(offsets.all { it < goalSeconds })
        assertTrue(offsets.all { it > 0 })
    }

    @Test
    fun `offsets are strictly increasing`() {
        val offsets = plannedPauseOffsetsSeconds(goalMinutes = 90, pauseCount = 4)

        assertEquals(offsets.sorted(), offsets)
        assertEquals(offsets.distinct(), offsets)
    }

    @Test
    fun `zero pauses yields nothing`() {
        assertTrue(plannedPauseOffsetsSeconds(goalMinutes = 60, pauseCount = 0).isEmpty())
    }

    @Test
    fun `negative or zero goal yields nothing`() {
        assertTrue(plannedPauseOffsetsSeconds(goalMinutes = 0, pauseCount = 3).isEmpty())
        assertTrue(plannedPauseOffsetsSeconds(goalMinutes = -5, pauseCount = 3).isEmpty())
    }

    @Test
    fun `more pauses than minutes does not produce duplicates`() {
        val offsets = plannedPauseOffsetsSeconds(goalMinutes = 1, pauseCount = 90)

        assertEquals(offsets.distinct(), offsets)
        assertTrue(offsets.all { it in 1 until 60 })
    }
}
