package rs.etf.focusguard.ui.elements.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rs.etf.focusguard.R
import rs.etf.focusguard.data.SessionDetail
import rs.etf.focusguard.data.room.InterruptionCount
import rs.etf.focusguard.data.room.SensorKind
import rs.etf.focusguard.sensors.EnvironmentThresholds
import rs.etf.focusguard.ui.elements.composables.SensorChart
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.AccentDim
import rs.etf.focusguard.ui.elements.theme.Blue
import rs.etf.focusguard.ui.elements.theme.Border
import rs.etf.focusguard.ui.elements.theme.Card
import rs.etf.focusguard.ui.elements.theme.Red
import rs.etf.focusguard.ui.elements.theme.TextPrimary
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import rs.etf.focusguard.ui.elements.theme.TextTertiary
import rs.etf.focusguard.ui.elements.theme.Yellow
import kotlin.math.roundToInt

private enum class DetailTab(val titleResId: Int) {
    ANALYSIS(R.string.tab_analysis),
    CONDITIONS(R.string.tab_conditions),
    INTERRUPTIONS(R.string.tab_interruptions),
}

/**
 * The three views of a finished session: what the AI made of it, what the room was doing, and
 * who interrupted it.
 *
 * One composable used both by the result shown when a session ends and by the history screen,
 * so the two cannot drift apart. [detail] is null while the session's history is still being
 * read, which is a moment rather than a wait.
 */
@Composable
fun SessionDetailTabs(
    comment: String?,
    analysis: String?,
    detail: SessionDetail?,
    modifier: Modifier = Modifier,
    pendingText: String? = null,
) {
    var tab by rememberSaveable { mutableStateOf(DetailTab.ANALYSIS) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TabBar(selected = tab, onSelect = { tab = it })

        when (tab) {
            DetailTab.ANALYSIS -> AnalysisTab(comment, analysis, pendingText)
            DetailTab.CONDITIONS -> ConditionsTab(detail)
            DetailTab.INTERRUPTIONS -> InterruptionsTab(detail)
        }
    }
}

