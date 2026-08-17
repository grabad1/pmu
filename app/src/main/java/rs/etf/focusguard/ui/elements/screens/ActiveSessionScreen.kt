package rs.etf.focusguard.ui.elements.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ActiveSessionScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "Session",
        note = "The live timer, progress ring and pause controls arrive in Phase 3.",
        onBack = onFinished,
        modifier = modifier,
    )
}
