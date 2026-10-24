package com.ayush.madv2

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.ayush.madv2.ui.CallAiApp
import com.ayush.madv2.ui.theme.AppTheme
import com.ayush.madv2.ui.theme.MADv2Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.load(this)
        AppTheme.systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        applyBars(AppTheme.isDark)
        setContent {
            MADv2Theme(dynamicColor = false) {
                val dark = AppTheme.isDark
                SideEffect { applyBars(dark) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    CallAiApp()
                }
            }
        }
    }

    private fun applyBars(dark: Boolean) {
        val bars = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
}
