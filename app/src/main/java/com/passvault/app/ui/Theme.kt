package com.passvault.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fixed brand colours, used for gradients and accents that should look the same in both themes. */
object Brand {
    val Violet = Color(0xFF6D4AFF)
    val VioletBright = Color(0xFF9B7BFF)
    val Indigo = Color(0xFF4338CA)
    val GradientStart = Color(0xFF6D4AFF)
    val GradientEnd = Color(0xFFA855F7)
    val primaryGradient get() = Brush.linearGradient(listOf(GradientStart, GradientEnd))

    val Weak = Color(0xFFF43F5E)
    val Fair = Color(0xFFF59E0B)
    val Strong = Color(0xFF22C55E)
    val VeryStrong = Color(0xFF14B8A6)
}

/** Gradient pairs for entry monograms; all dark enough to carry white text. */
val AvatarGradients = listOf(
    Color(0xFF4F46E5) to Color(0xFF7C5CFF),
    Color(0xFF0891B2) to Color(0xFF06B6D4),
    Color(0xFFE11D48) to Color(0xFFF43F5E),
    Color(0xFFD97706) to Color(0xFFF59E0B),
    Color(0xFF059669) to Color(0xFF10B981),
    Color(0xFF2563EB) to Color(0xFF3B82F6),
    Color(0xFFDB2777) to Color(0xFFEC4899),
    Color(0xFF7C3AED) to Color(0xFFA855F7),
)

/** Accent colours for folder tiles and setting icons. */
val AccentColors = listOf(
    Color(0xFF7C5CFF), Color(0xFF06B6D4), Color(0xFFF59E0B), Color(0xFF10B981),
    Color(0xFFEC4899), Color(0xFF3B82F6), Color(0xFFF43F5E), Color(0xFFA855F7),
)

fun accentFor(seed: String): Color = AccentColors[(seed.lowercase().hashCode() and 0x7fffffff) % AccentColors.size]

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAE98FF),
    onPrimary = Color(0xFF1C0F55),
    primaryContainer = Color(0xFF2E2470),
    onPrimaryContainer = Color(0xFFE7DFFF),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF00344A),
    secondaryContainer = Color(0xFF1B2A3A),
    onSecondaryContainer = Color(0xFFCDEBFF),
    tertiary = Color(0xFFF0ABFC),
    onTertiary = Color(0xFF4A0D54),
    background = Color(0xFF0B0C11),
    onBackground = Color(0xFFECECF4),
    surface = Color(0xFF0B0C11),
    onSurface = Color(0xFFECECF4),
    surfaceVariant = Color(0xFF1D1F2A),
    onSurfaceVariant = Color(0xFF9C9EB2),
    surfaceTint = Color(0xFFAE98FF),
    surfaceContainerLowest = Color(0xFF07080B),
    surfaceContainerLow = Color(0xFF111219),
    surfaceContainer = Color(0xFF15161F),
    surfaceContainerHigh = Color(0xFF1D1F2A),
    surfaceContainerHighest = Color(0xFF262836),
    inverseSurface = Color(0xFFECECF4),
    inverseOnSurface = Color(0xFF15161F),
    inversePrimary = Color(0xFF5B3FF5),
    outline = Color(0xFF3B3E52),
    outlineVariant = Color(0xFF242635),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF4A0010),
    errorContainer = Color(0xFF4D1222),
    onErrorContainer = Color(0xFFFFD9DE),
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3FF5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE4FF),
    onPrimaryContainer = Color(0xFF1E0F66),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9F3FB),
    onSecondaryContainer = Color(0xFF053341),
    tertiary = Color(0xFFA21CAF),
    onTertiary = Color.White,
    background = Color(0xFFF5F5FA),
    onBackground = Color(0xFF14151C),
    surface = Color(0xFFF5F5FA),
    onSurface = Color(0xFF14151C),
    surfaceVariant = Color(0xFFEDEDF5),
    onSurfaceVariant = Color(0xFF5E6075),
    surfaceTint = Color(0xFF5B3FF5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFD),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F0F7),
    surfaceContainerHighest = Color(0xFFE7E7F0),
    inverseSurface = Color(0xFF23242E),
    inverseOnSurface = Color(0xFFF2F2F8),
    inversePrimary = Color(0xFFAE98FF),
    outline = Color(0xFFC6C7D6),
    outlineVariant = Color(0xFFE6E6EF),
    error = Color(0xFFD92D4B),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E6),
    onErrorContainer = Color(0xFF5C0A1C),
    scrim = Color.Black,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
)

@Composable
fun PassVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