@Composable
private fun TabBar(
    selected: DetailTab,
    onSelect: (DetailTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DetailTab.entries.forEach { entry ->
            val active = entry == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (active) AccentDim else Card,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(entry) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(entry.titleResId),
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) Accent else TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun AnalysisTab(comment: String?, analysis: String?, pendingText: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = comment ?: pendingText ?: stringResource(R.string.analysis_pending_comment),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Text(
            text = analysis ?: stringResource(R.string.analysis_pending_detail),
            fontSize = 12.sp,
            lineHeight = 20.sp,
            color = TextSecondary,
        )
    }
}

/**
 * What the room was doing, as lines rather than percentages alone.
 *
 * The derived measures are shown beside the raw ones, because "the light averaged 40 lux"
 * and "the light changed fifteen times" describe very different rooms.
 */
@Composable
private fun ConditionsTab(detail: SessionDetail?) {
    if (detail == null) {
        Text(
            text = stringResource(R.string.detail_loading),
            fontSize = 12.sp,
            color = TextSecondary,
        )
        return
    }

    if (!detail.hasCharts) {
        Text(
            text = stringResource(R.string.conditions_too_short),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = TextSecondary,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ShareRow(detail)

        SensorChart(
            label = stringResource(R.string.chart_light),
            unit = "lx",
            samples = detail.samplesOf(SensorKind.LIGHT),
            colour = Yellow,
            threshold = EnvironmentThresholds.DARK_LUX,
            thresholdLabel = stringResource(R.string.chart_light_threshold),
        )
        SensorChart(
            label = stringResource(R.string.chart_noise),
            unit = "dB",
            samples = detail.samplesOf(SensorKind.NOISE),
            colour = Blue,
            threshold = EnvironmentThresholds.LOUD_DB,
            thresholdLabel = stringResource(R.string.chart_noise_threshold),
        )
        SensorChart(
            label = stringResource(R.string.chart_motion),
            unit = "m/s²",
            samples = detail.samplesOf(SensorKind.MOTION),
            colour = Red,
            threshold = EnvironmentThresholds.MOVEMENT_MS2,
            thresholdLabel = stringResource(R.string.chart_motion_threshold),
        )

        // The two patterns worth seeing over time; pick-ups are summarised in the row above.
        SensorChart(
            label = stringResource(R.string.chart_light_variability),
            unit = "",
            samples = detail.samplesOf(SensorKind.LIGHT_VARIABILITY),
            colour = Yellow,
            threshold = EnvironmentThresholds.FLICKER_SWINGS.toFloat(),
            thresholdLabel = stringResource(R.string.chart_light_variability_threshold),
        )
        SensorChart(
            label = stringResource(R.string.chart_noise_variability),
            unit = "dB",
            samples = detail.samplesOf(SensorKind.NOISE_VARIABILITY),
            colour = Blue,
            threshold = EnvironmentThresholds.RESTLESS_SPREAD_DB,
            thresholdLabel = stringResource(R.string.chart_noise_variability_threshold),
        )

        if (detail.pauses.isNotEmpty()) {
            Text(
                text = stringResource(R.string.chart_gap_note),
                fontSize = 9.sp,
                lineHeight = 13.sp,
                color = TextTertiary,
            )
        }
    }
}

/** The three headline percentages, and the peak pick-up count beside them. */
@Composable
private fun ShareRow(detail: SessionDetail) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShareBox(stringResource(R.string.share_dark), detail.darkShare, Yellow, Modifier.weight(1f))
        ShareBox(stringResource(R.string.share_loud), detail.loudShare, Blue, Modifier.weight(1f))
        ShareBox(stringResource(R.string.share_moving), detail.movingShare, Red, Modifier.weight(1f))
    }

    if (detail.peakPickUps > 0) {
        Text(
            text = pluralStringResource(
                R.plurals.share_pick_ups,
                detail.peakPickUps,
                detail.peakPickUps,
            ),
            fontSize = 11.sp,
            color = TextSecondary,
        )
    }
}

@Composable
private fun ShareBox(
    label: String,
    share: Double,
    colour: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = Card,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "${(share * 100).roundToInt()}%",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = if (share > 0.2) colour else TextPrimary,
            )
            Text(text = label, fontSize = 9.sp, color = TextSecondary)
            // A thin bar so the three can be compared at a glance rather than read.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Border, RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(colour, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/**
 * Who interrupted the session, and what to do about it.
 *
 * Deliberately says out loud that none of this affected the score — otherwise a list of
 * failings under a number invites the assumption that one caused the other.
 */
@Composable
private fun InterruptionsTab(detail: SessionDetail?) {
    if (detail == null) {
        Text(
            text = stringResource(R.string.detail_loading),
            fontSize = 12.sp,
            color = TextSecondary,
        )
        return
    }

    if (detail.interruptions.isEmpty()) {
        Text(
            text = stringResource(R.string.interruptions_none),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = TextSecondary,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = pluralStringResource(
                R.plurals.value_interruptions,
                detail.interruptionCount,
                detail.interruptionCount,
            ),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )

        detail.interruptions.forEach { app -> InterruptionRow(app) }

        Text(
            text = stringResource(R.string.interruptions_not_scored),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = TextTertiary,
        )
    }
}

@Composable
private fun InterruptionRow(app: InterruptionCount, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = Card,
        border = BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                val parts = buildList {
                    if (app.calls > 0) {
                        add(pluralStringResource(R.plurals.interruption_calls, app.calls, app.calls))
                    }
                    if (app.notifications > 0) {
                        add(
                            pluralStringResource(
                                R.plurals.interruption_notifications,
                                app.notifications,
                                app.notifications,
                            )
                        )
                    }
                }
                Text(text = parts.joinToString(" · "), fontSize = 10.sp, color = TextSecondary)
            }
            Text(
                text = app.total.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Accent,
            )
        }
    }
}
