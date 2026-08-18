package rs.etf.focusguard.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import rs.etf.focusguard.R
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

/**
 * A duration in words, never rounded down to nothing.
 *
 * Whole minutes are fine for a goal the user typed, but not for a measured duration: a
 * 16-second pause displayed as "0 min" reads as though it was not recorded, and a 39-second
 * session as though no work happened. Seconds are shown below a minute, and alongside minutes
 * up to an hour, after which they stop being interesting.
 */
@Composable
fun formatDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60

    return when {
        minutes == 0 -> stringResource(R.string.value_seconds, seconds)
        seconds == 0 || minutes >= 60 -> stringResource(R.string.value_minutes, minutes)
        else -> stringResource(R.string.value_minutes_seconds, minutes, seconds)
    }
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
 *
 * Compares seconds rather than whole minutes: 235 s against a 180 s goal is overtime, but
 * both round to "3 min" and would otherwise lose the distinction.
 */
fun actualTimeColor(actualSeconds: Int, goalSeconds: Int): Color {
    if (goalSeconds <= 0) return Blue
    if (actualSeconds > goalSeconds) return Blue

    val hue = min(actualSeconds.toFloat() / goalSeconds, 1f) * 118f
    return Color.hsl(hue = hue, saturation = 0.88f, lightness = 0.55f)
}
