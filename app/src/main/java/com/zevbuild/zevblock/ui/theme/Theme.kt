package com.zevbuild.zevblock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ShieldGreen,
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF005327),
    onPrimaryContainer = Color(0xFF6CFF9F),
    secondary = ShieldTeal,
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF004D6B),
    onSecondaryContainer = Color(0xFFBCE9FF),
    tertiary = ShieldAmber,
    background = ShieldNavyDark,
    surface = ShieldSurfaceDark,
    surfaceVariant = ShieldCardDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = ShieldBorderDark,
    error = ShieldRed
)

private val LightColorScheme = lightColorScheme(
    primary = ShieldGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5E9),
    onPrimaryContainer = Color(0xFF003919),
    secondary = Color(0xFF0288D1),
    background = ShieldSurfaceLight,
    surface = ShieldCardLight,
    onBackground = ShieldNavyLight,
    onSurface = ShieldNavyLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek security dark theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
