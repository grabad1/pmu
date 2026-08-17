package rs.etf.focusguard.ui.elements.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import rs.etf.focusguard.R
import rs.etf.focusguard.ui.elements.composables.EmptyState
import rs.etf.focusguard.ui.elements.composables.ScreenHeader

/** Phase 3 replaces this with the live timer, progress ring and pause controls. */
@Composable
fun ActiveSessionScreen(
    sessionName: String,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = sessionName, onBack = onFinished)
        EmptyState(icon = "\u23F1", text = stringResource(R.string.session_timer_pending))
    }
}
