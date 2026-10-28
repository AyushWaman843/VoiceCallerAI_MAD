package com.ayush.madv2.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ayush.madv2.ui.screens.CampaignComposer
import com.ayush.madv2.ui.theme.MADv2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CampaignConfirmationTest {
    @get:Rule val compose = createComposeRule()

    private val group = BusinessGroup("selected-groups", "Appointments", listOf(
        BusinessContact("aarav", "Aarav", "+919876543210", "approved", "Appointments")))

    @Test fun completePromptShowsOneConfirmationWithoutSelectionOrReentry() {
        var confirmations = 0
        val draft = CampaignDetail("draft", "Appointments", group.id, "Please attend your appointment.",
            "https://example.com/audio.mp3", "2026-11-01T09:15:00+05:30", "Asia/Kolkata", "draft",
            listOf(CampaignRecipient("aarav", "Aarav", "+919876543210")), 1, 1, 200, null, "Remider")
        compose.setContent {
            MADv2Theme {
                CampaignComposer(group, draft, false, null, "Call Aarav using reminder template at 9:15",
                    "Asia/Kolkata", {}, { _, _, _ -> }, {}, { confirmations++ })
            }
        }
        compose.onNodeWithText("Template: Remider").assertIsDisplayed()
        compose.onNodeWithText("Please attend your appointment.").assertIsDisplayed()
        compose.onNodeWithText("Aarav").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue with selected contacts").assertDoesNotExist()
        compose.onNodeWithText("What should Voxie say?").assertDoesNotExist()
        compose.onNodeWithText("Confirm & schedule").performClick()
        compose.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test fun incompletePromptPreservesTextWithoutRequiringRecipientSelection() {
        compose.setContent {
            MADv2Theme {
                CampaignComposer(group, null, false, "Please add a time.", "Call Aarav using reminder template",
                    "Asia/Kolkata", {}, { _, _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithText("Please add a time.").assertIsDisplayed()
        compose.onNodeWithText("Call Aarav using reminder template").assertExists()
        compose.onNodeWithText("Continue with selected contacts").assertDoesNotExist()
    }
}
