package com.hanzg.mipass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = SlateNavyPrimaryDark,
    onPrimary = SlateNavyOnPrimaryDark,
    primaryContainer = SlateNavyPrimaryContainerDark,
    onPrimaryContainer = SlateNavyOnPrimaryContainerDark,
    secondary = SlateNavyPrimaryDark,
    onSecondary = SlateNavyOnPrimaryDark,
    secondaryContainer = SlateNavyPrimaryContainerDark.copy(alpha = 0.4f),
    onSecondaryContainer = SlateNavyOnPrimaryContainerDark,
    tertiary = WarningAmber,
    onTertiary = Color(0xFF0C0E12),
    tertiaryContainer = WarningAmberContainerDark,
    onTertiaryContainer = Color(0xFFFFF3CD),
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceDim = DarkBackground,
    surfaceBright = DarkSurfaceContainerHighest,
    error = CoralRed,
    onError = Color(0xFF0C0E12),
    errorContainer = CoralRedContainerDark,
    onErrorContainer = Color(0xFFFCE4E1),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = ScrimDark
)

private val LightColorScheme = lightColorScheme(
    primary = SlateNavyPrimary,
    onPrimary = SlateNavyOnPrimary,
    primaryContainer = SlateNavyPrimaryContainer,
    onPrimaryContainer = SlateNavyOnPrimaryContainer,
    secondary = SlateNavyPrimary,
    onSecondary = SlateNavyOnPrimary,
    secondaryContainer = SlateNavyPrimaryContainer,
    onSecondaryContainer = SlateNavyOnPrimaryContainer,
    tertiary = WarningAmber,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = WarningAmberContainer,
    onTertiaryContainer = Color(0xFF3D2C00),
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceDim = LightBackground,
    surfaceBright = LightBackground,
    error = CoralRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = CoralRedContainer,
    onErrorContainer = Color(0xFF4A1A15),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = ScrimLight
)

val MiPassShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MiPassTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = MiPassShapes,
        content = content
    )
}
