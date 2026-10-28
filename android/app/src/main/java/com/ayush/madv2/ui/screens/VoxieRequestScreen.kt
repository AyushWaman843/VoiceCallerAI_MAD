package com.ayush.madv2.ui.screens

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.media.MediaPlayer
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ayush.madv2.ui.*
import com.ayush.madv2.ui.components.*
import com.ayush.madv2.ui.theme.PocketMotion
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VoxieRequestScreen(
    state: CallAiUiState, samples: List<Float>, onPrompt: (String) -> Unit,
    onSubmit: () -> Unit, onConfirm: () -> Unit, onPreviewAudio: () -> Unit = {}, onEdit: () -> Unit,
    onField: (String, String) -> Unit, onPlus: () -> Unit, onMic: () -> Unit,
    onCancelRecording: () -> Unit, onFinishRecording: () -> Unit,
    onSearchContacts: () -> Unit, onViewCall: (String) -> Unit, onDismissError: () -> Unit,
    mode: String = "personal", timezone: String = "Asia/Kolkata",
    focusComposer: Boolean = false,
    templateName: String? = null, onClearTemplate: () -> Unit = {},
    groupName: String? = null, onClearGroup: () -> Unit = {},
    onNewChat: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusComposer) { if (focusComposer) focusRequester.requestFocus() }
    AutoScheduleReadyCall(state.confirmationDraft?.confirmationKey,
        !state.isProcessing && !state.isRecording && state.errorMessage == null, onConfirm)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    LaunchedEffect(state.conversation.size, state.confirmationDraft, state.missingFieldsDraft, state.isProcessing) {
        if (state.conversation.isNotEmpty()) {
            delay(60)
            listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = hapticClick(onNewChat),
                enabled = !state.isProcessing && !state.isRecording && !state.isTranscribing,
                shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PocketIcon(Glyph.Plus, "Start new chat", MaterialTheme.colorScheme.secondary)
                    Text("New chat", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val showSuggestions = maxHeight >= 280.dp
            if (state.conversation.isEmpty() && !state.isProcessing) {
                Column(Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { focusManager.clearFocus(); keyboard?.hide() }
                }.padding(24.dp), verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start) {
                    Text("Tell Voxie who to call, what to say, and when.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("For example: Wish Dad a happy birthday tomorrow at 9 AM.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (showSuggestions) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = hapticClick(onSearchContacts)) { PocketIcon(Glyph.Contacts); Spacer(Modifier.width(8.dp)); Text("Choose someone from contacts") }
                        TextButton(onClick = hapticClick(onPlus)) { PocketIcon(Glyph.Templates); Spacer(Modifier.width(8.dp)); Text("Choose a message template") }
                    }
                }
            }
            if (state.conversation.isNotEmpty() || state.isProcessing || state.confirmationDraft != null || state.missingFieldsDraft != null)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(state.conversation, key = { it.id }) { entry ->
                    var entered by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { entered = true }
                    androidx.compose.animation.AnimatedVisibility(entered,
                        enter = fadeIn(tween(PocketMotion.Standard)) + slideInVertically(tween(PocketMotion.Standard)) { 24 }) {
                        if (entry.fromUser) {
                            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Surface(shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("YOUR REQUEST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                        Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (entry.callId != null) PocketIcon(Glyph.Check, "Call scheduled", MaterialTheme.colorScheme.secondary)
                                Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                                entry.callId?.let { id -> TextButton(onClick = hapticClick { onViewCall(id) }) { Text("View scheduled call") } }
                            }
                            }
                        }
                    }
                }
                if (state.isProcessing && !state.isTranscribing && state.confirmationDraft == null) item("thinking") {
                    ThinkingIndicator(state.processingMessage ?: if (state.confirmationDraft != null) "Scheduling your call" else "Preparing your call")
                }
                state.missingFieldsDraft?.let { draft ->
                    item("missing") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Add what's missing below, or reply in your own words.", style = MaterialTheme.typography.bodyMedium)
                            if (draft.contactName.isNotBlank()) Text(draft.contactName, fontWeight = FontWeight.SemiBold)
                            draft.fields.forEach { field ->
                                val value = when(field) {
                                    "contact_name" -> draft.contactName
                                    "contact_number" -> draft.contactNumber
                                    "message" -> draft.message
                                    else -> draft.scheduledTime
                                }
                                if (field == "scheduled_time") DateTimeRefinement(value, !state.isProcessing, timezone) { onField(field, it) }
                                else OutlinedTextField(value = value, onValueChange = { onField(field,it) },
                                    label = { Text(when(field) {
                                        "contact_name" -> "Who should I call?"
                                        "contact_number" -> "Phone number"
                                        else -> "What should I say?"
                                    }) }, enabled = !state.isProcessing, modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp))
                            }
                            if (draft.fields.any { it.startsWith("contact_") }) TextButton(onClick = hapticClick(onSearchContacts),
                                enabled = !state.isProcessing) { Text("Choose from phone contacts") }
                            Button(onClick = hapticClick(onSubmit), enabled = !state.isProcessing,
                                modifier = Modifier.fillMaxWidth()) { Text("Continue with these details") }
                        }
                    }
                }
                state.confirmationDraft?.let { draft ->
                    item("confirmation") { CallActionCard(draft, state.isProcessing, onConfirm, onPreviewAudio, onEdit, timezone) }
                }
                if (state.conversation.isNotEmpty()) state.errorMessage?.let { error ->
                    item("error") { InlineError(error, !state.isProcessing,
                        onRetry = if (state.confirmationDraft != null) onConfirm else onSubmit, onDismissError) }
                }
            }
        }
        if (state.conversation.isEmpty()) state.errorMessage?.let {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f))
                PocketIconButton(Glyph.Close, "Dismiss error", onClick = onDismissError)
            }
        }
        groupName?.let { name ->
            Surface(modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.height(32.dp).padding(start = 10.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    PocketIcon(Glyph.Groups, "Selected group", MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(6.dp))
                    Text(name, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                    IconButton(onClick = hapticClick(onClearGroup), modifier = Modifier.size(32.dp)) {
                        PocketIcon(Glyph.Close, "Remove group", MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        templateName?.let { name ->
            Surface(modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.height(32.dp).padding(start = 10.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    PocketIcon(Glyph.Templates, "Selected template", MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(6.dp))
                    Text(name, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                    IconButton(onClick = hapticClick(onClearTemplate), modifier = Modifier.size(32.dp)) {
                        PocketIcon(Glyph.Close, "Remove template", MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        PocketComposer(state.prompt, onPrompt, state.isRecording, state.isTranscribing,
            state.isProcessing, samples, focusRequester, onPlus, onMic, onCancelRecording, onFinishRecording, onSubmit,
            homeStyle = true, attachmentLabel = if (mode == "business") "Add group or template" else "Add template")
    }
}

@Composable
private fun CallActionCard(draft: ConfirmationDraft, busy: Boolean, onConfirm: () -> Unit,
                           onPreviewAudio: () -> Unit, onEdit: () -> Unit, timezone: String) {
    var playing by remember { mutableStateOf(false) }
    var playError by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp,
            contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("REVIEW YOUR CALL", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(draft.contactName, style = MaterialTheme.typography.titleMedium)
                    Text(draft.contactNumber, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PocketIcon(Glyph.Clock)
                    Text(formatInZone(draft.scheduledTime, timezone), style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Message", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(draft.messagePreview, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedButton(onClick = hapticClick {
                    val url = draft.audioPreviewUrl
                    if (url == null) onPreviewAudio()
                    else runCatching {
                        player.reset(); player.setDataSource(url)
                        player.setOnPreparedListener { it.start(); playing = true }
                        player.setOnCompletionListener { playing = false }
                        player.prepareAsync()
                    }.onFailure { playError = true }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (draft.audioPreviewUrl == null) "Generate audio preview" else if (playing) "Replay audio" else "Play audio preview")
                }
                if (playError) Text("Audio could not be played. Check the public backend URL.", color = MaterialTheme.colorScheme.error)
                ConfirmCallButton("Schedule call", !busy, onConfirm)
                TextButton(onClick = hapticClick(onEdit), enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Edit details") }
            }
        }
    }
}

@Composable
fun InlineError(message: String, enabled: Boolean, onRetry: () -> Unit, onDismiss: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Row {
            TextButton(onClick = hapticClick(onRetry), enabled = enabled) { Text("Try again") }
            TextButton(onClick = hapticClick(onDismiss)) { Text("Dismiss") }
        }
    }
}

private fun formatInZone(value: String?, timezone: String): String = runCatching {
    java.time.OffsetDateTime.parse(value.orEmpty()).atZoneSameInstant(ZoneId.of(timezone))
        .format(DateTimeFormatter.ofPattern("EEE, d MMM 'at' h:mm a z", java.util.Locale.ENGLISH))
}.getOrElse { formatCallTime(value) }

@Composable
private fun DateTimeRefinement(value: String, enabled: Boolean, timezone: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = hapticClick {
        val zone = ZoneId.of(timezone)
        val initial = runCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone) }
            .getOrDefault(ZonedDateTime.now(zone).plusMinutes(10))
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                onChange(LocalDate.of(year, month+1, day).atTime(hour,minute)
                    .atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            }, initial.hour, initial.minute, false).show()
        }, initial.year, initial.monthValue-1, initial.dayOfMonth).show()
    }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(if (value.isBlank()) "Choose date and time ($timezone)" else formatInZone(value, timezone))
    }
}
