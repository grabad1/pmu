package rs.etf.focusguard.ui.elements.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rs.etf.focusguard.R
import rs.etf.focusguard.data.SessionDetail
import rs.etf.focusguard.data.room.Pause
import rs.etf.focusguard.data.room.PauseType
import rs.etf.focusguard.data.room.SessionWithPauses
import rs.etf.focusguard.ui.elements.composables.Badge
import rs.etf.focusguard.ui.elements.composables.DialogButton
import rs.etf.focusguard.ui.elements.composables.DialogButtonStyle
import rs.etf.focusguard.ui.elements.composables.FocusGuardDialog
import rs.etf.focusguard.ui.elements.theme.Border
import rs.etf.focusguard.ui.elements.theme.Card
import rs.etf.focusguard.ui.elements.theme.Green
import rs.etf.focusguard.ui.elements.theme.TextPrimary
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import rs.etf.focusguard.ui.elements.theme.Yellow
import rs.etf.focusguard.util.formatDuration
import rs.etf.focusguard.util.formatMinutesSeconds
import rs.etf.focusguard.util.scoreColor

@Composable
fun PauseLogDialog(
    item: SessionWithPauses,
    onDismiss: () -> Unit,
) {
    FocusGuardDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.pause_log_title),
        actions = {
            DialogButton(
                text = stringResource(R.string.action_close),
                onClick = onDismiss,
                style = DialogButtonStyle.SECONDARY,
            )
        },
    ) {
        if (item.pauses.isEmpty()) {
            Text(
                text = stringResource(R.string.pause_log_empty),
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.pauses.forEachIndexed { index, pause ->
                    PauseLogItem(index = index, pause = pause)
                }
            }
        }
    }
}

@Composable
private fun PauseLogItem(index: Int, pause: Pause, modifier: Modifier = Modifier) {
    val planned = pause.type == PauseType.PLANNED
    val accent = if (planned) Green else Yellow

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = Card,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.pause_log_item, index + 1),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.sp,
                )
                Badge(
                    text = if (planned) {
                        stringResource(R.string.pause_planned)
                    } else {
                        stringResource(R.string.pause_unplanned)
                    },
                    containerColor = accent.copy(alpha = 0.15f),
                    contentColor = accent,
                )
            }
            LabelledValue(
                label = stringResource(R.string.pause_log_duration),
                value = formatDuration(pause.durationSeconds),
            )
            LabelledValue(
                label = stringResource(R.string.pause_log_time),
                value = "${formatMinutesSeconds(pause.startOffsetSeconds)} – " +
                    formatMinutesSeconds(pause.startOffsetSeconds + pause.durationSeconds),
            )
        }
    }
}

@Composable
private fun LabelledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(top = 2.dp)) {
        Text(text = "$label ", fontSize = 11.sp, color = TextSecondary)
        Text(text = value, fontSize = 11.sp, color = TextPrimary)
    }
}

@Composable
fun AnalysisDialog(
    item: SessionWithPauses,
    detail: SessionDetail?,
    onDismiss: () -> Unit,
) {
    val session = item.session

    FocusGuardDialog(
        onDismissRequest = onDismiss,
        header = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                    )
                    listOfNotNull(session.category, session.topic)
                        .takeIf { it.isNotEmpty() }
                        ?.let { labels ->
                            Text(
                                text = labels.joinToString(" · "),
                                fontSize = 11.sp,
                                color = TextSecondary,
                            )
                        }
                }
                Text(
                    text = session.focusScore?.toString() ?: "—",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = session.focusScore?.let(::scoreColor) ?: TextSecondary,
                )
            }
        },
        actions = {
            DialogButton(
                text = stringResource(R.string.action_close),
                onClick = onDismiss,
                style = DialogButtonStyle.SECONDARY,
            )
        },
    ) {
        SessionDetailTabs(
            comment = session.aiComment,
            analysis = session.aiAnalysis,
            detail = detail,
        )
    }
}
