package rs.etf.focusguard.util

import androidx.compose.ui.graphics.Color
import rs.etf.focusguard.ui.elements.theme.Blue
import rs.etf.focusguard.ui.elements.theme.Green
import rs.etf.focusguard.ui.elements.theme.Red
import rs.etf.focusguard.ui.elements.theme.Yellow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.min

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun Instant.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())

fun LocalDateTime.toInstant(): Instant = atZone(ZoneId.systemDefault()).toInstant()

fun LocalDate.atTimeToInstant(time: LocalTime): Instant = LocalDateTime.of(this, time).toInstant()

fun formatDate(instant: Instant): String = instant.toLocalDateTime().format(DATE_FORMATTER)

fun formatTime(instant: Instant): String = instant.toLocalDateTime().format(TIME_FORMATTER)

/** `HH:MM:SS`, as shown on the session stopwatch. */
fun formatHoursMinutesSeconds(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(safe / 3600, (safe % 3600) / 60, safe % 60)
}

/** `MM:SS`, as shown for pause countdowns and in the pause log. */
fun formatMinutesSeconds(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

/** Green at or above 70, yellow from 40, red below — matching the prototype. */
fun scoreColor(score: Int): Color = when {
    score >= 70 -> Green
    score >= 40 -> Yellow
    else -> Red
}

/**
 * Red through to green as focus time approaches the goal, and blue once it is exceeded.
 * The prototype interpolates hue over 0..118 degrees at 88% saturation, 55% lightness.
 */
fun actualTimeColor(actualMinutes: Int, goalMinutes: Int): Color {
    if (goalMinutes <= 0) return Blue
    if (actualMinutes > goalMinutes) return Blue

    val hue = min(actualMinutes.toFloat() / goalMinutes, 1f) * 118f
    return Color.hsl(hue = hue, saturation = 0.88f, lightness = 0.55f)
}
