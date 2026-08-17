package rs.etf.focusguard.ui.elements.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import rs.etf.focusguard.R

@Composable
fun ScheduledSessionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.scheduled_sessions_title),
        note = "Scheduled session list arrives in Phase 2.",
        onBack = onBack,
        modifier = modifier,
    )
}
