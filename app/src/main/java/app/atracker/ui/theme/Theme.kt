package app.atracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF7C6CFF)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF14102E),
    secondary = Color(0xFF4FD1C5),
    background = Color(0xFF0F1117),
    surface = Color(0xFF161923),
    surfaceVariant = Color(0xFF1F2330),
    onBackground = Color(0xFFE8EAF2),
    onSurface = Color(0xFFE8EAF2),
    onSurfaceVariant = Color(0xFFA9AEC2)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    secondary = Color(0xFF0F8F85),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EBF4)
)

@Composable
fun ATrackerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
