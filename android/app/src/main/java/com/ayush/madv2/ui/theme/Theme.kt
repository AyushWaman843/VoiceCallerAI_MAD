package com.ayush.madv2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = AppAccent,
    secondary = AppAccent,
    tertiary = AppAccent,
    background = AppBackground,
    surface = AppPanel,
    surfaceVariant = AppPanelAlt,
    outline = AppLine,
    onPrimary = AppPanel,
    onSecondary = AppPanel,
    onTertiary = AppPanel,
    onBackground = AppText,
    onSurface = AppText,
    onSurfaceVariant = AppMuted,
)

private val DarkColorScheme = darkColorScheme(
    primary = AppAccent,
    secondary = AppAccent,
    tertiary = AppAccent,
    background = AppBackground,
    surface = AppPanel,
    surfaceVariant = AppPanelAlt,
    outline = AppLine,
    onPrimary = AppPanel,
    onSecondary = AppPanel,
    onTertiary = AppPanel,
    onBackground = AppText,
    onSurface = AppText,
    onSurfaceVariant = AppMuted,
)

@Composable
fun MADv2Theme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
