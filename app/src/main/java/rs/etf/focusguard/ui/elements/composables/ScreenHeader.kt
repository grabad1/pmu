package rs.etf.focusguard.ui.elements.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rs.etf.focusguard.R
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.TextPrimary

/** Back arrow + title, matching the prototype's `.s-header`. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = stringResource(R.string.back),
                tint = Accent,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
    }
}
