package rs.etf.focusguard.ui.elements.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FocusGuardColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentDim,
    onPrimaryContainer = Accent,
    secondary = Blue,
    onSecondary = Color.White,
    tertiary = Green,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Card,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Card,
    surfaceContainerHigh = Card2,
    outline = Border,
    outlineVariant = Border,
    error = Red,
    onError = Color.White,
)

/**
 * The app is dark-only by design — the prototype has no light variant,
 * so we deliberately ignore the system theme and dynamic colour.
 */
@Composable
fun FocusGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FocusGuardColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
