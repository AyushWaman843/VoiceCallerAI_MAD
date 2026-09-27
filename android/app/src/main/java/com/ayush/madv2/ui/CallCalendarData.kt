package com.ayush.madv2.ui

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

internal val CallCalendarZone: ZoneId = ZoneId.of("Asia/Kolkata")

internal data class CalendarCall(
    val call: ScheduledCallUiModel,
    val time: ZonedDateTime,
)

internal fun calendarCall(call: ScheduledCallUiModel): CalendarCall? = runCatching {
    // The API may return UTC; group by the call's IST day rather than the raw date.
    CalendarCall(call, OffsetDateTime.parse(call.scheduledTime).atZoneSameInstant(CallCalendarZone))
}.getOrNull()

internal fun callsByCalendarDate(calls: List<ScheduledCallUiModel>): Map<LocalDate, List<CalendarCall>> =
    calls.distinctBy { it.id }
        .mapNotNull(::calendarCall)
        .sortedBy { it.time.toInstant() }
        .groupBy { it.time.toLocalDate() }

internal fun calendarMonthDays(month: YearMonth): List<LocalDate?> {
    // Monday-first weeks; blank cells keep adjacent months from looking selectable.
    val leadingDays = month.atDay(1).dayOfWeek.value - 1
    val cellCount = ((leadingDays + month.lengthOfMonth() + 6) / 7) * 7
    return List(cellCount) { index ->
        val day = index - leadingDays + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }
}
