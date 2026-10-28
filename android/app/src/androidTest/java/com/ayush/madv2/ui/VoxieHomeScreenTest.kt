package com.ayush.madv2.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ayush.madv2.ui.components.PocketComposer
import com.ayush.madv2.ui.screens.*
import com.ayush.madv2.ui.theme.MADv2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class VoxieHomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeUsesAccountNameRealCallsAndWorkingMicrophone() {
        var microphone = 0
        var selected = ""
        val call = ScheduledCallUiModel("sample", "Batch B Parents", "", "Tomorrow, 6:00 PM", "Exam reminder", "pending", true)
        compose.setContent {
            MADv2Theme(darkTheme = false) {
                Surface {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Box(Modifier.weight(1f)) {
                            VoxieHomeScreen(CallAiUiState(userName = "Priya Shah", upcomingCalls = listOf(call)), {}, {}, { selected = it.id }) {
                                PocketComposer("", {}, false, false, false, emptyList(), remember { FocusRequester() },
                                    {}, { microphone++ }, {}, {}, {}, homeStyle = true)
                            }
                        }
                        VoxieBottomBar("Home", true, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Priya!", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Record voice message").performClick()
        compose.runOnIdle { assertEquals(1, microphone) }
        compose.onNodeWithText("Batch B Parents").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("sample", selected) }
        compose.onNodeWithText("Recent").performClick()
        compose.onNodeWithText("No recent calls").assertExists()
        compose.onNodeWithText("Upcoming").performClick()
        compose.onNodeWithText("Voxie", useUnmergedTree = true).performScrollTo()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "voxie-home.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }
}
