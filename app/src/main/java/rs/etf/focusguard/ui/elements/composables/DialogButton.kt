package rs.etf.focusguard.ui.elements.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.Card
import rs.etf.focusguard.ui.elements.theme.Red
import rs.etf.focusguard.ui.elements.theme.TextSecondary

enum class DialogButtonStyle { PRIMARY, SECONDARY, DANGER }

/** `.mbtn` from the prototype — equal-width buttons along the bottom of a modal. */
@Composable
fun RowScope.DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: DialogButtonStyle = DialogButtonStyle.SECONDARY,
    enabled: Boolean = true,
    containerColorOverride: Color? = null,
) {
    val container = containerColorOverride ?: when (style) {
        DialogButtonStyle.PRIMARY -> Accent
        DialogButtonStyle.SECONDARY -> Card
        DialogButtonStyle.DANGER -> Red.copy(alpha = 0.12f)
    }
    val content = when (style) {
        DialogButtonStyle.PRIMARY -> Color.White
        DialogButtonStyle.SECONDARY -> TextSecondary
        DialogButtonStyle.DANGER -> Red
    }

    Button(
        onClick = onClick,
        modifier = modifier.weight(1f),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = if (style == DialogButtonStyle.DANGER) BorderStroke(1.dp, Red) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
        ),
        contentPadding = PaddingValues(vertical = 11.dp),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
