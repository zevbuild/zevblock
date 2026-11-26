package com.zevbuild.zevblock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class ZevSemanticPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val green: Color,
    val greenBtn: Color,
    val greenBtnFg: Color,
    val teal: Color,
    val tealBtnFg: Color,
    val amber: Color,
    val amberBtnFg: Color,
    val amberSoftText: Color,
    val red: Color,
    val redBtn: Color,
    val redBtnFg: Color
)

val DarkZevSemanticPalette = ZevSemanticPalette(
    isDark = true,
    background = ShieldNavyDark,
    surface = ShieldSurfaceDark,
    card = ShieldCardDark,
    border = ShieldBorderDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textMuted = TextMutedDark,
    green = ShieldGreen,
    greenBtn = ShieldGreenDark,
    greenBtnFg = Color.White,
    teal = ShieldTeal,
    tealBtnFg = ShieldTealOnFilled,
    amber = ShieldAmber,
    amberBtnFg = ShieldAmberOnFilled,
    amberSoftText = ShieldAmberSoftText,
    red = ShieldRed,
    redBtn = ShieldRedDark,
    redBtnFg = Color.White
)

val LightZevSemanticPalette = ZevSemanticPalette(
    isDark = false,
    background = ShieldSurfaceLight,
    surface = ShieldCardLight,
    card = ShieldCardElevatedLight,
    border = ShieldBorderLight,
    textPrimary = ShieldNavyLight,
    textSecondary = TextSecondaryLight,
    textMuted = TextMutedLight,
    green = ShieldGreenLight,
    greenBtn = ShieldGreenDarkLight,
    greenBtnFg = Color.White,
    teal = ShieldTealLight,
    tealBtnFg = Color.White,
    amber = ShieldAmberLight,
    amberBtnFg = Color.White,
    amberSoftText = ShieldAmberSoftTextLight,
    red = ShieldRedLight,
    redBtn = ShieldRedDarkLight,
    redBtnFg = Color.White
)

val LocalZevPalette = staticCompositionLocalOf { DarkZevSemanticPalette }

object ZevTheme {
    val colors: ZevSemanticPalette
        @Composable get() = LocalZevPalette.current
}

private val DarkColorScheme = darkColorScheme(
    primary = ShieldGreen,
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF005327),
    onPrimaryContainer = Color(0xFF6CFF9F),
    secondary = ShieldTeal,
    onSecondary = ShieldTealOnFilled,
    secondaryContainer = Color(0xFF004D6B),
    onSecondaryContainer = Color(0xFFBCE9FF),
    tertiary = ShieldAmber,
    onTertiary = ShieldAmberOnFilled,
    background = ShieldNavyDark,
    surface = ShieldSurfaceDark,
    surfaceVariant = ShieldCardDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = ShieldBorderDark,
    error = ShieldRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ShieldGreenDarkLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = ShieldTealLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = ShieldAmberLight,
    onTertiary = Color.White,
    background = ShieldSurfaceLight,
    surface = ShieldCardLight,
    surfaceVariant = ShieldCardElevatedLight,
    onBackground = ShieldNavyLight,
    onSurface = ShieldNavyLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = ShieldBorderLight,
    error = ShieldRedLight,
    onError = Color.White
)

@Composable
fun ZevBlockTheme(
    themeMode: String = "dark", // "dark", "light", "system"
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val semanticPalette = if (useDark) DarkZevSemanticPalette else LightZevSemanticPalette

    CompositionLocalProvider(LocalZevPalette provides semanticPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    ZevBlockTheme(
        themeMode = if (darkTheme) "dark" else "light",
        dynamicColor = dynamicColor,
        content = content
    )
}
