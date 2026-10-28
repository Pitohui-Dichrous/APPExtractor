package app.appextractor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Corner = RoundedCornerShape(2.dp)

private val AcidLime = Color(0xFFC0FE03)

internal enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色");

    fun usesDarkTheme(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun restore(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/** Brand planes exchange places; neutral content surfaces stay strictly grayscale. */
@Immutable
internal data class AcidColors(
    val main: Color,
    val onMain: Color,
    val action: Color,
    val onAction: Color,
    val dark: Boolean,
)

private val DarkAcid = AcidColors(Color.Black, AcidLime, AcidLime, Color.Black, true)
private val LightAcid = AcidColors(AcidLime, Color.Black, Color.Black, AcidLime, false)
internal val LocalAcidColors = staticCompositionLocalOf { DarkAcid }

private val DarkColors = darkColorScheme(
    primary = AcidLime, onPrimary = Color.Black,
    primaryContainer = Color(0xFF242424), onPrimaryContainer = AcidLime,
    secondary = Color(0xFFD0D0D0), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF262626), onSecondaryContainer = Color(0xFFF7F7F7),
    tertiary = AcidLime, onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF262626), onTertiaryContainer = AcidLime,
    background = Color.Black, onBackground = Color(0xFFF7F7F7),
    surface = Color(0xFF161616), onSurface = Color(0xFFF7F7F7),
    surfaceVariant = Color(0xFF242424), onSurfaceVariant = Color(0xFFBBBBBB),
    outline = Color(0xFF858585), outlineVariant = Color(0xFF3B3B3B),
    surfaceDim = Color.Black, surfaceBright = Color(0xFF2C2C2C),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF111111),
    surfaceContainer = Color(0xFF161616), surfaceContainerHigh = Color(0xFF202020),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    // Matching the surface avoids Material's elevation tint; transparent becomes black at elevation.
    surfaceTint = Color(0xFF161616),
    inverseSurface = Color(0xFFEAEAEA), inverseOnSurface = Color.Black,
    inversePrimary = Color.Black,
    error = Color(0xFFF7F7F7), onError = Color.Black,
    errorContainer = Color(0xFF262626), onErrorContainer = Color(0xFFF7F7F7),
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = Color.Black, onPrimary = AcidLime,
    primaryContainer = Color(0xFFE0E0E0), onPrimaryContainer = Color.Black,
    secondary = Color(0xFF414141), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6E6), onSecondaryContainer = Color.Black,
    tertiary = Color.Black, onTertiary = AcidLime,
    tertiaryContainer = Color(0xFFE6E6E6), onTertiaryContainer = Color.Black,
    background = Color(0xFFF2F2F2), onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color(0xFFE6E6E6), onSurfaceVariant = Color(0xFF525252),
    outline = Color(0xFF777777), outlineVariant = Color(0xFFC5C5C5),
    surfaceDim = Color(0xFFE1E1E1), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFDDDDDD), surfaceTint = Color.White,
    inverseSurface = Color(0xFF161616), inverseOnSurface = Color(0xFFF7F7F7),
    inversePrimary = AcidLime,
    error = Color.Black, onError = Color.White,
    errorContainer = Color(0xFFE6E6E6), onErrorContainer = Color.Black,
    scrim = Color.Black,
)

private val DisplayFont = FontFamily(Font(R.font.barlow_condensed_extra_bold, FontWeight.ExtraBold))
private val AcidTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 88.sp, lineHeight = 80.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 60.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

@Composable
internal fun ExtractorTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = mode.usesDarkTheme(isSystemInDarkTheme())
    CompositionLocalProvider(LocalAcidColors provides if (dark) DarkAcid else LightAcid) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AcidTypography,
            shapes = Shapes(extraSmall = Corner, small = Corner, medium = Corner, large = Corner, extraLarge = Corner),
            content = content,
        )
    }
}
