package com.ayush.madv2.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.ayush.madv2.network.AgentResultDto
import com.ayush.madv2.ui.components.*
import com.ayush.madv2.ui.screens.*
import com.ayush.madv2.ui.theme.MADv2Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PocketCallUiTest {
    @get:Rule val compose = createComposeRule()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(250)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun chatConfirmationVoiceAndThemes() {
        val state = mutableStateOf(CallAiUiState(backendBaseUrl = "http://localhost/"))
        val dark = mutableStateOf(false)
        var confirmed = 0
        var cancelled = 0
        compose.setContent {
            MADv2Theme(darkTheme = dark.value) {
                Surface {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                        Text("Voxie", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                        VoxieRequestScreen(state.value, List(37) { if (it % 3 == 0) .7f else .2f },
                            onPrompt = { state.value = state.value.copy(prompt = it) },
                            onSubmit = {}, onConfirm = { confirmed++ }, onEdit = {}, onField = { _, _ -> },
                            onPlus = {}, onMic = { state.value = state.value.copy(isRecording = true) },
                            onCancelRecording = { cancelled++; state.value = state.value.copy(isRecording = false) },
                            onFinishRecording = {}, onSearchContacts = {}, onViewCall = {}, onDismissError = {})
                    }
                }
            }
        }
        compose.onNodeWithText("What would you like\nme to call about?").assertIsDisplayed()
        screenshot("pocket-home-light.png")
        compose.runOnIdle { state.value = state.value.copy(prompt = "Call Mom at 7 PM") }
        compose.onNodeWithContentDescription("Message Voxie").assertTextContains("Call Mom at 7 PM")
        compose.runOnIdle { state.value = state.value.copy(prompt = "") }
        compose.onNodeWithContentDescription("Record voice message").performClick()
        compose.onNodeWithContentDescription("Microphone volume waveform").assertIsDisplayed()
        screenshot("pocket-waveform.png")
        compose.onNodeWithContentDescription("Cancel recording").performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
        val draft = ConfirmationDraft("Mom", "+910000000000", "Hi Mom, this is an automated message on behalf of Ayush. Please remember our meeting.",
            "2026-10-01T19:00:00+05:30", AgentResultDto())
        compose.runOnIdle {
            state.value = state.value.copy(conversation = listOf(ChatEntry(text = "Call Mom tomorrow at 7 PM.", fromUser = true)),
                confirmationDraft = draft)
        }
        compose.onNodeWithText("Schedule call").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
        screenshot("pocket-confirmation-light.png")
        compose.runOnIdle { dark.value = true }
        screenshot("pocket-confirmation-dark.png")
        compose.runOnIdle { state.value = state.value.copy(isProcessing = true) }
        compose.onNodeWithText("Schedule call").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun contactsSearchIncludesBeyondFirstTen() {
        val contacts = (1..25).map { ContactDraft(it.toLong(), "Contact ${it.toString().padStart(2, '0')}", "+910000000${it.toString().padStart(2,'0')}") }
        var selected: ContactDraft? = null
        compose.setContent {
            MADv2Theme {
                Surface { ContactsScreen(contacts, true, false, {}, { selected = it }) }
            }
        }
        compose.onNodeWithText("Contact 25").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("Contact 25")
        compose.onNode(hasText("Contact 25") and !hasSetTextAction()).performClick()
        compose.runOnIdle { assertEquals(25L, selected?.id) }
        screenshot("pocket-contacts.png")
    }

    @Test
    fun secondaryScreensAndDarkSettings() {
        val screen = mutableIntStateOf(0)
        val call = ScheduledCallUiModel("test", "Mom", "+910000000000",
            "Tomorrow at 7:00 PM IST", "Remember our meeting.", "Pending", true, "2026-10-01T19:00:00+05:30")
        compose.setContent {
            MADv2Theme(darkTheme = screen.intValue == 2) {
                Surface {
                    when(screen.intValue) {
                        0 -> CallsScreen(listOf(call), false, null, onCall = {}, onRefresh = {})
                        1 -> ContactsScreen(emptyList(), false, false, {}, {})
                        else -> SettingsScreen(CallAiUiState(backendBaseUrl = "http://localhost/"), "Dark", {}, {}, {}, {},
                            page = SettingsPage.Appearance)
                    }
                }
            }
        }
        compose.onNodeWithText("Mom").assertIsDisplayed()
        screenshot("pocket-upcoming.png")
        compose.runOnIdle { screen.intValue = 1 }
        compose.onNodeWithText("Allow contacts").assertIsDisplayed()
        screenshot("pocket-contacts-permission.png")
        compose.runOnIdle { screen.intValue = 2 }
        compose.onNodeWithText("Dark mode enabled").assertIsDisplayed()
        screenshot("pocket-settings-dark.png")
    }
}
