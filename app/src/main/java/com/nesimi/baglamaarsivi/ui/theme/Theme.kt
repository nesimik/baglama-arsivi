package com.nesimi.baglamaarsivi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFC86D24)
val AmberLight = Color(0xFFE59A52)
val WoodDark = Color(0xFF2A1810)
val WoodMedium = Color(0xFF5D3A1A)
val Sand = Color(0xFFFAF6F1)

val StatusBlue = Color(0xFF1E88E5)
val StatusYellow = Color(0xFFF59E0B)
val StatusGreen = Color(0xFF2E9D4F)
val FavGold = Color(0xFFFFA000)

private val Light = lightColorScheme(
    primary = Amber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8D2),
    onPrimaryContainer = WoodDark,
    secondary = WoodMedium,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E4D8),
    onSecondaryContainer = WoodDark,
    tertiary = Color(0xFF2F7D6D),
    background = Sand,
    onBackground = Color(0xFF231C18),
    surface = Color.White,
    onSurface = Color(0xFF231C18),
    surfaceVariant = Color(0xFFF3ECE4),
    onSurfaceVariant = Color(0xFF6B5E55),
    surfaceContainer = Color(0xFFF7F1EA),
    surfaceContainerHigh = Color(0xFFF2EBE3),
    outline = Color(0xFFD9CCBF),
    outlineVariant = Color(0xFFEAE0D6)
)

// Karanlık mod da "kömür" değil, sıcak koyu gri tonlarda – kartlar okunaklı ve daha açık.
private val Dark = darkColorScheme(
    primary = AmberLight,
    onPrimary = Color(0xFF2A1404),
    primaryContainer = Color(0xFF5A3A20),
    onPrimaryContainer = Color(0xFFFFE8D2),
    secondary = Color(0xFFD9B08C),
    onSecondary = Color(0xFF2A1810),
    secondaryContainer = Color(0xFF4A3F38),
    onSecondaryContainer = Color(0xFFF4ECE4),
    tertiary = Color(0xFF7CC9B8),
    background = Color(0xFF26211E),
    onBackground = Color(0xFFF4ECE4),
    surface = Color(0xFF302A26),
    onSurface = Color(0xFFF4ECE4),
    surfaceVariant = Color(0xFF3D3530),
    onSurfaceVariant = Color(0xFFCFC2B7),
    surfaceContainer = Color(0xFF342D29),
    surfaceContainerHigh = Color(0xFF3B3430),
    outline = Color(0xFF5E534B),
    outlineVariant = Color(0xFF4A413B)
)

val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun BaglamaTheme(dark: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(), content = content)
    }
}
