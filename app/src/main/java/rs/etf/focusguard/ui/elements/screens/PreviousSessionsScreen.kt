package rs.etf.focusguard.ui.elements.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import rs.etf.focusguard.R

@Composable
fun PreviousSessionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.previous_sessions_title),
        note = "Past sessions, pause log and AI analysis arrive in Phase 2.",
        onBack = onBack,
        modifier = modifier,
    )
}
