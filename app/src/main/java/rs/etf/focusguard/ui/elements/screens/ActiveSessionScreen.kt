package rs.etf.focusguard.ui.elements.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.etf.focusguard.R
import rs.etf.focusguard.data.SessionRuntimeState
import rs.etf.focusguard.data.room.PauseType
import rs.etf.focusguard.ui.elements.composables.DialogButton
import rs.etf.focusguard.ui.elements.composables.DialogButtonStyle
import rs.etf.focusguard.ui.elements.composables.FocusGuardDialog
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.Blue
import rs.etf.focusguard.ui.elements.theme.Card2
import rs.etf.focusguard.ui.elements.theme.TextPrimary
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import rs.etf.focusguard.ui.stateholders.ActiveSessionViewModel
import rs.etf.focusguard.util.formatHoursMinutesSeconds
import rs.etf.focusguard.util.formatMinutesSeconds

@Composable
fun ActiveSessionScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEndDialog by remember { mutableStateOf(false) }
    var hasSeenSession by remember { mutableStateOf(false) }

    // The engine has no state until it has attached, so null only means "finished" once a
    // session has actually been seen. Reacting to the first null would bounce straight back
    // to Home on the way in.
    LaunchedEffect(state) {
        if (state != null) hasSeenSession = true else if (hasSeenSession) onFinished()
    }

    val current = state ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        SessionHeader(current)
        SessionDial(current)
        SessionControls(
            state = current,
            onTogglePause = viewModel::togglePause,
            onEndRequested = { showEndDialog = true },
        )
    }

    if (showEndDialog) {
        FocusGuardDialog(
            onDismissRequest = { showEndDialog = false },
            title = stringResource(R.string.end_dialog_title),
            actions = {
                DialogButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showEndDialog = false },
                    style = DialogButtonStyle.SECONDARY,
                )
                DialogButton(
                    text = stringResource(R.string.action_end_session),
                    onClick = {
                        showEndDialog = false
                        viewModel.endSession()
                    },
                    style = DialogButtonStyle.DANGER,
                )
            },
        ) {
            Text(
                text = stringResource(R.string.end_dialog_body),
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun SessionHeader(state: SessionRuntimeState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = stringResource(R.string.session_label),
                fontSize = 11.sp,
                color = TextSecondary,
            )
            Text(
                text = state.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.session_goal),
                fontSize = 11.sp,
                color = TextSecondary,
            )
            Text(
                text = stringResource(R.string.value_minutes, state.goalSeconds / 60),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }
    }
}

/** Progress ring, stopwatch and pause readout — the prototype's `.circle-wrap`. */
@Composable
private fun SessionDial(state: SessionRuntimeState, modifier: Modifier = Modifier) {
    val ringColor by animateColorAsState(
        targetValue = if (state.isPastGoal) Blue else Accent,
        animationSpec = tween(500),
        label = "ringColor",
    )
    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(900),
        label = "progress",
    )

    // Past the goal the ring glows, echoing the prototype's pulsing drop shadow.
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1400), repeatMode = RepeatMode.Reverse),
        label = "glowAlpha",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(224.dp)) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)

                drawArc(
                    color = Card2,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )

                if (state.isPastGoal) {
                    drawArc(
                        color = ringColor.copy(alpha = 0.35f * glow),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke * 2.2f, cap = StrokeCap.Round),
                    )
                }

                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatHoursMinutesSeconds(state.focusedSeconds),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = if (state.isPastGoal) Blue else TextPrimary,
                )
                Text(
                    text = state.statusText(),
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        PauseReadout(state)
    }
}

@Composable
private fun PauseReadout(state: SessionRuntimeState, modifier: Modifier = Modifier) {
    val label: String
    val value: String

    when {
        state.activePauseType == PauseType.PLANNED -> {
            label = stringResource(R.string.session_pause_resume_in)
            value = formatMinutesSeconds(state.pauseRemainingSeconds ?: 0)
        }

        state.activePauseType == PauseType.UNPLANNED -> {
            label = stringResource(R.string.session_unplanned_pause)
            value = formatMinutesSeconds(state.pauseElapsedSeconds)
        }

        state.nextPauseInSeconds != null -> {
            label = stringResource(R.string.session_next_pause)
            value = stringResource(
                R.string.session_next_pause_in,
                formatMinutesSeconds(state.nextPauseInSeconds),
            )
        }

        else -> {
            label = stringResource(R.string.session_pauses)
            value = if (state.hasPlannedPauses) {
                stringResource(R.string.session_pauses_done)
            } else {
                stringResource(R.string.session_pauses_none)
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (state.isPaused) Blue else TextPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SessionControls(
    state: SessionRuntimeState,
    onTogglePause: () -> Unit,
    onEndRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val pauseLabel = when (state.activePauseType) {
            PauseType.PLANNED -> stringResource(R.string.action_skip_pause)
            PauseType.UNPLANNED -> stringResource(R.string.action_resume)
            null -> stringResource(R.string.action_pause)
        }

        SessionButton(
            text = pauseLabel,
            onClick = onTogglePause,
            contentColor = if (state.isPaused) Blue else TextPrimary,
            borderColor = if (state.isPaused) Blue else null,
            modifier = Modifier.weight(1f),
        )
        SessionButton(
            text = stringResource(R.string.action_end_session),
            onClick = onEndRequested,
            contentColor = MaterialTheme.colorScheme.error,
            borderColor = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SessionButton(
    text: String,
    onClick: () -> Unit,
    contentColor: Color,
    borderColor: Color?,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            borderColor ?: rs.etf.focusguard.ui.elements.theme.Border,
        ),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = rs.etf.focusguard.ui.elements.theme.Card,
            contentColor = contentColor,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 13.dp),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SessionRuntimeState.statusText(): String = when {
    activePauseType == PauseType.PLANNED -> stringResource(R.string.session_on_planned_pause)
    activePauseType == PauseType.UNPLANNED -> stringResource(R.string.session_on_unplanned_pause)
    isPastGoal -> stringResource(R.string.session_past_goal)
    else -> stringResource(R.string.session_focusing)
}
