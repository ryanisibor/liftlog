package app.liftlog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * LiftLog's look: charcoal surfaces with a single amber accent. The accent marks the thing
 * to act on next (ticked sets, the selected exercise, worked muscles), so use it sparingly.
 * To re-colour the app, change Amber and the two container shades derived from it.
 */
private val Amber = Color(0xFFFFB454)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2B1700),
    primaryContainer = Color(0xFF4A3212),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFC9C5BD),
    onSecondary = Color(0xFF1F1E1B),
    secondaryContainer = Color(0xFF2B2F36),
    onSecondaryContainer = Color(0xFFE6E3DE),
    tertiary = Color(0xFF8FD0C0),
    onTertiary = Color(0xFF00382E),
    background = Color(0xFF0F1114),
    onBackground = Color(0xFFE6E3DE),
    surface = Color(0xFF0F1114),
    onSurface = Color(0xFFE6E3DE),
    surfaceVariant = Color(0xFF272C34),
    onSurfaceVariant = Color(0xFFA9A6A0),
    surfaceContainerLowest = Color(0xFF0B0D0F),
    surfaceContainerLow = Color(0xFF14171B),
    surfaceContainer = Color(0xFF181B20),
    surfaceContainerHigh = Color(0xFF1F232A),
    surfaceContainerHighest = Color(0xFF272C34),
    outline = Color(0xFF5A5D63),
    outlineVariant = Color(0xFF33373D),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0A06),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5100),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB0),
    onPrimaryContainer = Color(0xFF2B1700),
    secondary = Color(0xFF5F5B53),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E5DD),
    onSecondaryContainer = Color(0xFF1F1E1B),
    tertiary = Color(0xFF006B5A),
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFAF8F5),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE6E2DA),
    onSurfaceVariant = Color(0xFF55524B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F1EC),
    surfaceContainer = Color(0xFFEFECE6),
    surfaceContainerHigh = Color(0xFFE9E5DF),
    surfaceContainerHighest = Color(0xFFE2DED7),
    outline = Color(0xFF86827A),
    outlineVariant = Color(0xFFD0CCC4),
)

private val LiftLogShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun LiftLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = LiftLogShapes,
        content = content,
    )
}
