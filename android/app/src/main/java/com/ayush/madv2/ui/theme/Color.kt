package com.ayush.madv2.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

enum class AccentChoice(val label: String, val light: Color, val dark: Color) {
    Green("Green", Color(0xFF16805A), Color(0xFF3FBF8A)),
    Blue("Blue", Color(0xFF2563C9), Color(0xFF6B9CF0)),
    Purple("Purple", Color(0xFF7248C7), Color(0xFFA98AF0)),
    Orange("Orange", Color(0xFFC25E12), Color(0xFFF0954F)),
}

object AppTheme {
    var mode by mutableStateOf(ThemeMode.System)
        private set
    var accent by mutableStateOf(AccentChoice.Green)
        private set

    // Set from the system setting at the top of MADv2Theme
    var systemDark = false

    val isDark: Boolean
        get() = when (mode) {
            ThemeMode.System -> systemDark
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        mode = ThemeMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: ThemeMode.System
        accent = AccentChoice.entries.firstOrNull { it.name == prefs.getString("accent", null) } ?: AccentChoice.Green
    }

    fun update(context: Context, newMode: ThemeMode = mode, newAccent: AccentChoice = accent) {
        mode = newMode
        accent = newAccent
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            .edit()
            .putString("mode", newMode.name)
            .putString("accent", newAccent.name)
            .apply()
    }
}

val AppBackground get() = if (AppTheme.isDark) Color(0xFF121413) else Color(0xFFF4F3EE)
val AppPanel get() = if (AppTheme.isDark) Color(0xFF1C1F1D) else Color(0xFFFFFFFF)
val AppPanelAlt get() = if (AppTheme.isDark) Color(0xFF282C2A) else Color(0xFFEFEEE8)
val AppLine get() = if (AppTheme.isDark) Color(0xFF353A38) else Color(0xFFE3E1D9)
val AppText get() = if (AppTheme.isDark) Color(0xFFECEFEC) else Color(0xFF16201B)
val AppMuted get() = if (AppTheme.isDark) Color(0xFF9BA39F) else Color(0xFF69716C)
val AppAccent get() = if (AppTheme.isDark) AppTheme.accent.dark else AppTheme.accent.light
val AppAccentSoft get() = AppAccent.copy(alpha = if (AppTheme.isDark) 0.22f else 0.14f)
val AppOnAccent get() = if (AppTheme.isDark) Color(0xFF0B1410) else Color.White
val AppDanger get() = if (AppTheme.isDark) Color(0xFFE5808B) else Color(0xFFC05A67)
val AppDangerSoft get() = AppDanger.copy(alpha = 0.12f)
