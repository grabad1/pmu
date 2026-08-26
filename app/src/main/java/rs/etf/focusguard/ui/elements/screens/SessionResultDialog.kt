package rs.etf.focusguard.ui.elements.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rs.etf.focusguard.R
import rs.etf.focusguard.data.room.InterruptionCount
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.ui.elements.composables.DialogButton
import rs.etf.focusguard.ui.elements.composables.DialogButtonStyle
import rs.etf.focusguard.ui.elements.composables.FocusGuardDialog
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.TextPrimary
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import rs.etf.focusguard.util.formatHoursMinutesSeconds
import rs.etf.focusguard.util.scoreColor

/**
 * Shown as soon as a session ends, so the verdict arrives while the session still matters
 * rather than being buried in a list.
 *
 * The rating is fetched over the network, so the dialog opens immediately with the session's
 * own figures and fills in the score when it lands.
 */
@Composable
fun SessionResultDialog(
    session: Session,
    onDismiss: () -> Unit,
    interruptions: List<InterruptionCount> = emptyList(),
) {
    val rated = session.focusScore != null

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
                        text = stringResource(R.string.result_dialog_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                    )
                    Text(
                        text = session.name,
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
                if (rated) {
                    Text(
                        text = session.focusScore.toString(),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = scoreColor(session.focusScore),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Accent,
                        strokeWidth = 2.dp,
                    )
                }
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(
                    R.string.result_dialog_focused,
                    formatHoursMinutesSeconds(session.focusedSeconds),
                    session.goalMinutes,
                ),
                fontSize = 13.sp,
                color = TextSecondary,
            )

            // Listed, never scored: a message arriving is not something the user did.
            if (interruptions.isNotEmpty()) {
                val total = interruptions.sumOf { it.total }
                Text(
                    text = pluralStringResource(R.plurals.value_interruptions, total, total) +
                        " · " + interruptions.joinToString(", ") { "${it.appLabel} ×${it.total}" },
                    fontSize = 12.sp,
                    color = Accent,
                )
            }
            if (rated) {
                session.aiComment?.let { comment ->
                    Text(
                        text = comment,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                session.aiAnalysis?.let { analysis ->
                    Text(
                        text = analysis,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        color = TextSecondary,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.result_dialog_analysing),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
