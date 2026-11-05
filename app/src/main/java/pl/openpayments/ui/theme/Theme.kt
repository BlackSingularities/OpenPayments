package pl.openpayments.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Kolory semantyczne niezależne od palety z tapety.
val Danger = Color(0xFFC8473D)
val Positive = Color(0xFF2E8B57)
val Amber = Color(0xFFE0A030)

/** Paleta marki (zieleń) — gdy kolory z tapety są wyłączone albo system ich nie obsługuje. */
private val BrandLight = expressiveLightColorScheme().copy(
    primary = Color(0xFF1F6F5C), onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2D6), onPrimaryContainer = Color(0xFF002118),
    secondary = Color(0xFF4B635A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8DC), onSecondaryContainer = Color(0xFF072019),
    tertiary = Color(0xFF7B5800), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA6), onTertiaryContainer = Color(0xFF271900),
    background = Color(0xFFF5FBF7), onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF7), onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DF), onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5F1),
    surfaceContainer = Color(0xFFE9EFEB),
    surfaceContainerHigh = Color(0xFFE3EAE5),
    surfaceContainerHighest = Color(0xFFDEE4E0),
    surfaceBright = Color(0xFFF5FBF7),
    outline = Color(0xFF6F7975), outlineVariant = Color(0xFFBFC9C3),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFF8AD5BA), onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF005140), onPrimaryContainer = Color(0xFFA6F2D6),
    secondary = Color(0xFFB2CCC1), onSecondary = Color(0xFF1D352D),
    secondaryContainer = Color(0xFF344B43), onSecondaryContainer = Color(0xFFCDE8DC),
    tertiary = Color(0xFFF7BD48), onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5D4200), onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFBFC9C3),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    surfaceBright = Color(0xFF353B38),
    outline = Color(0xFF89938E), outlineVariant = Color(0xFF3F4945),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

/** Typografia w duchu Androida 16: duże, mocne nagłówki i liczby, zwarte odstępy. */
private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.5).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Mocniej zaokrąglone kształty Expressive. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun OpenPaymentsTheme(mode: String, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> BrandDark
        else -> BrandLight
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        typography = AppTypography,
    ) { content() }
}

/** Czy bieżąca paleta jest ciemna — przydatne dla kolorów kategorii. */
@Composable
fun isDark(): Boolean = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3f < 0.5f }
