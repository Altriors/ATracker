package app.atracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Approved prototype palette: calm slate surfaces with one saffron accent, in both themes.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5B83D), onPrimary = Color(0xFF1B1405),
    primaryContainer = Color(0xFF3A2B08), onPrimaryContainer = Color(0xFFFFE2A0),
    secondary = Color(0xFF5BA4FF), onSecondary = Color(0xFF06223F),
    tertiary = Color(0xFF47C688),
    background = Color(0xFF0C1117), onBackground = Color(0xFFE8EDF3),
    surface = Color(0xFF151B23), onSurface = Color(0xFFE8EDF3),
    surfaceVariant = Color(0xFF1E2631), onSurfaceVariant = Color(0xFF9BA7B6),
    surfaceContainer = Color(0xFF151B23), surfaceContainerHigh = Color(0xFF1E2631),
    outline = Color(0xFF2A3441), outlineVariant = Color(0xFF2A3441),
    error = Color(0xFFF26B67)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFF0B323), onPrimary = Color(0xFF1B1405),
    primaryContainer = Color(0xFFFFEFC2), onPrimaryContainer = Color(0xFF855700),
    secondary = Color(0xFF1D6FD6), onSecondary = Color.White,
    tertiary = Color(0xFF17804D),
    background = Color(0xFFF3F5F8), onBackground = Color(0xFF10151C),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF10151C),
    surfaceVariant = Color(0xFFE8ECF2), onSurfaceVariant = Color(0xFF566170),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFE8ECF2),
    outline = Color(0xFFD7DDE6), outlineVariant = Color(0xFFD7DDE6),
    error = Color(0xFFC23A36)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp)
)

@Composable
fun ATrackerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, shapes = AppShapes, content = content)
}
