package rs.etf.focusguard.ui.elements.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rs.etf.focusguard.data.room.SensorSample
import rs.etf.focusguard.ui.elements.theme.Border
import rs.etf.focusguard.ui.elements.theme.TextPrimary
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import java.time.Duration
import kotlin.math.roundToInt

/**
 * One sensor's readings across a session, drawn as a line.
 *
 * Two details that matter more than they look:
 *
 * - **Time drives the horizontal position**, not the index. Samples are only stored while the
 *   user is focusing, so a break leaves a hole in the data; spacing points evenly would
 *   quietly close that hole and draw a line through time that never happened.
 * - **The line is broken across those holes** rather than bridged, so a break reads as a gap.
 *   Drawing straight through would invent readings for minutes the app deliberately did not
 *   measure.
 *
 * The threshold line shows where the warning sits, so a shape can be read against the rule
 * rather than merely admired.
 */
@Composable
fun SensorChart(
    label: String,
    unit: String,
    samples: List<SensorSample>,
    colour: Color,
    modifier: Modifier = Modifier,
    threshold: Float? = null,
    thresholdLabel: String? = null,
) {
    if (samples.size < 2) return

    val values = samples.map { it.value }
    val lowest = minOf(values.min(), threshold ?: values.min())
    val highest = maxOf(values.max(), threshold ?: values.max())
    // A flat line would otherwise divide by zero and vanish; give it room to sit mid-height.
    val span = (highest - lowest).takeIf { it > 0.01f } ?: 1f

    val startedAt = samples.first().recordedAt
    val endedAt = samples.last().recordedAt
    val totalSeconds = Duration.between(startedAt, endedAt).seconds.coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                text = "${values.min().roundToInt()}–${values.max().roundToInt()} $unit",
                fontSize = 10.sp,
                color = TextSecondary,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 2.dp),
        ) {
            fun xOf(index: Int): Float {
                val elapsed = Duration.between(startedAt, samples[index].recordedAt).seconds
                return size.width * (elapsed.toFloat() / totalSeconds)
            }

            fun yOf(value: Float): Float = size.height * (1f - ((value - lowest) / span))

            threshold?.let { level ->
                val y = yOf(level)
                drawLine(
                    color = Border,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }

            // A gap of more than a few sampling intervals means the session was paused.
            val gapSeconds = GAP_MULTIPLE * SAMPLE_INTERVAL_SECONDS
            val path = Path()
            var drawing = false

            samples.indices.forEach { index ->
                val x = xOf(index)
                val y = yOf(samples[index].value)

                val gapBefore = index > 0 && Duration.between(
                    samples[index - 1].recordedAt,
                    samples[index].recordedAt,
                ).seconds > gapSeconds

                if (!drawing || gapBefore) {
                    path.moveTo(x, y)
                    drawing = true
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(path = path, color = colour, style = Stroke(width = 1.8.dp.toPx()))
        }

        thresholdLabel?.let {
            Text(text = it, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

private const val SAMPLE_INTERVAL_SECONDS = 10
private const val GAP_MULTIPLE = 3
