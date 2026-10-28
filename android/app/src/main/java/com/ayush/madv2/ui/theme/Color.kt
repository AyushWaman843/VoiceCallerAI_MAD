package com.ayush.madv2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

// Semantic aliases keep the calendar and conversation on the same theme.
val AppBackground @Composable get() = MaterialTheme.colorScheme.background
val AppPanel @Composable get() = MaterialTheme.colorScheme.surface
val AppPanelAlt @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val AppLine @Composable get() = MaterialTheme.colorScheme.outlineVariant
val AppText @Composable get() = MaterialTheme.colorScheme.onSurface
val AppMuted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val AppAccent @Composable get() = MaterialTheme.colorScheme.secondary
val AppAccentSoft @Composable get() = MaterialTheme.colorScheme.secondary.copy(alpha = .12f)
val AppDanger @Composable get() = MaterialTheme.colorScheme.error
val AppDangerSoft @Composable get() = MaterialTheme.colorScheme.errorContainer
