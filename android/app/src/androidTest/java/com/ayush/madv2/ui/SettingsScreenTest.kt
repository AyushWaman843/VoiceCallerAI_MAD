package com.ayush.madv2.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ayush.madv2.ui.screens.SettingsPage
import com.ayush.madv2.ui.screens.SettingsScreen
import com.ayush.madv2.ui.theme.MADv2Theme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun categoriesThemeAndSearchOpenFocusedSettings() {
        var page by mutableStateOf(SettingsPage.Overview)
        var theme by mutableStateOf("System")
        compose.setContent {
            MADv2Theme {
                SettingsScreen(CallAiUiState(backendBaseUrl = "https://example.com/"), theme,
                    onTheme = { theme = it }, onSignOut = {}, onBackend = {}, onPermissions = {},
                    page = page, onPage = { page = it })
            }
        }
        capture("settings-overview.png")
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithContentDescription("Dark theme").performClick()
        compose.onNodeWithText("Dark mode enabled").assertIsDisplayed()
        capture("settings-appearance.png")
        compose.onNode(hasSetTextAction()).performTextInput("backend")
        compose.onNodeWithText("Connection").performClick()
        compose.onNodeWithText("Backend URL").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
