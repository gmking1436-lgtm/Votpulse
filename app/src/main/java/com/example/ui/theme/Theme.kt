package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Pure AMOLED Pitch Black ColorScheme.
 * Features true #000000 background to turn off OLED pixels completely for maximum energy saving,
 * paired with electric green and cyan high-contrast accents.
 */
val AmoledDarkColorScheme: ColorScheme = darkColorScheme(
    primary = VoltGreen,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF003816),
    onPrimaryContainer = Color(0xFF69F0AE),
    secondary = VoltElectricCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00333D),
    onSecondaryContainer = Color(0xFF84FFFF),
    tertiary = VoltAmber,
    onTertiary = Color.Black,
    background = PureAmoledBlack,
    onBackground = Color.White,
    surface = PureAmoledSurface,
    onSurface = TextPrimary,
    surfaceVariant = PureAmoledSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = PureAmoledBorder,
    outlineVariant = Color(0xFF1A1A1A),
    error = VoltRed,
    onError = Color.Black
)

/**
 * Cyberpunk Neon ColorScheme.
 * Synthwave-inspired deep slate background accentuated with blazing neon cyan,
 * hot magenta, and electric lime.
 */
val CyberpunkNeonColorScheme: ColorScheme = darkColorScheme(
    primary = CyberpunkCyan,
    onPrimary = Color(0xFF00151A),
    primaryContainer = Color(0xFF003844),
    onPrimaryContainer = Color(0xFF80FAFF),
    secondary = CyberpunkMagenta,
    onSecondary = Color(0xFF1F000D),
    secondaryContainer = Color(0xFF470020),
    onSecondaryContainer = Color(0xFFFF80BF),
    tertiary = CyberpunkLime,
    onTertiary = Color(0xFF051D00),
    background = CyberpunkBackground,
    onBackground = Color(0xFFF0F4FC),
    surface = CyberpunkSurface,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = CyberpunkSurfaceVariant,
    onSurfaceVariant = Color(0xFF9AA5BC),
    outline = CyberpunkBorder,
    outlineVariant = Color(0xFF232B45),
    error = Color(0xFFFF3366),
    onError = Color(0xFF1A0007)
)

/**
 * Minimal Frosted Glass ColorScheme.
 * Atmospheric deep navy background with icy sky blue, periwinkle, and mint reflections.
 */
val FrostedGlassColorScheme: ColorScheme = darkColorScheme(
    primary = FrostedIcyBlue,
    onPrimary = Color(0xFF001B2E),
    primaryContainer = Color(0xFF073857),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = FrostedIndigo,
    onSecondary = Color(0xFF0E1338),
    secondaryContainer = Color(0xFF242C6B),
    onSecondaryContainer = Color(0xFFC7D2FE),
    tertiary = FrostedMint,
    onTertiary = Color(0xFF002919),
    background = FrostedGlassBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = FrostedGlassSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = FrostedGlassSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = FrostedGlassBorder,
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFFB7185),
    onError = Color(0xFF29030B)
)

/**
 * Standard Fallback Material Dark ColorScheme.
 */
private val StandardMaterialDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
)

/**
 * Standard Fallback Material Light ColorScheme for pre-Android 12 devices.
 */
private val StandardMaterialLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

/**
 * Main application theme provider supporting the 4 distinct visual themes.
 */
@Composable
fun VoltPulseTheme(
    themeMode: AppThemeMode = AppThemeMode.DYNAMIC_MATERIAL_YOU,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val effectiveMode = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AppThemeMode.DYNAMIC_MATERIAL_YOU
    } else {
        themeMode
    }

    val colorScheme = when (effectiveMode) {
        AppThemeMode.DYNAMIC_MATERIAL_YOU -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) StandardMaterialDarkColorScheme else StandardMaterialLightColorScheme
            }
        }
        AppThemeMode.AMOLED_PITCH_BLACK -> AmoledDarkColorScheme
        AppThemeMode.CYBERPUNK_NEON -> CyberpunkNeonColorScheme
        AppThemeMode.MINIMAL_FROSTED_GLASS -> FrostedGlassColorScheme
    }

    CompositionLocalProvider(
        LocalAppThemeMode provides effectiveMode
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Backward compatibility wrapper for Robolectric unit tests and legacy previews.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    VoltPulseTheme(
        themeMode = AppThemeMode.DYNAMIC_MATERIAL_YOU,
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}
