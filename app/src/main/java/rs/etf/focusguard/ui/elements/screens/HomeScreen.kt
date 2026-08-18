package rs.etf.focusguard.ui.elements.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The prototype lifts the header into place on arrival. It travels less on a short
        // screen, where 170 dp would start it off the bottom entirely.
        val travel = if (maxHeight < 560.dp) 60.dp else 170.dp

        // rememberSaveable, so the entrance plays once on arrival rather than again on every
        // rotation — an animation that repeats itself stops reading as an arrival.
        var arrived by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) { arrived = true }

        val offsetY by animateDpAsState(
            targetValue = if (arrived) 0.dp else travel,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessVeryLow,
            ),
            label = "homeHeaderOffset",
        )
        val headerAlpha by animateFloatAsState(
            targetValue = if (arrived) 1f else 0f,
            animationSpec = tween(durationMillis = 500),
            label = "homeHeaderAlpha",
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(y = offsetY)
                    .alpha(headerAlpha),
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
            }

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
}
