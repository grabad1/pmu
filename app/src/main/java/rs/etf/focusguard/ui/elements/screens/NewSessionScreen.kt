package rs.etf.focusguard.ui.elements.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rs.etf.focusguard.R
import rs.etf.focusguard.ui.elements.composables.PrimaryButton
import rs.etf.focusguard.ui.elements.composables.ScreenHeader
import rs.etf.focusguard.ui.elements.composables.SecondaryButton
import rs.etf.focusguard.ui.elements.theme.TextSecondary

@Composable
fun NewSessionScreen(
    onBack: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.new_session_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Session form arrives in Phase 2 —\nname, goal time, pauses, pause duration.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(
                text = "Schedule",
                onClick = { },
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = "Start",
                onClick = onStart,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
