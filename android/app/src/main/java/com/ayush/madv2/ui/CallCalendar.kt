package com.ayush.madv2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.ayush.madv2.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val DayTitle = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
private val CallTime = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallCalendar(
    uiState: CallAiUiState,
    modifier: Modifier = Modifier,
    callContent: @Composable (ScheduledCallUiModel) -> Unit,
) {
    val today = LocalDate.now(CallCalendarZone)
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedValue by rememberSaveable { mutableStateOf(today.toString()) }
    var showDay by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.parse(monthValue)
    val selectedDate = LocalDate.parse(selectedValue)
    val allCalls = remember(uiState.upcomingCalls, uiState.historyCalls) {
        (uiState.upcomingCalls + uiState.historyCalls).distinctBy { it.id }
    }
    val byDate = remember(allCalls) { callsByCalendarDate(allCalls) }
    val monthEntries = byDate.entries.filter { YearMonth.from(it.key) == month }.sortedBy { it.key }
    val undatedCalls = remember(allCalls) { allCalls.filter { calendarCall(it) == null } }
    val openDay: (LocalDate) -> Unit = {
        selectedValue = it.toString()
        showDay = true
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (uiState.isRefreshingCalls) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Refreshing calls" },
                    color = AppAccent,
                    trackColor = AppAccentSoft,
                )
            }
        }
        uiState.errorMessage?.let { message ->
            item { CalendarNotice(message, error = true) }
        }
        item {
            MonthGrid(
                month = month,
                today = today,
                selectedDate = selectedDate,
                byDate = byDate,
                onPrevious = { monthValue = month.minusMonths(1).toString() },
                onNext = { monthValue = month.plusMonths(1).toString() },
                onToday = {
                    monthValue = YearMonth.from(today).toString()
                    selectedValue = today.toString()
                },
                onSelectDay = openDay,
            )
        }
        item {
            Text(
                "Tap a date to see its calls. All times in IST.",
                color = AppMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("This month", color = AppText, fontWeight = FontWeight.Bold)
                val count = monthEntries.sumOf { it.value.size }
                Text("$count ${if (count == 1) "call" else "calls"}", color = AppMuted)
            }
        }
        if (monthEntries.isEmpty()) {
            item {
                CalendarNotice(
                    if (uiState.isRefreshingCalls) "Loading calls..."
                    else if (uiState.errorMessage != null) "Calls could not be refreshed. Try the refresh button."
                    else "No calls in ${month.format(MonthTitle)}. Browse another month or schedule a call from Home.",
                )
            }
        }
        items(monthEntries, key = { it.key.toString() }) { (date, calls) ->
            Surface(
                onClick = { openDay(date) },
                color = AppPanel,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.dayOfMonth.toString(), color = AppText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(date.dayOfWeek.name.take(3), color = AppMuted, fontSize = 10.sp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            calls.joinToString { it.call.contactName },
                            color = AppText,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${calls.first().time.format(CallTime)}${if (calls.size > 1) " +${calls.size - 1} more" else ""}",
                            color = AppMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("View", color = AppText, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        // Keep malformed legacy timestamps visible instead of silently losing call history.
        if (undatedCalls.isNotEmpty()) {
            item { Text("Calls with unavailable dates", color = AppMuted) }
            items(undatedCalls, key = { "undated-${it.id}" }) { callContent(it) }
        }
    }

    if (showDay) {
        val dayCalls = byDate[selectedDate].orEmpty()
        ModalBottomSheet(
            onDismissRequest = { showDay = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppBackground,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(selectedDate.format(DayTitle), color = AppText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "${selectedDate.year} / ${dayCalls.size} ${if (dayCalls.size == 1) "call" else "calls"} / IST",
                                color = AppMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { showDay = false }) { Text("Done", color = AppText) }
                    }
                }
                uiState.errorMessage?.let { message ->
                    item { CalendarNotice(message, error = true) }
                }
                if (dayCalls.isEmpty()) {
                    item { CalendarNotice("No calls on this day.") }
                }
                items(dayCalls, key = { it.call.id }) { entry ->
                    callContent(entry.call.copy(timeLabel = "${entry.time.format(CallTime)} IST"))
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    byDate: Map<LocalDate, List<CalendarCall>>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
) {
    Surface(color = AppPanel, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    month.format(MonthTitle),
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    color = AppText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                IconButton(onClick = onPrevious, modifier = Modifier.semantics { contentDescription = "Previous month" }) {
                    Text("\u2039", color = AppText, fontSize = 28.sp)
                }
                IconButton(onClick = onNext, modifier = Modifier.semantics { contentDescription = "Next month" }) {
                    Text("\u203a", color = AppText, fontSize = 28.sp)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Text(label, Modifier.weight(1f), color = AppMuted, textAlign = TextAlign.Center, fontSize = 12.sp)
                }
            }
            calendarMonthDays(month).chunked(7).forEach { week ->
                val maxCalls = week.maxOf { byDate[it].orEmpty().size }
                val rowHeight = when (maxCalls) {
                    0 -> 48.dp
                    1 -> 64.dp
                    2 -> 78.dp
                    else -> 90.dp
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { date ->
                        if (date == null) {
                            Box(Modifier.weight(1f))
                        } else {
                            CalendarDay(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                calls = byDate[date].orEmpty(),
                                minHeight = rowHeight,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectDay(date) },
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onToday) { Text("Today", color = AppText) }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    calls: List<CalendarCall>,
    minHeight: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            selected = isSelected
            contentDescription = "${date.format(DayTitle)} ${date.year}${if (isToday) ", today" else ""}, " +
                "${calls.size} calls${if (calls.isEmpty()) "" else ": " + calls.joinToString { it.call.contactName }}"
        },
        color = if (isSelected) AppAccentSoft else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isSelected || isToday) AppAccent else AppLine),
    ) {
        Column(
            Modifier.heightIn(min = minHeight).padding(horizontal = 2.dp, vertical = 6.dp).clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(date.dayOfMonth.toString(), color = AppText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            calls.take(2).forEach { entry ->
                val cancelled = entry.call.status.equals("cancelled", ignoreCase = true)
                val failed = entry.call.status.equals("failed", ignoreCase = true)
                Surface(
                    color = if (failed) AppDangerSoft else if (cancelled) AppPanelAlt else AppAccentSoft,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        entry.call.contactName,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                        color = AppText,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (calls.size > 2) Text("+${calls.size - 2}", color = AppText, fontSize = 10.sp, lineHeight = 12.sp)
            if (isToday && calls.isEmpty()) Box(Modifier.size(4.dp).background(AppAccent, CircleShape))
        }
    }
}

@Composable
private fun CalendarNotice(message: String, error: Boolean = false) {
    Surface(color = if (error) AppDangerSoft else AppPanel, shape = RoundedCornerShape(18.dp)) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            color = if (error) AppDanger else AppMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun CalendarMonthPreview() {
    val date = LocalDate.of(2026, 9, 27)
    val calls = listOf("Ayush", "Mom", "Rahul").mapIndexed { index, name ->
        ScheduledCallUiModel(
            id = "preview-$index", contactName = name, contactNumber = "", timeLabel = "",
            message = "Preview", status = "Pending", canCancel = true,
            scheduledTime = "2026-09-27T${14 + index}:00:00+05:30",
        )
    }
    MADv2Theme {
        MonthGrid(YearMonth.from(date), date, date, callsByCalendarDate(calls), {}, {}, {}, {})
    }
}
