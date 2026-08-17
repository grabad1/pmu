package rs.etf.focusguard.ui.elements.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rs.etf.focusguard.R
import rs.etf.focusguard.ui.elements.composables.PrimaryButton
import rs.etf.focusguard.ui.elements.composables.SecondaryButton
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.AccentDim
import rs.etf.focusguard.ui.elements.theme.TextSecondary

@Composable
fun HomeScreen(
    onNewSession: () -> Unit,
    onScheduledSessions: () -> Unit,
    onPreviousSessions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .background(AccentDim, RoundedCornerShape(20.dp))
                .border(2.dp, Accent, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.TrackChanges,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(32.dp),
            )
        }

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = Accent,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(
                text = stringResource(R.string.home_new_session),
                onClick = onNewSession,
            )
            SecondaryButton(
                text = stringResource(R.string.home_scheduled_sessions),
                onClick = onScheduledSessions,
            )
            SecondaryButton(
                text = stringResource(R.string.home_previous_sessions),
                onClick = onPreviousSessions,
            )
        }
    }
}
