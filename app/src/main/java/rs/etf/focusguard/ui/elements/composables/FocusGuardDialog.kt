package rs.etf.focusguard.ui.elements.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import rs.etf.focusguard.ui.elements.theme.Surface as SurfaceColor
import rs.etf.focusguard.ui.elements.theme.TextPrimary

/**
 * Modal shell matching the prototype's `.overlay` / `.modal`. Content scrolls so long
 * pause logs and AI analyses stay reachable on small screens.
 */
@Composable
fun FocusGuardDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    title: String? = null,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = SurfaceColor,
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                when {
                    header != null -> header()
                    title != null -> Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .weight(weight = 1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    content = actions,
                )
            }
        }
    }
}
