package com.ayush.madv2.ui

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ayush.madv2.ui.components.*
import com.ayush.madv2.ui.theme.PocketMotion
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CallCalendar(
    uiState: CallAiUiState, modifier: Modifier = Modifier,
    onSchedule: (LocalDate) -> Unit = {},
    callContent: @Composable (ScheduledCallUiModel) -> Unit,
) {
    val today = LocalDate.now(CallCalendarZone)
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var dayText by rememberSaveable { mutableStateOf(today.toString()) }
    val month = YearMonth.parse(monthText)
    val selectedDay = LocalDate.parse(dayText)
    val all = remember(uiState.upcomingCalls, uiState.historyCalls) {
        (uiState.upcomingCalls + uiState.historyCalls).distinctBy { it.id }
    }
    val byDate = remember(all) { callsByCalendarDate(all) }
    val undated = remember(all) { all.filter { calendarCall(it) == null } }
    val dayCalls = byDate[selectedDay].orEmpty()
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH) }
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH) }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { VoxiePageHeader("A little planning,\na lot of care.", "Pick a day to see calls or schedule a new one.") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PocketIconButton(Glyph.Back, "Previous month") { monthText = month.minusMonths(1).toString() }
                Text(month.format(monthFormat), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                PocketIconButton(Glyph.Next, "Next month") { monthText = month.plusMonths(1).toString() }
            }
        }
        item {
            AnimatedContent(month, transitionSpec = {
                androidx.compose.animation.fadeIn(tween(PocketMotion.Standard)) togetherWith
                    androidx.compose.animation.fadeOut(tween(PocketMotion.Fast))
            }, label = "Calendar month") { visibleMonth ->
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                            Text(it, Modifier.weight(1f).padding(vertical = 12.dp), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    calendarMonthDays(visibleMonth).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                if (date == null) Spacer(Modifier.weight(1f).height(52.dp))
                                else {
                                    val calls = byDate[date].orEmpty()
                                    Column(
                                        Modifier.weight(1f).heightIn(min = 52.dp)
                                            .clickable(onClick = hapticClick { dayText = date.toString() })
                                            .semantics {
                                                selected = date == selectedDay
                                                contentDescription = "${date.format(dayFormat)} ${date.year}${if (date == today) ", today" else ""}, " +
                                                    "${calls.size} calls${if (calls.isEmpty()) "" else ": " + calls.joinToString { it.call.contactName }}"
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        val background = when(date) {
                                            selectedDay -> MaterialTheme.colorScheme.primary
                                            today -> MaterialTheme.colorScheme.surfaceContainerHigh
                                            else -> androidx.compose.ui.graphics.Color.Transparent
                                        }
                                        Box(Modifier.size(34.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
                                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
                                                color = if (date == selectedDay) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                        }
                                        Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            repeat(calls.size.coerceAtMost(3)) {
                                                Box(Modifier.size(3.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("All times in IST", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = hapticClick { monthText = YearMonth.from(today).toString(); dayText = today.toString() }) { Text("Today") }
            }
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        item { Text(selectedDay.format(dayFormat), style = MaterialTheme.typography.titleMedium) }
        if (!selectedDay.isBefore(today)) item {
            Button(onClick = hapticClick { onSchedule(selectedDay) }, modifier = Modifier.fillMaxWidth()) {
                PocketIcon(Glyph.Plus)
                Spacer(Modifier.width(8.dp))
                Text("Schedule a call for this day")
            }
        }
        if (uiState.isRefreshingCalls) item { ThinkingIndicator("Refreshing calls") }
        uiState.errorMessage?.let { item { QuietNotice(it) } }
        if (dayCalls.isEmpty()) item { QuietNotice("No calls on this day.") }
        items(dayCalls, key = { it.call.id }) { callContent(it.call) }
        if (undated.isNotEmpty()) {
            item { SectionLabel("DATE UNAVAILABLE") }
            items(undated, key = { it.id }) { callContent(it) }
        }
    }
}
