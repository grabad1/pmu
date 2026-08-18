package rs.etf.focusguard.ui.elements.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.etf.focusguard.R
import rs.etf.focusguard.data.SessionRuntimeState
import rs.etf.focusguard.data.room.PauseType
import rs.etf.focusguard.sensors.WarningKind
import rs.etf.focusguard.ui.elements.composables.BigWarningOverlay
import rs.etf.focusguard.ui.elements.composables.DialogButton
import rs.etf.focusguard.ui.elements.composables.DialogButtonStyle
import rs.etf.focusguard.ui.elements.composables.FocusGuardDialog
import rs.etf.focusguard.ui.elements.composables.WarningToastData
import rs.etf.focusguard.ui.elements.composables.WarningToastHost
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.Blue
import rs.etf.focusguard.ui.elements.theme.Border
import rs.etf.focusguard.ui.elements.theme.Card
import rs.etf.focusguard.ui.elements.theme.Card2
import rs.etf.focusguard.ui.elements.theme.TextPrimary
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import rs.etf.focusguard.ui.stateholders.ActiveSessionViewModel
import rs.etf.focusguard.util.formatHoursMinutesSeconds
import rs.etf.focusguard.util.formatMinutesSeconds

/**
 * Sizes for the running-session screen, chosen from the height actually available.
 *
 * The dial was a fixed 224 dp, which with `SpaceBetween` left a landscape phone (about 411 dp
 * tall) with no room for the buttons: they were squeezed to a few unlabelled pixels and could
 * not be tapped at all, so a session could only be controlled from the notification. The
 * prototype answers this by shrinking the circle and tightening the gaps, and so does this.
 *
 * Driven by height rather than orientation, because a short window is the actual problem —
 * split-screen and small phones hit it too.
 */
private data class SessionMetrics(
    val compact: Boolean,
    val dialSize: Dp,
    val timeSize: TextUnit,
    val statusSize: TextUnit,
    val dialGap: Dp,
    val pauseValueSize: TextUnit,
    val headerPadding: Dp,
    val buttonPadding: Dp,
)

private fun sessionMetricsFor(availableHeight: Dp): SessionMetrics =
    if (availableHeight < COMPACT_HEIGHT_THRESHOLD) {
        SessionMetrics(
            compact = true,
            dialSize = 148.dp,
            timeSize = 22.sp,
            statusSize = 10.sp,
            dialGap = 12.dp,
            pauseValueSize = 15.sp,
            headerPadding = 2.dp,
            buttonPadding = 10.dp,
        )
    } else {
        SessionMetrics(
            compact = false,
            dialSize = 224.dp,
            timeSize = 34.sp,
            statusSize = 11.sp,
            dialGap = 22.dp,
            pauseValueSize = 22.sp,
            headerPadding = 8.dp,
            buttonPadding = 13.dp,
        )
    }

/** Below this there is not enough height for the full-size dial and the controls together. */
private val COMPACT_HEIGHT_THRESHOLD = 560.dp

@Composable
fun ActiveSessionScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toasts by viewModel.toasts.collectAsStateWithLifecycle()
    val showBigWarning by viewModel.showBigWarning.collectAsStateWithLifecycle()
    var showEndDialog by remember { mutableStateOf(false) }
    var hasSeenSession by remember { mutableStateOf(false) }

    // The engine has no state until it has attached, so null only means "finished" once a
    // session has actually been seen. Reacting to the first null would bounce straight back
    // to Home on the way in.
    LaunchedEffect(state) {
        if (state != null) hasSeenSession = true else if (hasSeenSession) onFinished()
    }

    val current = state ?: return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val metrics = sessionMetricsFor(maxHeight)
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scrolling is a backstop, not the normal case: the compact sizes fit a
                // landscape phone, but a very short window must never clip the controls
                // again. Demanding at least the viewport's height keeps SpaceBetween
                // meaningful, so the layout still reads the same as it does in portrait.
                .then(
                    if (metrics.compact) {
                        Modifier.verticalScroll(scrollState).heightIn(min = maxHeight)
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SessionHeader(current, metrics)
            SessionDial(current, metrics)
            SessionControls(
                state = current,
                metrics = metrics,
                onTogglePause = viewModel::togglePause,
                onEndRequested = { showEndDialog = true },
            )
        }

        WarningToastHost(
            toasts = toasts.map { it.id to it.kind.toToastData() },
            onDismiss = viewModel::dismissToast,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
        )

        if (showBigWarning) {
            BigWarningOverlay(onDismiss = viewModel::dismissBigWarning)
        }
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
private fun SessionHeader(
    state: SessionRuntimeState,
    metrics: SessionMetrics,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = metrics.headerPadding),
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
private fun SessionDial(
    state: SessionRuntimeState,
    metrics: SessionMetrics,
    modifier: Modifier = Modifier,
) {
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
        verticalArrangement = Arrangement.spacedBy(metrics.dialGap),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(metrics.dialSize)) {
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
                    fontSize = metrics.timeSize,
                    fontWeight = FontWeight.Black,
                    color = if (state.isPastGoal) Blue else TextPrimary,
                )
                Text(
                    text = state.statusText(),
                    fontSize = metrics.statusSize,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        PauseReadout(state, metrics)
    }
}

@Composable
private fun PauseReadout(
    state: SessionRuntimeState,
    metrics: SessionMetrics,
    modifier: Modifier = Modifier,
) {
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
            fontSize = metrics.pauseValueSize,
            fontWeight = FontWeight.Bold,
            color = if (state.isPaused) Blue else TextPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SessionControls(
    state: SessionRuntimeState,
    metrics: SessionMetrics,
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
            verticalPadding = metrics.buttonPadding,
            modifier = Modifier.weight(1f),
        )
        SessionButton(
            text = stringResource(R.string.action_end_session),
            onClick = onEndRequested,
            contentColor = MaterialTheme.colorScheme.error,
            borderColor = MaterialTheme.colorScheme.error,
            verticalPadding = metrics.buttonPadding,
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
    verticalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, borderColor ?: Border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Card,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(vertical = verticalPadding),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SessionRuntimeState.statusText(): String = when {
    activePauseType == PauseType.PLANNED -> stringResource(R.string.session_on_planned_pause)
    activePauseType == PauseType.UNPLANNED -> stringResource(R.string.session_on_unplanned_pause)
    isPastGoal -> stringResource(R.string.session_past_goal)
    else -> stringResource(R.string.session_focusing)
}

private fun WarningKind.toToastData(): WarningToastData = when (this) {
    WarningKind.BAD_LIGHT -> WarningToastData("💡", R.string.warning_bad_light)
    WarningKind.LOUD_ROOM -> WarningToastData("🔊", R.string.warning_loud_room)
    // Movement is shown full screen; this exists only for exhaustiveness.
    WarningKind.MOVEMENT -> WarningToastData("🚫", R.string.warning_movement_toast)
}
