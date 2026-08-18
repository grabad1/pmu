package rs.etf.focusguard.ui.elements.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import rs.etf.focusguard.data.room.SessionWithPauses
import rs.etf.focusguard.ui.elements.composables.DetailItem
import rs.etf.focusguard.ui.elements.composables.DetailRow
import rs.etf.focusguard.ui.elements.composables.EmptyState
import rs.etf.focusguard.ui.elements.composables.FocusCard
import rs.etf.focusguard.ui.elements.composables.ScreenHeader
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.Border
import rs.etf.focusguard.ui.elements.theme.TextPrimary
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import rs.etf.focusguard.ui.elements.theme.TextTertiary
import rs.etf.focusguard.ui.stateholders.PreviousSessionsViewModel
import rs.etf.focusguard.util.actualTimeColor
import rs.etf.focusguard.util.formatDate
import rs.etf.focusguard.util.formatDuration
import rs.etf.focusguard.util.formatTime
import rs.etf.focusguard.util.scoreColor

@Composable
fun PreviousSessionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PreviousSessionsViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.previous_sessions_title), onBack = onBack)

        if (sessions.isEmpty()) {
            EmptyState(icon = "\uD83D\uDCCA", text = stringResource(R.string.previous_empty))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = sessions, key = { it.session.id }) { item ->
                    PreviousSessionCard(
                        item = item,
                        onCardClick = { viewModel.showAnalysis(item.session.id) },
                        onPausesClick = { viewModel.showPauseLog(item.session.id) },
                    )
                }
            }
        }
    }

    uiState.pauseLogSessionId
        ?.let { id -> sessions.firstOrNull { it.session.id == id } }
        ?.let { item -> PauseLogDialog(item = item, onDismiss = viewModel::dismissDialogs) }

    uiState.analysisSessionId
        ?.let { id -> sessions.firstOrNull { it.session.id == id } }
        ?.let { item -> AnalysisDialog(item = item, onDismiss = viewModel::dismissDialogs) }
}

@Composable
private fun PreviousSessionCard(
    item: SessionWithPauses,
    onCardClick: () -> Unit,
    onPausesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = item.session
    val goalSeconds = session.goalMinutes * 60
    val isOvertime = session.focusedSeconds > goalSeconds
    val actualTime = formatDuration(session.focusedSeconds)

    FocusCard(modifier = modifier, onClick = onCardClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                session.endedAt?.let { at ->
                    Text(
                        text = "${formatDate(at)} · ${formatTime(at)}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                }
            }
            Text(
                text = session.focusScore?.toString() ?: "—",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = session.focusScore?.let(::scoreColor) ?: TextSecondary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DetailRow(
                left = {
                    DetailItem(
                        label = stringResource(R.string.detail_goal_time),
                        value = stringResource(R.string.value_minutes, session.goalMinutes),
                    )
                },
                right = {
                    DetailItem(
                        label = stringResource(R.string.detail_actual_time),
                        value = if (isOvertime) {
                            stringResource(R.string.value_duration_over, actualTime)
                        } else {
                            actualTime
                        },
                        valueColor = actualTimeColor(session.focusedSeconds, goalSeconds),
                    )
                },
            )

            DetailItem(
                label = stringResource(R.string.detail_pauses),
                value = pluralStringResource(
                    R.plurals.value_pauses_tap,
                    item.pauses.size,
                    item.pauses.size,
                ),
                valueColor = Accent,
                modifier = Modifier.clickable(onClick = onPausesClick),
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = Border,
        )
        Text(
            text = stringResource(R.string.previous_card_footer),
            fontSize = 11.sp,
            color = TextTertiary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
