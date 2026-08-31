package rs.etf.focusguard.util

import rs.etf.focusguard.data.TopicSummary
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val REPORT_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/**
 * The averages card, as a text file.
 *
 * Deliberately plain Kotlin with no Android types: the report can then be pinned by an
 * ordinary JVM unit test, which the rest of the export cannot be. It is also why the wording
 * here is hard-coded rather than taken from `strings.xml` — a saved file is a record, not a
 * screen, and it should read the same whatever the phone's language is.
 *
 * ASCII only, on purpose. The file is meant to be pulled off the device and opened in
 * whatever a marker happens to have to hand, and Notepad still guesses at encodings.
 */
fun summaryReportText(summary: TopicSummary, savedAt: LocalDateTime): String {
    val lines = mutableListOf<String>()

    lines += "FOCUS GUARD - SESSION SUMMARY"
    lines += "============================="
    lines += ""
    lines += field("Selection", summary.label)
    lines += field("Category", summary.category ?: "All")
    lines += field("Topic", summary.topic ?: "All")
    lines += field("Saved", savedAt.format(REPORT_STAMP))
    lines += field("Sessions", summary.sessionCount.toString())
    lines += ""
    lines += "AVERAGES"
    lines += field("  Score", summary.averageScore?.toString() ?: "not rated")
    lines += field("  Focus time", plainDuration(summary.averageFocusedSeconds))
    lines += field("  Goal", plainDuration(summary.averageGoalMinutes * 60))
    lines += field("  Goal reached", "${percent(summary.averageGoalCompletion)}%")
    lines += field("  Planned pauses", "%.1f".format(summary.averagePlannedPauses))
    lines += field("  Unplanned pauses", "%.1f".format(summary.averageUnplannedPauses))
    lines += ""

    // The card hides a condition at 0%; the file always states all three. On screen that
    // would be three lines saying nothing, but in a record "Loud: 0%" is a finding.
    lines += "CONDITIONS (share of readings)"
    lines += field("  Dark", "${percent(summary.darkFraction)}%")
    lines += field("  Loud", "${percent(summary.loudFraction)}%")
    lines += field("  Moving", "${percent(summary.movementFraction)}%")
    lines += ""
    lines += field("Time away from the app", plainDuration(summary.totalAwaySeconds))
    lines += ""

    return lines.joinToString(separator = "\n", postfix = "\n")
}

/**
 * `focus-guard-math-20260831-120530.txt`.
 *
 * Stamped to the second so saving twice keeps both files rather than silently overwriting the
 * first, and named after the filter so a folder of them can be told apart without opening any.
 */
fun summaryReportFileName(summary: TopicSummary, savedAt: LocalDateTime): String =
    "focus-guard-${slug(summary.label)}-${savedAt.format(FILE_STAMP)}.txt"

private fun field(label: String, value: String): String = "${label.padEnd(24)}: $value"

private fun percent(fraction: Double): Int = (fraction * 100).roundToInt()

/**
 * The same rule as the on-screen [formatDuration] — seconds below a minute, so a 39-second
 * average never reports as "0 min" — but without the string resources, which would drag a
 * Context into a file that is deliberately free of one.
 */
private fun plainDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60

    return when {
        minutes == 0 -> "$seconds s"
        seconds == 0 || minutes >= 60 -> "$minutes min"
        else -> "$minutes min $seconds s"
    }
}

/** A topic the user typed is free text, and a file name is not. */
private fun slug(label: String): String = label
    .lowercase()
    .map { if (it.isLetterOrDigit()) it else '-' }
    .joinToString("")
    .trim('-')
    .replace(Regex("-+"), "-")
    .ifEmpty { "summary" }
