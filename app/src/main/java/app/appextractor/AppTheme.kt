package app.appextractor

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val Corner = RoundedCornerShape(12.dp)

private val Gold = Color(0xFFC29E64)
private val Green = Color(0xFF4C7543)

@Composable
internal fun ExtractorTheme(content: @Composable () -> Unit) {
    // The brand's main color is the canvas; Material primary is the action color.
    // Keep these roles when the OS switches appearance rather than tinting a black canvas.
    val colors = lightColorScheme(
        primary = Green, onPrimary = Color.White,
        primaryContainer = Color(0xFFE5D0A9), onPrimaryContainer = Color(0xFF28251F),
        secondary = Color(0xFF433623), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE5D0A9), onSecondaryContainer = Color(0xFF28251F),
        tertiary = Green, onTertiary = Color.White,
        background = Gold, onBackground = Color(0xFF28251F),
        surface = Color(0xFFDBC398), onSurface = Color(0xFF28251F),
        surfaceVariant = Color(0xFFF2E4C9), onSurfaceVariant = Color(0xFF433623),
        outline = Color(0xFF625030), outlineVariant = Color(0xFF917344),
        surfaceDim = Gold, surfaceBright = Color(0xFFDBC398),
        surfaceContainerLowest = Gold, surfaceContainerLow = Color(0xFFDBC398),
        surfaceContainer = Color(0xFFDBC398), surfaceContainerHigh = Color(0xFFDBC398),
        surfaceContainerHighest = Color(0xFFF2E4C9), surfaceTint = Color(0xFFDBC398),
        inverseSurface = Green, inverseOnSurface = Color.White,
        inversePrimary = Color.White, error = Color(0xFF64211C),
        errorContainer = Color(0xFFEDD0BA), onErrorContainer = Color(0xFF64211C),
    )
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(small = Corner, medium = Corner, large = Corner),
        content = content,
    )
}
