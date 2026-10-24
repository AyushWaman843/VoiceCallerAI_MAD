package com.ayush.madv2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun MADv2Theme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
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
}
