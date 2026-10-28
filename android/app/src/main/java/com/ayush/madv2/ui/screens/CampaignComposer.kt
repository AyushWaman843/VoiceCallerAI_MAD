package com.ayush.madv2.ui.screens

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.ayush.madv2.data.AccountSession
import com.ayush.madv2.R
import com.ayush.madv2.data.CallAiRepository
import com.ayush.madv2.ui.*
import com.ayush.madv2.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampaignComposer(group: BusinessGroup, draft: CampaignDetail?, busy: Boolean, error: String?,
    initialPrompt: String, timezone: String, onDismiss: () -> Unit,
    onPreview: (String, List<String>, String?) -> Unit, onEdit: () -> Unit, onConfirm: () -> Unit,
    chooseRecipientsFirst: Boolean = false, templates: List<MessageTemplate> = emptyList(),
    onLoadTemplates: () -> Unit = {}) {
    val eligible = group.contacts.filter { it.consent_status == "approved" }
    AutoScheduleReadyCall(draft?.id, !busy && error == null, onConfirm)
    var selected by remember(group.id) { mutableStateOf(eligible.map { it.id }.toSet()) }
    var query by remember { mutableStateOf("") }
    var composing by remember(group.id, chooseRecipientsFirst) { mutableStateOf(initialPrompt.isNotBlank() && !chooseRecipientsFirst) }
    var prompt by remember(group.id) { mutableStateOf(initialPrompt) }
    var pickedTemplate by remember(group.id) { mutableStateOf<MessageTemplate?>(null) }
    var showTemplates by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var audioError by remember { mutableStateOf<String?>(null) }
    val player = remember { MediaPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    fun time(value: String) = runCatching { OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of(timezone))
        .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a")) }.getOrDefault(value)
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
            if (MaterialTheme.colorScheme.background.luminance() > .5f)
                Image(painterResource(R.drawable.landing_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .55f)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PocketIconButton(Glyph.Back, "Back", enabled = !busy) {
                        if (draft != null) onEdit() else if (composing) composing = false else onDismiss()
                    }
                    Text(group.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    PocketIconButton(Glyph.Close, "Close", enabled = !busy, onClick = onDismiss)
                }
                if (draft != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ready to call?", style = MaterialTheme.typography.headlineSmall)
                            Text("Review your message and recipients.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Image(painterResource(R.drawable.voxie_landing), null, Modifier.size(60.dp))
                    }
                } else VoxiePageHeader(if (busy) "Preparing your call" else if (composing) "What should Voxie say?" else "Who's included?",
                    if (composing) "Only your selected contacts will be called." else "Everyone eligible is selected. Uncheck anyone to leave them out.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) ThinkingIndicator("Preparing your calls")
                VoxieTransition(draft to composing, Modifier.weight(1f)) { snapshot ->
                val draft = snapshot.first
                val showPrompt = snapshot.second
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    busy && draft == null -> {
                        Spacer(Modifier.weight(1f))
                        Text("Finding your recipients, preparing the message and checking the schedule...",
                            style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                    }
                    draft != null -> {
                        val reviewListState = key(draft.id) { rememberLazyListState() }
                        LazyColumn(Modifier.weight(1f), state = reviewListState,
                            contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("${draft.eligible_count} recipients · ${draft.estimated_credits} test credits",
                                        style = MaterialTheme.typography.titleSmall)
                                    draft.template_name?.let { Text("Template: $it", style = MaterialTheme.typography.bodySmall) }
                                    Text("Timezone: $timezone", style = MaterialTheme.typography.bodySmall)
                                    draft.estimated_provider_cost_paise?.let {
                                        Text("Estimated provider cost: ₹${it / 100.0}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            item { Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("MESSAGE", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(draft.message, style = MaterialTheme.typography.bodyLarge)
                                }
                            } }
                            item {
                                OutlinedButton(onClick = hapticClick {
                                    if (playing) { player.pause(); playing = false }
                                    else runCatching {
                                        audioError = null; player.reset()
                                        player.setOnPreparedListener { it.start(); playing = true }
                                        player.setOnCompletionListener { playing = false }
                                        player.setOnErrorListener { _, _, _ -> playing = false; audioError = "Audio could not be loaded."; true }
                                        player.setDataSource(draft.audio_url); player.prepareAsync()
                                    }.onFailure { audioError = "Audio could not be loaded." }
                                }, shape = CircleShape) { Text(if (playing) "Pause preview" else "Play audio preview") }
                                audioError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                            items(draft.recipients, key = { it.id }) { contact ->
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text(contact.name, style = MaterialTheme.typography.titleSmall)
                                        group.contacts.find { it.id == contact.id }?.group_name?.let {
                                            Text(it, style = MaterialTheme.typography.labelMedium)
                                        }
                                        Text(contact.number, style = MaterialTheme.typography.bodySmall)
                                        Text(time(contact.scheduled_time ?: draft.scheduled_time), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        ConfirmCallButton("Confirm & schedule", !busy, onConfirm)
                        TextButton(onClick = { player.pause(); playing = false; onEdit() }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth()) { Text("Edit instruction or recipients") }
                    }
                    !showPrompt -> {
                        OutlinedTextField(query, { query = it }, placeholder = { Text("Search contacts") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${eligible.count { it.id in selected }} of ${eligible.size} selected", Modifier.weight(1f))
                            TextButton(onClick = { selected = if (eligible.all { it.id in selected }) emptySet() else eligible.map { it.id }.toSet() }) {
                                Text(if (eligible.all { it.id in selected }) "Deselect all" else "Select all")
                            }
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(eligible.filter { it.name.contains(query, true) || it.number.contains(query) }, key = { it.id }) { contact ->
                                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(contact.id in selected, { checked -> selected = if (checked) selected + contact.id else selected - contact.id })
                                        Column {
                                            Text(contact.name)
                                            contact.group_name?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                                            Text(contact.number, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                        Button(onClick = hapticClick { composing = true }, enabled = selected.isNotEmpty() && !busy,
                            shape = CircleShape, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Continue with selected contacts") }
                    }
                    else -> {
                        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                            Text("Call ${group.name} · ${eligible.count { it.id in selected }} selected recipients", Modifier.padding(16.dp))
                        }
                        Text("For example: Tomorrow at 3pm, remind them about Monday's exam. Call each person every 5 minutes.",
                            style = MaterialTheme.typography.bodyMedium)
                        pickedTemplate?.let { template ->
                            TextButton(onClick = { pickedTemplate = null }) { Text("Template: ${template.name}  x") }
                        }
                        Spacer(Modifier.weight(1f))
                        GroupVoicePrompt(prompt, { prompt = it }, busy, onAdd = {
                            showTemplates = true; onLoadTemplates()
                        }) {
                            onPreview("Call group ${group.name}. $prompt", eligible.filter { it.id in selected }.map { it.id }, pickedTemplate?.id)
                        }
                        TextButton(onClick = { composing = false }, enabled = !busy) { Text("Change recipients") }
                    }
                }
                }
                }
            }
            }
        }
    }
    if (showTemplates) ModalBottomSheet(onDismissRequest = { showTemplates = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add a saved template", style = MaterialTheme.typography.titleLarge)
            templates.forEach { template ->
                OutlinedButton(onClick = { pickedTemplate = template; showTemplates = false },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text(template.name) }
            }
            if (templates.isEmpty()) Text("No saved templates yet.")
        }
    }
}

@Composable
private fun GroupVoicePrompt(prompt: String, onPrompt: (String) -> Unit, busy: Boolean,
    onAdd: () -> Unit, onSend: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capture = rememberVoiceCapture {}
    var transcribing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentPrompt by rememberUpdatedState(prompt)
    val focus = remember { FocusRequester() }
    fun start() { runCatching { capture.start() }.onFailure { error = "Could not start the microphone." } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) start() else error = "Allow microphone access to dictate your instruction."
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    PocketComposer(prompt, onPrompt, capture.recording, transcribing, busy || transcribing, capture.samples, focus,
        onPlus = onAdd, onMic = {
            error = null
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }, onCancel = { capture.cancel() }, onFinish = {
            val file = capture.finish()
            if (file == null) error = "Recording was too short. Please try again."
            else { transcribing = true; scope.launch {
                try {
                    val account = AccountSession.account.value ?: error("Sign in again.")
                    val result = CallAiRepository().transcribeAudio(account.backendUrl, file)
                    onPrompt(listOf(currentPrompt, result.text).filter { it.isNotBlank() }.joinToString(" "))
                } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message }
                finally { transcribing = false; file.delete() }
            } }
        }, onSend = onSend, homeStyle = true)
}
