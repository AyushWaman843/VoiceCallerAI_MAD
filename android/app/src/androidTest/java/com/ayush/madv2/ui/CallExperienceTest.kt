package com.ayush.madv2.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ayush.madv2.ui.components.PocketComposer
import com.ayush.madv2.ui.screens.VoxieHomeScreen
import com.ayush.madv2.ui.theme.MADv2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CallExperienceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun businessHomeHasOnlyUpcomingAndRecentAndAccessibleSendIcon() {
        var sends = 0
        compose.setContent {
            MADv2Theme(darkTheme = false) {
                VoxieHomeScreen(CallAiUiState(userName = "The Dental Clinic"), {}, {}, {}, business = true) {
                    PocketComposer("Call Aarav tomorrow", {}, false, false, false, emptyList(),
                        remember { FocusRequester() }, {}, {}, {}, {}, { sends++ }, homeStyle = true)
                }
            }
        }
        compose.onNodeWithText("Campaigns").assertDoesNotExist()
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.runOnIdle { assertEquals(1, sends) }
        compose.onNodeWithText("Upcoming").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Recent").performClick()
        compose.onNodeWithText("No recent calls").performScrollTo().assertIsDisplayed()
    }

    @Test fun successfulCampaignClearsThePrompt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = CallAiViewModel(initialState = CallAiUiState(userId = "", prompt = "Call Aarav"))
            model.campaignScheduled(listOf("Aarav", "Akshay Damle"))
            assertEquals("", model.uiState.value.prompt)
            assertEquals("Scheduled calls for Aarav, Akshay Damle.", model.uiState.value.conversation.last().text)
        }
    }

    @Test fun longPromptExpandsWithoutLosingTypedText() {
        var prompt by mutableStateOf("")
        compose.setContent {
            MADv2Theme {
                PocketComposer(prompt, { prompt = it }, false, false, false, emptyList(),
                    remember { FocusRequester() }, {}, {}, {}, {}, {}, homeStyle = true)
            }
        }
        compose.onNodeWithContentDescription("Message Voxie").performTextInput(
            "Call Aarav and Akshay Damle from Appointments tomorrow at 9pm")
        compose.runOnIdle { assertEquals("Call Aarav and Akshay Damle from Appointments tomorrow at 9pm", prompt) }
        compose.onNodeWithContentDescription("Add group or template").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
    }

    @Test fun newRequestClearsPreviousScheduledMessageButKeepsPrompt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = CallAiViewModel(initialState = CallAiUiState(userId = "", prompt = "Call Aarav and Akshay"))
            model.campaignScheduled(listOf("Aarav", "Akshay Damle"))
            model.setPrompt("Call Aarav and Akshay again")
            model.beginNewRequest()
            assertEquals("Call Aarav and Akshay again", model.uiState.value.prompt)
            assertEquals(emptyList<ChatEntry>(), model.uiState.value.conversation)
        }
    }

    @Test fun busyComposerShowsProgressInsteadOfAnotherSendAction() {
        compose.setContent {
            MADv2Theme {
                PocketComposer("Call Aarav", {}, false, false, true, emptyList(),
                    remember { FocusRequester() }, {}, {}, {}, {}, {}, homeStyle = true)
            }
        }
        compose.onNodeWithText("Working on your request").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send message").assertDoesNotExist()
    }
}
