package com.ayush.madv2.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ayush.madv2.ui.theme.MADv2Theme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CallCalendarUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun monthNavigationDayDetailsAndConfirmedCancellation() {
        val today = LocalDate.now(CallCalendarZone)
        val call = ScheduledCallUiModel("calendar-test", "Ayush Waman", "+910000000000",
            "2:00 PM IST", "Test reminder.", "Pending", true, "${today}T14:00:00+05:30")
        val state = mutableStateOf(CallAiUiState(backendBaseUrl = "http://localhost/", upcomingCalls = listOf(call)))
        var cancelledId: String? = null
        compose.setContent {
            MADv2Theme {
                ScheduledTab(state.value, {}, { id -> cancelledId = id })
            }
        }
        val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        compose.onNodeWithText(YearMonth.from(today).format(monthFormat)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText(YearMonth.from(today).plusMonths(1).format(monthFormat)).assertIsDisplayed()
        compose.onNodeWithText("Today").performScrollTo().performClick()
        compose.onNodeWithText("Ayush Waman").performScrollTo().performClick()
        compose.onAllNodesWithText("Test reminder.").onLast().assertIsDisplayed()
        compose.onNodeWithText("Cancel scheduled call").performScrollTo().performClick()
        compose.runOnIdle { assertNull(cancelledId) }
        compose.onNodeWithText("Keep call").performClick()
        compose.runOnIdle { assertNull(cancelledId) }
        compose.onNodeWithText("Cancel scheduled call").performScrollTo().performClick()
        compose.onNodeWithText("Cancel call").performClick()
        compose.runOnIdle { assertEquals(call.id, cancelledId) }
    }
}
