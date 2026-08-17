package rs.etf.focusguard.ui.elements.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rs.etf.focusguard.R
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.ui.elements.composables.DialogButton
import rs.etf.focusguard.ui.elements.composables.DialogButtonStyle
import rs.etf.focusguard.ui.elements.composables.FocusGuardDialog
import rs.etf.focusguard.ui.elements.theme.Green
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import java.time.Duration
import java.time.Instant

/**
 * Offers a scheduled session that is due, as in the prototype's "Session Starting!" modal.
 */
@Composable
fun JoinSessionDialog(
    session: Session,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The dialog can sit open across the start time, and the session object itself never
    // changes, so a self-contained tick is what keeps the wording honest.
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(15_000)
        }
    }

    val startsAt = session.scheduledAt
    val minutesAway = startsAt?.let { Duration.between(now, it).toMinutes() } ?: 0L

    FocusGuardDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.join_dialog_title),
        actions = {
            DialogButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = DialogButtonStyle.SECONDARY,
            )
            DialogButton(
                text = stringResource(R.string.action_join_session),
                onClick = onJoin,
                style = DialogButtonStyle.PRIMARY,
                containerColorOverride = Green,
            )
        },
    ) {
        Text(
            text = when {
                minutesAway > 0 -> pluralStringResource(
                    R.plurals.join_dialog_starts_in,
                    minutesAway.toInt(),
                    session.name,
                    minutesAway,
                )

                minutesAway == 0L -> stringResource(R.string.join_dialog_starting_now, session.name)
                else -> pluralStringResource(
                    R.plurals.join_dialog_started_ago,
                    (-minutesAway).toInt(),
                    session.name,
                    -minutesAway,
                )
            },
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = TextSecondary,
        )
    }
}
