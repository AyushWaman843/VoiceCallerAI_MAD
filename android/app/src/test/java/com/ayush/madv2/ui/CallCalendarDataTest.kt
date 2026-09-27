package com.ayush.madv2.ui

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallCalendarDataTest {
    @Test
    fun leapFebruaryContainsEveryDayInMondayFirstColumns() {
        val cells = calendarMonthDays(YearMonth.of(2028, 2))
        assertEquals(35, cells.size)
        assertNull(cells.first())
        assertEquals(LocalDate.of(2028, 2, 1), cells[1])
        assertEquals((1..29).toList(), cells.filterNotNull().map { it.dayOfMonth })
        cells.forEachIndexed { index, date ->
            if (date != null) assertEquals(date.dayOfWeek.value - 1, index % 7)
        }
    }

    @Test
    fun sixWeekMonthDoesNotLoseLastDay() {
        val cells = calendarMonthDays(YearMonth.of(2026, 3))
        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 3, 1), cells[6])
        assertEquals(LocalDate.of(2026, 3, 31), cells[36])
    }

    @Test
    fun fourWeekFebruaryAndYearBoundaryAreSupported() {
        assertEquals(28, calendarMonthDays(YearMonth.of(2027, 2)).size)
        val january = YearMonth.of(2026, 12).plusMonths(1)
        assertEquals(LocalDate.of(2027, 1, 1), calendarMonthDays(january).filterNotNull().first())
    }

    @Test
    fun utcMidnightBoundaryUsesIstDateAndChronologicalOrder() {
        val later = call("later", "2026-09-27T23:00:00Z")
        val earlier = call("earlier", "2026-09-28T00:15:00+05:30")
        val grouped = callsByCalendarDate(listOf(later, earlier, earlier))
        assertEquals(setOf(LocalDate.of(2026, 9, 28)), grouped.keys)
        assertEquals(listOf("earlier", "later"), grouped.values.single().map { it.call.id })
        assertEquals(4, grouped.values.single().last().time.hour)
        assertEquals(30, grouped.values.single().last().time.minute)
    }

    @Test
    fun historyAndMultipleCallsOnOneDayRemainAvailable() {
        val calls = listOf(
            call("pending", "2026-09-27T13:00:00+05:30"),
            call("failed", "2026-09-27T12:00:00+05:30").copy(status = "Failed", canCancel = false),
            call("cancelled", "2026-09-27T14:00:00+05:30").copy(status = "Cancelled", canCancel = false),
            call("past", "2025-12-01T09:00:00+05:30").copy(status = "Connected", canCancel = false),
        )
        val grouped = callsByCalendarDate(calls)
        assertEquals(3, grouped[LocalDate.of(2026, 9, 27)]?.size)
        assertEquals("past", grouped[LocalDate.of(2025, 12, 1)]?.single()?.call?.id)
    }

    @Test
    fun invalidAndEmptyDatesDoNotCrashCalendar() {
        assertNull(calendarCall(call("bad", "invalid")))
        assertNull(calendarCall(call("missing", "")))
        assertTrue(callsByCalendarDate(emptyList()).isEmpty())
    }

    private fun call(id: String, time: String) = ScheduledCallUiModel(
        id = id,
        contactName = "Contact $id",
        contactNumber = "",
        timeLabel = "",
        message = "Test message",
        status = "Pending",
        canCancel = true,
        scheduledTime = time,
    )
}
