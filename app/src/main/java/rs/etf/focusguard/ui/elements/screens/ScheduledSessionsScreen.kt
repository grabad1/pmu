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
import rs.etf.focusguard.ui.elements.composables.EmptyState
import rs.etf.focusguard.ui.elements.composables.FocusCard
import rs.etf.focusguard.ui.elements.composables.ScreenHeader
import rs.etf.focusguard.ui.elements.theme.TextPrimary
import rs.etf.focusguard.ui.stateholders.ScheduledSessionsViewModel
import rs.etf.focusguard.util.formatDate
import rs.etf.focusguard.util.formatTime

@Composable
fun ScheduledSessionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduledSessionsViewModel = hiltViewModel(),
) {
    val sessions by viewModel.scheduledSessions.collectAsStateWithLifecycle(initialValue = emptyList())

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
                items(items = sessions, key = { it.id }) { session ->
                    ScheduledSessionCard(session)
                }
            }
        }
    }
}

@Composable
private fun ScheduledSessionCard(session: Session, modifier: Modifier = Modifier) {
    FocusCard(modifier = modifier) {
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
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            session.scheduledAt?.let { at ->
                Badge(text = "${formatDate(at)}  ${formatTime(at)}")
            }
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
}
