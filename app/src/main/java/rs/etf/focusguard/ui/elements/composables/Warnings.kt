package rs.etf.focusguard.ui.elements.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rs.etf.focusguard.R
import rs.etf.focusguard.ui.elements.theme.Border
import rs.etf.focusguard.ui.elements.theme.Card
import rs.etf.focusguard.ui.elements.theme.Red
import rs.etf.focusguard.ui.elements.theme.TextPrimary
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import rs.etf.focusguard.ui.elements.theme.Yellow

/** One advisory warning, matching the prototype's `.toast`. */
@Composable
fun WarningToast(
    icon: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, top = 9.dp, end = 4.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // The prototype marks toasts with a yellow leading edge; a coloured bar reads the
        // same way without a custom shape.
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 26.dp)
                .background(Yellow, RoundedCornerShape(2.dp))
        )
        Text(text = icon, fontSize = 17.sp)
        Text(
            text = message,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_dismiss),
                tint = TextSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** Stack of toasts pinned below the status bar, newest last. */
@Composable
fun WarningToastHost(
    toasts: List<Pair<Long, WarningToastData>>,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        toasts.forEach { (id, data) ->
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically { -it / 2 } + fadeIn(),
                exit = slideOutVertically { -it / 2 } + fadeOut(),
            ) {
                WarningToast(
                    icon = data.icon,
                    message = stringResource(data.messageResId),
                    onDismiss = { onDismiss(id) },
                )
            }
        }
    }
}

data class WarningToastData(val icon: String, val messageResId: Int)

/**
 * Full-screen interruption for phone movement — the prototype's `.big-warning`. Deliberately
 * unmissable and dismissed only by tapping, since the point is to break the reach for the
 * phone rather than to be scrolled past.
 */
@Composable
fun BigWarningOverlay(
    messageResId: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "bigWarning").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(animation = tween(750), repeatMode = RepeatMode.Reverse),
        label = "pulse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.97f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(text = "🚫", fontSize = 72.sp, modifier = Modifier.scale(pulse))
            Text(
                text = stringResource(messageResId),
                fontSize = 28.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.Black,
                color = Red,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp),
            )
            Text(
                text = stringResource(R.string.warning_tap_dismiss),
                fontSize = 13.sp,
                color = TextSecondary,
            )
        }
    }
}
