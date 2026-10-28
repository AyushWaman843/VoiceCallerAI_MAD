package com.ayush.madv2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

codezxsWIN/mad-foundation
private val LightColors = lightColorScheme(
    primary = Color(0xFF202123), onPrimary = Color.White,
    secondary = Color(0xFF28745D), onSecondary = Color.White,
    background = Color(0xFFF7F7F9), onBackground = Color(0xFF202123),
    surface = Color.White, onSurface = Color(0xFF202123),
    surfaceVariant = Color(0xFFF0F0EE), onSurfaceVariant = Color(0xFF686967),
    surfaceContainer = Color(0xFFF0F0EE), surfaceContainerHigh = Color(0xFFE8E8E6),
    outline = Color(0xFFDADBD7), outlineVariant = Color(0xFFE8E8E5),
    error = Color(0xFFAF3636), errorContainer = Color(0xFFFFEDEA),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFF0F0EE), onPrimary = Color(0xFF202123),
    secondary = Color(0xFF81C6AA), onSecondary = Color(0xFF143B2F),
    background = Color(0xFF202123), onBackground = Color(0xFFF0F0EE),
    surface = Color(0xFF202123), onSurface = Color(0xFFF0F0EE),
    surfaceVariant = Color(0xFF2D2E30), onSurfaceVariant = Color(0xFFB0B1AC),
    surfaceContainer = Color(0xFF2D2E30), surfaceContainerHigh = Color(0xFF38393B),
    outline = Color(0xFF4B4D4A), outlineVariant = Color(0xFF393B38),
    error = Color(0xFFFFABA5), errorContainer = Color(0xFF492C2C),
)

@Composable
fun MADv2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),

@Composable
fun MADv2Theme(
main
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
codezxsWIN/mad-foundation
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography, content = content)

    AppTheme.systemDark = isSystemInDarkTheme()

    val colorScheme = if (AppTheme.isDark) {
        darkColorScheme(
            primary = AppAccent,
            secondary = AppAccent,
            tertiary = AppAccent,
            background = AppBackground,
            surface = AppPanel,
            surfaceVariant = AppPanelAlt,
            outline = AppLine,
            onPrimary = AppOnAccent,
            onSecondary = AppOnAccent,
            onTertiary = AppOnAccent,
            onBackground = AppText,
            onSurface = AppText,
            onSurfaceVariant = AppMuted,
        )
    } else {
        lightColorScheme(
            primary = AppAccent,
            secondary = AppAccent,
            tertiary = AppAccent,
            background = AppBackground,
            surface = AppPanel,
            surfaceVariant = AppPanelAlt,
            outline = AppLine,
            onPrimary = AppOnAccent,
            onSecondary = AppOnAccent,
            onTertiary = AppOnAccent,
            onBackground = AppText,
            onSurface = AppText,
            onSurfaceVariant = AppMuted,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
main
}
