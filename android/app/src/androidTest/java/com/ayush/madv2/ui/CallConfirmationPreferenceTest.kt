package com.ayush.madv2.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ayush.madv2.ui.components.*
import com.ayush.madv2.ui.theme.MADv2Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CallConfirmationPreferenceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun checkboxAppliesOnlyAfterConfirmAndNextDraftSchedulesOnce() {
        var required by mutableStateOf(true)
        var key by mutableStateOf("first")
        var ready by mutableStateOf(true)
        var calls = 0
        compose.setContent {
            MADv2Theme {
                CompositionLocalProvider(LocalCallConfirmation provides CallConfirmationPreference(required) { required = it }) {
                    AutoScheduleReadyCall(key, ready) { calls++ }
                    ConfirmCallButton("Confirm", true) { calls++ }
                }
            }
        }
        compose.onNode(isToggleable()).performClick()
        compose.runOnIdle { assertTrue(required); assertEquals(0, calls) }
        compose.onNodeWithText("Confirm", substring = false).performClick()
        compose.runOnIdle { assertFalse(required); assertEquals(1, calls); key = "second" }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, calls); ready = false }
        compose.waitForIdle()
        compose.runOnIdle { ready = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun settingsCanRestoreConfirmationAndMissingDetailsNeverSubmit() {
        var required by mutableStateOf(false)
        var key by mutableStateOf<String?>(null)
        var calls = 0
        compose.setContent {
            MADv2Theme {
                CompositionLocalProvider(LocalCallConfirmation provides CallConfirmationPreference(required) { required = it }) {
                    Column {
                        AutoScheduleReadyCall(key, true) { calls++ }
                        CallConfirmationSetting()
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNode(isToggleable()).performClick()
        compose.runOnIdle { assertTrue(required); key = "ready" }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, calls) }
    }
}
