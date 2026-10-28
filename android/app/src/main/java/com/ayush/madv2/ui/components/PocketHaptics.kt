package com.ayush.madv2.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

class PocketHaptics(private val view: View) {
    companion object {
        private val lastTap = java.util.WeakHashMap<View, Long>()
    }
    fun tap() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    fun success() = perform(if (Build.VERSION.SDK_INT >= 30)
        HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)

    fun error() = perform(if (Build.VERSION.SDK_INT >= 30)
        HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    private fun perform(effect: Int) {
        if (effect == HapticFeedbackConstants.KEYBOARD_TAP) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - (lastTap[view] ?: 0L) < 45L) return
            lastTap[view] = now
        }
        // Native feedback respects system settings; background responses stay silent.
        if (view.isShown && view.hasWindowFocus()) view.performHapticFeedback(effect)
    }
}

@Composable
fun rememberPocketHaptics(): PocketHaptics {
    val view = LocalView.current
    return remember(view) { PocketHaptics(view) }
}

@Composable
fun hapticClick(action: () -> Unit): () -> Unit {
    val haptics = rememberPocketHaptics()
    return { haptics.tap(); action() }
}
