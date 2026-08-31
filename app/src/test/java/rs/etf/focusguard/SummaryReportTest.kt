package rs.etf.focusguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.etf.focusguard.data.TopicSummary
import rs.etf.focusguard.util.summaryReportFileName
import rs.etf.focusguard.util.summaryReportText
import java.time.LocalDateTime

class SummaryReportTest {

    private val savedAt = LocalDateTime.of(2026, 8, 31, 12, 5, 30)

    private fun summary(
        category: String? = "Studying",
        topic: String? = "Math",
        sessionCount: Int = 12,
        averageScore: Int? = 62,
        averageFocusedSeconds: Int = 2472,
        averageGoalMinutes: Int = 45,
        averagePlannedPauses: Double = 2.0,
        averageUnplannedPauses: Double = 0.8,
        darkFraction: Double = 0.18,
        loudFraction: Double = 0.0,
        movementFraction: Double = 0.07,
        totalAwaySeconds: Int = 125,
    ) = TopicSummary(
        category = category,
        topic = topic,
        sessionCount = sessionCount,
        averageScore = averageScore,
        averageFocusedSeconds = averageFocusedSeconds,
        averageGoalMinutes = averageGoalMinutes,
        averagePlannedPauses = averagePlannedPauses,
        averageUnplannedPauses = averageUnplannedPauses,
        darkFraction = darkFraction,
        loudFraction = loudFraction,
        movementFraction = movementFraction,
        totalAwaySeconds = totalAwaySeconds,
    )

    @Test
    fun `the report carries every number the card shows`() {
        val report = summaryReportText(summary(), savedAt)

        assertTrue(report.contains("Selection               : Math"))
        assertTrue(report.contains("Category                : Studying"))
        assertTrue(report.contains("Topic                   : Math"))
        assertTrue(report.contains("Sessions                : 12"))
        assertTrue(report.contains("Score                 : 62"))
        assertTrue(report.contains("Focus time            : 41 min 12 s"))
        assertTrue(report.contains("Planned pauses        : 2.0"))
        assertTrue(report.contains("Unplanned pauses      : 0.8"))
        assertTrue(report.contains("Dark                  : 18%"))
        assertTrue(report.contains("Moving                : 7%"))
        assertTrue(report.contains("Time away from the app  : 2 min 5 s"))
    }

    @Test
    fun `the time it was saved is recorded`() {
        assertTrue(summaryReportText(summary(), savedAt).contains("2026-08-31 12:05"))
    }

    /** On screen a 0% condition is hidden; in a file it is a finding worth stating. */
    @Test
    fun `a condition at zero is still reported`() {
        assertTrue(summaryReportText(summary(), savedAt).contains("Loud                  : 0%"))
    }

    @Test
    fun `an unfiltered summary says so rather than leaving blanks`() {
        val report = summaryReportText(summary(category = null, topic = null), savedAt)

        assertTrue(report.contains("Selection               : All sessions"))
        assertTrue(report.contains("Category                : All"))
        assertTrue(report.contains("Topic                   : All"))
    }

    @Test
    fun `sessions with no rating yet do not print a null`() {
        val report = summaryReportText(summary(averageScore = null), savedAt)

        assertTrue(report.contains("Score                 : not rated"))
        assertFalse(report.contains("null"))
    }

    /** A measured average below a minute must never read as "0 min" — the app's rule. */
    @Test
    fun `a short average is reported in seconds`() {
        val report = summaryReportText(summary(averageFocusedSeconds = 39), savedAt)

        assertTrue(report.contains("Focus time            : 39 s"))
    }

    @Test
    fun `goal completion is a percentage of the goal`() {
        // 2472 s against a 45 min (2700 s) goal is 91.6%, rounded.
        assertTrue(summaryReportText(summary(), savedAt).contains("Goal reached          : 92%"))
    }

    @Test
    fun `the report is plain ASCII so any editor can open it`() {
        assertTrue(summaryReportText(summary(), savedAt).all { it.code < 128 })
    }

    @Test
    fun `the file name carries the filter and the time`() {
        assertEquals(
            "focus-guard-math-20260831-120530.txt",
            summaryReportFileName(summary(), savedAt),
        )
    }

    @Test
    fun `a topic with spaces and punctuation still makes a legal file name`() {
        val name = summaryReportFileName(summary(topic = "Discrete Math / Ch. 3"), savedAt)

        assertEquals("focus-guard-discrete-math-ch-3-20260831-120530.txt", name)
    }

    @Test
    fun `an unfiltered report is named for all sessions`() {
        val name = summaryReportFileName(summary(category = null, topic = null), savedAt)

        assertEquals("focus-guard-all-sessions-20260831-120530.txt", name)
    }
}
