package rs.etf.focusguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.etf.focusguard.data.TopicSummary

/**
 * Averages for a group of finished sessions.
 *
 * This is what makes recording a topic worthwhile: one session says little, while a dozen on
 * the same topic shows a pattern. The label and the empty case matter because the same card
 * is shown before any session exists.
 */
class TopicSummaryTest {

    private fun summary(
        category: String? = "Studying",
        topic: String? = "Math",
        count: Int = 4,
        score: Int? = 81,
        focused: Int = 2700,
        goal: Int = 45,
    ) = TopicSummary(
        category = category,
        topic = topic,
        sessionCount = count,
        averageScore = score,
        averageFocusedSeconds = focused,
        averageGoalMinutes = goal,
        averagePlannedPauses = 2.0,
        averageUnplannedPauses = 0.5,
        darkFraction = 0.1,
        loudFraction = 0.2,
        movementFraction = 0.05,
        totalAwaySeconds = 30,
    )

    @Test
    fun `a summary with sessions is not empty`() {
        assertFalse(summary().isEmpty)
    }

    @Test
    fun `a summary without sessions is empty`() {
        assertTrue(summary(count = 0).isEmpty)
    }

    @Test
    fun `the topic is used as the label when present`() {
        assertEquals("Math", summary().label)
    }

    @Test
    fun `the category is used when there is no topic`() {
        assertEquals("Studying", summary(topic = null).label)
    }

    @Test
    fun `unfiltered summaries are labelled as all sessions`() {
        assertEquals("All sessions", summary(category = null, topic = null).label)
    }

    @Test
    fun `a topic without a category still labels by topic`() {
        assertEquals("Math", summary(category = null).label)
    }

    @Test
    fun `goal completion is the share of the goal reached`() {
        assertEquals(1.0, summary(focused = 2700, goal = 45).averageGoalCompletion, 0.0001)
    }

    @Test
    fun `falling short gives a completion below one`() {
        assertEquals(0.5, summary(focused = 1350, goal = 45).averageGoalCompletion, 0.0001)
    }

    @Test
    fun `exceeding the goal is allowed to pass one`() {
        assertTrue(summary(focused = 3600, goal = 45).averageGoalCompletion > 1.0)
    }

    @Test
    fun `a goal of zero does not divide by zero`() {
        assertEquals(0.0, summary(goal = 0).averageGoalCompletion, 0.0001)
    }

    @Test
    fun `a negative goal does not divide by zero`() {
        assertEquals(0.0, summary(goal = -10).averageGoalCompletion, 0.0001)
    }

    @Test
    fun `an empty summary keeps the filter it was built for`() {
        val empty = TopicSummary.empty("Work", "Email")

        assertEquals("Work", empty.category)
        assertEquals("Email", empty.topic)
        assertEquals("Email", empty.label)
    }

    @Test
    fun `an empty summary reports no sessions`() {
        val empty = TopicSummary.empty(null, null)

        assertTrue(empty.isEmpty)
        assertEquals(0, empty.sessionCount)
    }

    @Test
    fun `an empty summary has no average score rather than zero`() {
        assertNull(TopicSummary.empty("Studying", null).averageScore)
    }

    @Test
    fun `an empty summary reports no conditions`() {
        val empty = TopicSummary.empty(null, null)

        assertEquals(0.0, empty.darkFraction, 0.0001)
        assertEquals(0.0, empty.loudFraction, 0.0001)
        assertEquals(0.0, empty.movementFraction, 0.0001)
        assertEquals(0, empty.totalAwaySeconds)
    }

    @Test
    fun `an unscored group is distinguishable from one scoring zero`() {
        assertNull(summary(score = null).averageScore)
        assertEquals(0, summary(score = 0).averageScore)
    }
}
