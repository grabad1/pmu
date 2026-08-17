package rs.etf.focusguard.ui.elements.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.Border
import rs.etf.focusguard.ui.elements.theme.Card
import rs.etf.focusguard.ui.elements.theme.TextPrimary

/** `.btn-primary` — solid accent, white bold label. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(vertical = 15.dp),
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** `.btn-secondary` — card background with a subtle border. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Card,
            contentColor = TextPrimary,
        ),
        contentPadding = PaddingValues(vertical = 13.dp),
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
