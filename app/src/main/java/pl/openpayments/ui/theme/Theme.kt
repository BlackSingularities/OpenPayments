package pl.openpayments.ui.theme

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta: „papier paragonu” + głęboka zieleń + bursztyn jako akcent.
val Ink = Color(0xFF15211D)
val Paper = Color(0xFFF6F7F4)
val Forest = Color(0xFF1F6F5C)
val ForestLight = Color(0xFFD6EBE3)
val Amber = Color(0xFFE8B04A)
val Danger = Color(0xFFC8473D)
val Positive = Color(0xFF2E8B57)

private val Light = lightColorScheme(
    primary = Forest, onPrimary = Color.White,
    primaryContainer = ForestLight, onPrimaryContainer = Color(0xFF07291F),
    secondary = Color(0xFF4F635B), secondaryContainer = Color(0xFFE3EAE6),
    tertiary = Color(0xFF8A5A00), tertiaryContainer = Color(0xFFFBE3B5),
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF1EC),
    surfaceContainerHigh = Color(0xFFE8ECE6),
    surfaceVariant = Color(0xFFE3E8E3), onSurfaceVariant = Color(0xFF59655F),
    outline = Color(0xFFBFC8C2), outlineVariant = Color(0xFFDDE3DE),
    error = Danger,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD3B6), onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF16503F), onPrimaryContainer = Color(0xFFBDEFDB),
    secondary = Color(0xFFB4CBC1), secondaryContainer = Color(0xFF2C3833),
    tertiary = Amber, tertiaryContainer = Color(0xFF5A3F00),
    background = Color(0xFF0F1513), onBackground = Color(0xFFE1E5E2),
    surface = Color(0xFF0F1513), onSurface = Color(0xFFE1E5E2),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171E1B),
    surfaceContainer = Color(0xFF1B2320),
    surfaceContainerHigh = Color(0xFF242D29),
    surfaceVariant = Color(0xFF2A332F), onSurfaceVariant = Color(0xFFAAB6B0),
    outline = Color(0xFF55615B), outlineVariant = Color(0xFF333D38),
    error = Color(0xFFFF8A80),
)

val Display = FontFamily.Serif

private val AppTypography = Typography().let { t ->
    t.copy(
        displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, letterSpacing = (-1).sp),
        headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
        headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 23.sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun OpenPaymentsTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
}
