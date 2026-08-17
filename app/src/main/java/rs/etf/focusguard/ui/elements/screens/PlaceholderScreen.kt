package rs.etf.focusguard.ui.elements.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rs.etf.focusguard.ui.elements.composables.ScreenHeader
import rs.etf.focusguard.ui.elements.theme.TextSecondary

/**
 * Temporary Phase 0 scaffold body. Each screen gets its real content in Phase 2.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    note: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = title, onBack = onBack)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
