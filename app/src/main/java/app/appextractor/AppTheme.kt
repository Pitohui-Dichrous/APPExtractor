package app.appextractor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val Corner = RoundedCornerShape(12.dp)

private val Gold = Color(0xFFC29E64)
private val Green = Color(0xFF4C7543)

@Composable
internal fun ExtractorTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Gold, onPrimary = Color(0xFF241D12),
        primaryContainer = Color(0xFF554026), onPrimaryContainer = Color(0xFFF6E6C9),
        secondary = Color(0xFFB2D0A7), onSecondary = Color(0xFF183211),
        secondaryContainer = Color(0xFF293D23), onSecondaryContainer = Color(0xFFDCEBD4),
        tertiary = Gold, onTertiary = Color(0xFF241D12),
        background = Color(0xFF171713), onBackground = Color(0xFFEDE8DF),
        surface = Color(0xFF22221C), onSurface = Color(0xFFEDE8DF),
        surfaceVariant = Color(0xFF39372F), onSurfaceVariant = Color(0xFFC4BDAF),
        outline = Color(0xFFA69A85), outlineVariant = Color(0xFF514B40),
        surfaceDim = Color(0xFF171713), surfaceBright = Color(0xFF3C3A32),
        surfaceContainerLowest = Color(0xFF11110E), surfaceContainerLow = Color(0xFF20201A),
        surfaceContainer = Color(0xFF25251F), surfaceContainerHigh = Color(0xFF302F28),
        surfaceContainerHighest = Color(0xFF3A3931),
        inverseSurface = Color(0xFFEDE8DF), inverseOnSurface = Color(0xFF28251F),
        inversePrimary = Color(0xFF70552B), error = Color(0xFFFFB3AE),
    ) else lightColorScheme(
        primary = Gold, onPrimary = Color(0xFF241D12),
        primaryContainer = Color(0xFFF0E1C8), onPrimaryContainer = Color(0xFF3B2B15),
        secondary = Green, onSecondary = Color.White,
        secondaryContainer = Color(0xFFE5EDDF), onSecondaryContainer = Color(0xFF24401E),
        tertiary = Green, onTertiary = Color.White,
        background = Color(0xFFF7F4ED), onBackground = Color(0xFF28251F),
        surface = Color(0xFFFFFCF6), onSurface = Color(0xFF28251F),
        surfaceVariant = Color(0xFFEBE5D9), onSurfaceVariant = Color(0xFF655E52),
        outline = Color(0xFF857964), outlineVariant = Color(0xFFD7CEBD),
        surfaceDim = Color(0xFFE5DED1), surfaceBright = Color(0xFFFFFCF6),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBF7EF),
        surfaceContainer = Color(0xFFF2EDE3), surfaceContainerHigh = Color(0xFFECE6DA),
        surfaceContainerHighest = Color(0xFFE5DED1),
        inverseSurface = Color(0xFF302E27), inverseOnSurface = Color(0xFFF7F4ED),
        inversePrimary = Gold, error = Color(0xFFA3302C),
    )
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(small = Corner, medium = Corner, large = Corner),
        content = content,
    )
}
