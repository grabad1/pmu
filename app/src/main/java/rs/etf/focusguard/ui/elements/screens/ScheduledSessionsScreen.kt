package rs.etf.focusguard.ui.elements.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.etf.focusguard.R
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.ui.elements.composables.Badge
import rs.etf.focusguard.ui.elements.composables.DetailItem
import rs.etf.focusguard.ui.elements.composables.DetailRow
import rs.etf.focusguard.ui.elements.composables.DialogButton
import rs.etf.focusguard.ui.elements.composables.DialogButtonStyle
import rs.etf.focusguard.ui.elements.composables.EmptyState
import rs.etf.focusguard.ui.elements.composables.FocusCard
import rs.etf.focusguard.ui.elements.composables.FocusGuardDialog
import rs.etf.focusguard.ui.elements.composables.ScreenHeader
import rs.etf.focusguard.ui.elements.theme.TextPrimary
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import rs.etf.focusguard.ui.elements.theme.TextTertiary
import rs.etf.focusguard.ui.elements.theme.Yellow
import rs.etf.focusguard.ui.stateholders.ScheduledSessionItem
import rs.etf.focusguard.ui.stateholders.ScheduledSessionsViewModel
import rs.etf.focusguard.util.formatDate
import rs.etf.focusguard.util.formatTime

@Composable
fun ScheduledSessionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduledSessionsViewModel = hiltViewModel(),
) {
    val sessions by viewModel.scheduledSessions.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedSessionId.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.scheduled_sessions_title), onBack = onBack)

        if (sessions.isEmpty()) {
            EmptyState(icon = "\uD83D\uDCC5", text = stringResource(R.string.scheduled_empty))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = sessions, key = { it.session.id }) { item ->
                    ScheduledSessionCard(
                        item = item,
                        onClick = { viewModel.select(item.session.id) },
                    )
                }
            }
        }
    }

    selectedId
        ?.let { id -> sessions.firstOrNull { it.session.id == id } }
        ?.let { item ->
            ScheduledSessionDialog(
                item = item,
                onCancelSession = { viewModel.cancel(item.session) },
                onDismiss = viewModel::dismissDialog,
            )
        }
}

@Composable
private fun ScheduledSessionCard(
    item: ScheduledSessionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = item.session

    FocusCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = session.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                // A missed session is history, not a plan, so it recedes.
                color = if (item.isMissed) TextSecondary else TextPrimary,
                modifier = Modifier.weight(1f),
            )
            session.scheduledAt?.let { at ->
                if (item.isMissed) {
                    Badge(
                        text = stringResource(R.string.scheduled_missed),
                        containerColor = Yellow.copy(alpha = 0.15f),
                        contentColor = Yellow,
                    )
                } else {
                    Badge(text = "${formatDate(at)}  ${formatTime(at)}")
                }
            }
        }

        SessionPlanDetails(session)

        Text(
            text = if (item.isMissed) {
                stringResource(
                    R.string.scheduled_missed_footer,
                    session.scheduledAt?.let { "${formatDate(it)} ${formatTime(it)}" }.orEmpty(),
                )
            } else {
                stringResource(R.string.scheduled_card_footer)
            },
            fontSize = 11.sp,
            color = TextTertiary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun SessionPlanDetails(session: Session, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailRow(
            left = {
                DetailItem(
                    label = stringResource(R.string.detail_goal_time),
                    value = stringResource(R.string.value_minutes, session.goalMinutes),
                )
            },
            right = {
                DetailItem(
                    label = stringResource(R.string.detail_planned_total),
                    value = stringResource(R.string.value_minutes, session.plannedTotalMinutes),
                )
            },
        )
        DetailRow(
            left = {
                DetailItem(
                    label = stringResource(R.string.detail_pauses),
                    value = pluralStringResource(
                        R.plurals.value_pauses,
                        session.plannedPauseCount,
                        session.plannedPauseCount,
                    ),
                )
            },
            right = {
                DetailItem(
                    label = stringResource(R.string.detail_pause_duration),
                    value = stringResource(R.string.value_minutes_each, session.plannedPauseMinutes),
                )
            },
        )
    }
}

/**
 * Details of one scheduled session, and the only way to get rid of it.
 *
 * Without this a scheduled session could never be removed: the cards were not clickable, so a
 * mistyped time or a session that was missed stayed on the list for good.
 */
@Composable
private fun ScheduledSessionDialog(
    item: ScheduledSessionItem,
    onCancelSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    val session = item.session

    FocusGuardDialog(
        onDismissRequest = onDismiss,
        title = session.name,
        actions = {
            DialogButton(
                text = stringResource(R.string.action_close),
                onClick = onDismiss,
                style = DialogButtonStyle.SECONDARY,
            )
            DialogButton(
                text = stringResource(
                    if (item.isMissed) R.string.action_remove else R.string.action_cancel_session
                ),
                onClick = onCancelSession,
                style = DialogButtonStyle.DANGER,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            session.scheduledAt?.let { at ->
                Text(
                    text = "${formatDate(at)} · ${formatTime(at)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isMissed) Yellow else TextPrimary,
                )
            }
            SessionPlanDetails(session)
            Text(
                text = stringResource(
                    if (item.isMissed) {
                        R.string.scheduled_dialog_missed_body
                    } else {
                        R.string.scheduled_dialog_body
                    }
                ),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = TextSecondary,
            )
        }
    }
}
