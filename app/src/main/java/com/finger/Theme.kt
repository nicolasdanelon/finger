package com.finger

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

// Estilo terminal fosforescente. Solo estilos: sin permisos, libs ni lógica nueva.

private val Phosphor = Color(0xFF39FF6A)
private val PhosphorDim = Color(0xFF1D9E44)
private val TerminalBg = Color(0xFF020603)
private val TerminalSurface = Color(0xFF070B07)
private val TerminalText = Color(0xFFC9F5D3)
private val Amber = Color(0xFFFFB000)

private val PaperBg = Color(0xFFEDEFE8)
private val PaperSurface = Color(0xFFE2E7DE)
private val InkGreen = Color(0xFF0B3D1C)
private val LeafGreen = Color(0xFF0E7A33)

private val HackerDarkColors = darkColorScheme(
    primary = Phosphor,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0B2E16),
    onPrimaryContainer = Phosphor,
    secondary = PhosphorDim,
    onSecondary = Color.Black,
    tertiary = Amber,
    onTertiary = Color.Black,
    background = TerminalBg,
    onBackground = TerminalText,
    surface = TerminalSurface,
    onSurface = TerminalText,
    surfaceVariant = Color(0xFF0D130D),
    onSurfaceVariant = PhosphorDim,
    outline = PhosphorDim,
    outlineVariant = Color(0xFF12341C)
)

private val HackerLightColors = lightColorScheme(
    primary = LeafGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9E9D2),
    onPrimaryContainer = InkGreen,
    secondary = Color(0xFF3E6B4C),
    onSecondary = Color.White,
    tertiary = Color(0xFF8A5A00),
    background = PaperBg,
    onBackground = InkGreen,
    surface = PaperSurface,
    onSurface = InkGreen,
    surfaceVariant = Color(0xFFD8DED3),
    onSurfaceVariant = Color(0xFF2E4A37),
    outline = LeafGreen,
    outlineVariant = Color(0xFF9DB3A4)
)

private fun TextStyle.mono() = copy(fontFamily = FontFamily.Monospace)

private val HackerTypography = Typography().run {
    copy(
        displayLarge = displayLarge.mono(),
        displayMedium = displayMedium.mono(),
        displaySmall = displaySmall.mono(),
        headlineLarge = headlineLarge.mono(),
        headlineMedium = headlineMedium.mono(),
        headlineSmall = headlineSmall.mono(),
        titleLarge = titleLarge.mono(),
        titleMedium = titleMedium.mono(),
        titleSmall = titleSmall.mono(),
        bodyLarge = bodyLarge.mono(),
        bodyMedium = bodyMedium.mono(),
        bodySmall = bodySmall.mono(),
        labelLarge = labelLarge.mono(),
        labelMedium = labelMedium.mono(),
        labelSmall = labelSmall.mono()
    )
}

private val HackerShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp)
)

@Composable
fun FingerTheme(content: @Composable () -> Unit) {
    // Solo lee el flag local del sistema (sin permisos, sin red).
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) HackerDarkColors else HackerLightColors,
        typography = HackerTypography,
        shapes = HackerShapes,
        content = content
    )
}
