package com.ayush.madv2.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.ayush.madv2.ui.theme.MADv2Theme
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CallCalendarUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun monthNavigationDayDetailsAndCancellation() {
        val today = LocalDate.now(CallCalendarZone)
        val calls = listOf("Ayush Waman", "Mom", "Rahul Shah").mapIndexed { index, name ->
            ScheduledCallUiModel(
                id = "calendar-test-$index", contactName = name, contactNumber = "+910000000000",
                timeLabel = "", message = "Test reminder for $name.",
                status = if (index == 0) "Pending" else "Connected", canCancel = index == 0,
                scheduledTime = "${today}T${14 + index}:00:00+05:30",
            )
        }
        val state = mutableStateOf(CallAiUiState(
            backendBaseUrl = "http://localhost/", upcomingCalls = calls.take(1), historyCalls = calls.drop(1),
        ))
        var cancelledId: String? = null
        compose.setContent {
            MADv2Theme {
                ScheduledTab(state.value, onRefreshCalls = {}, onCancelCall = { id ->
                    cancelledId = id
                    state.value = state.value.copy(
                        upcomingCalls = emptyList(),
                        historyCalls = calls.map { if (it.id == id) it.copy(status = "Cancelled", canCancel = false) else it },
                    )
                })
            }
        }
        val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        compose.onNodeWithText(YearMonth.from(today).format(monthFormat)).assertIsDisplayed()
        saveScreenshot("calendar-month.png")
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText(YearMonth.from(today).plusMonths(1).format(monthFormat)).assertIsDisplayed()
        compose.onNodeWithText("Today").performScrollTo().performClick()
        val dayLabel = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
        compose.onNodeWithContentDescription("$dayLabel ${today.year}, today, 3 calls: Ayush Waman, Mom, Rahul Shah")
            .performScrollTo().performClick()
        compose.onNodeWithText("Test reminder for Ayush Waman.").assertIsDisplayed()
        compose.onNodeWithText("2:00 PM IST / +910000000000").assertIsDisplayed()
        saveScreenshot("calendar-day.png")
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("calendar-test-0", cancelledId) }
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Next month").performScrollTo().performClick()
        val emptyDate = YearMonth.from(today).plusMonths(1).atDay(1)
        val emptyLabel = emptyDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
        compose.onNodeWithContentDescription("$emptyLabel ${emptyDate.year}, 0 calls")
            .performScrollTo().performClick()
        compose.onNodeWithText("No calls on this day.").assertIsDisplayed()
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
