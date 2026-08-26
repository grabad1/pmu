package rs.etf.focusguard.ui.elements.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import rs.etf.focusguard.R
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.interruptions.FocusGuardNotificationListener
import rs.etf.focusguard.ui.elements.composables.PrimaryButton
import rs.etf.focusguard.ui.elements.composables.SecondaryButton
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.AccentDim
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import rs.etf.focusguard.util.formatTime

@Composable
fun HomeScreen(
    onNewSession: () -> Unit,
    onScheduledSessions: () -> Unit,
    onPreviousSessions: () -> Unit,
    modifier: Modifier = Modifier,
    dueSession: Session? = null,
    onJoinDueSession: () -> Unit = {},
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
                // Above the other actions, because a session that is due now is the most
                // likely reason the app was opened at all.
                dueSession?.let { session ->
                    DueSessionButton(session = session, onClick = onJoinDueSession)
                }

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

            NotificationAccessPrompt(modifier = Modifier.padding(top = 22.dp))
        }
    }
}

/**
 * Offers a scheduled session that is due right now.
 *
 * The prompt that appears on opening the app can be dismissed, and once it was, the session
 * could not be started from the app at all until the next launch — so a scheduled session
 * could be missed while sitting in front of the person who scheduled it. This stays for the
 * whole window the session is joinable.
 */
@Composable
private fun DueSessionButton(
    session: Session,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "dueSession").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dueGlow",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AccentDim, RoundedCornerShape(14.dp))
            .border(2.dp, Accent.copy(alpha = pulse), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "⏰", fontSize = 22.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_due_title, session.name),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
            )
            Text(
                text = session.scheduledAt?.let {
                    stringResource(R.string.home_due_subtitle, formatTime(it))
                } ?: stringResource(R.string.home_due_now),
                fontSize = 11.sp,
                color = TextSecondary,
            )
        }
        Text(
            text = stringResource(R.string.home_due_action),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Accent,
        )
    }
}

/**
 * Asks for notification access, which is what lets a session report which app interrupted it.
 *
 * Shown only while the access is missing, and it disappears as soon as it is granted — so a
 * user who wants the feature is told how to get it, and one who does not is not nagged on
 * every visit to the home screen. Everything else works without it.
 *
 * The state is re-read whenever the app comes back to the front, because the user grants it
 * in Settings and returns; without that the card would still be sitting there.
 */
@Composable
private fun NotificationAccessPrompt(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(FocusGuardNotificationListener.isEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = FocusGuardNotificationListener.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (granted) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AccentDim, RoundedCornerShape(14.dp))
            .border(1.dp, Accent, RoundedCornerShape(14.dp))
            .clickable { context.startActivity(FocusGuardNotificationListener.settingsIntent()) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.notification_access_title),
            style = MaterialTheme.typography.bodyMedium,
            color = Accent,
        )
        Text(
            text = stringResource(R.string.notification_access_body),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}
