package com.ayush.madv2.ui.screens

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayush.madv2.ui.*
import com.ayush.madv2.ui.components.*
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun BusinessScreen(model: BusinessViewModel, timezone: String = "Asia/Kolkata",
                   initialGroupId: String? = null, initialPrompt: String = "",
                   onInitialConsumed: () -> Unit = {}, onIndividual: (BusinessContact) -> Unit,
                   onSheet: (String) -> Unit) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    var groupName by remember { mutableStateOf("") }
    var creatingGroup by remember { mutableStateOf(false) }
    var deletingGroup by remember { mutableStateOf<BusinessGroup?>(null) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var editingGroup by remember { mutableStateOf<BusinessGroup?>(null) }
    var editingContact by remember { mutableStateOf<BusinessContact?>(null) }
    var adding by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var campaigning by remember { mutableStateOf(false) }
    var campaignPrompt by remember { mutableStateOf("") }
    var review by remember { mutableStateOf(false) }
    LaunchedEffect(state.import, state.draft, state.notice) {
        if (review && state.import == null && state.notice == "Contacts saved.") review = false
        if (campaigning && state.draft == null && state.notice == "Campaign scheduled.") campaigning = false
    }
    val chosen = state.groups.find { it.id == selectedGroup }
    LaunchedEffect(Unit) { model.refresh() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            model.refreshCampaigns()
        }
    }
    LaunchedEffect(initialGroupId) {
        if (initialGroupId != null) {
            selectedGroup = initialGroupId
            campaignPrompt = initialPrompt
            campaigning = true
            onInitialConsumed()
        }
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && chosen != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) { model.csv(chosen.name, bytes); review = true }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { VoxiePageHeader("Your groups", "The right people, all in one place.") }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.notice?.let { item { Text(it, color = MaterialTheme.colorScheme.secondary) } }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item {
            Button(onClick = hapticClick { groupName = ""; creatingGroup = true }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) {
                PocketIcon(Glyph.Plus, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text("Create a group", style = MaterialTheme.typography.labelLarge)
            }
        }
        item {
            Text("Saved groups${if (state.groups.isEmpty()) "" else "  ·  ${state.groups.size}"}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (state.groups.isEmpty()) item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PocketIcon(Glyph.Groups, color = MaterialTheme.colorScheme.secondary)
                    Text("Keep your contacts together", fontWeight = FontWeight.SemiBold)
                    Text("Create a group to add people and schedule calls for them.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(state.groups, key = { it.id }) { group ->
            val expanded = selectedGroup == group.id
            val eligible = group.contacts.count { it.consent_status == "approved" }
            var menuOpen by remember(group.id) { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clickable(onClick = hapticClick {
                            selectedGroup = if (expanded) null else group.id
                        }).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = .10f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                PocketIcon(Glyph.Groups, color = MaterialTheme.colorScheme.secondary)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${group.contacts.size} contacts · $eligible ready to call",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PocketIcon(if (expanded) Glyph.Close else Glyph.Next,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            TextButton(onClick = hapticClick { menuOpen = true },
                                modifier = Modifier.semantics { contentDescription = "Options for ${group.name}" },
                                contentPadding = PaddingValues(horizontal = 10.dp)) { Text("⋮", fontSize = 23.sp) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Rename group") }, onClick = hapticClick {
                                    menuOpen = false; editingGroup = group
                                })
                                DropdownMenuItem(text = { Text("Delete group", color = MaterialTheme.colorScheme.error) },
                                    onClick = hapticClick { menuOpen = false; deletingGroup = group })
                            }
                        }
                    }
                    if (expanded) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Button(onClick = hapticClick { campaignPrompt = ""; campaigning = true },
                            enabled = eligible > 0 && !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = CircleShape) {
                            Text("Schedule group call")
                        }
                        if (eligible == 0) Text("Add an approved contact to call this group.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = hapticClick { adding = true }, modifier = Modifier.weight(1f),
                                shape = CircleShape) { Text("Add contact", maxLines = 1) }
                            OutlinedButton(onClick = hapticClick { importing = true }, modifier = Modifier.weight(1f),
                                shape = CircleShape) { Text("Import", maxLines = 1) }
                        }
                        if (group.contacts.isNotEmpty()) {
                            Text("People", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            group.contacts.forEach { contact ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                                        contentAlignment = Alignment.Center) {
                                        Text(contact.name.take(1).uppercase(), fontWeight = FontWeight.SemiBold)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(contact.number, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = hapticClick { onIndividual(contact) },
                                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Call") }
                                    TextButton(onClick = hapticClick { editingContact = contact },
                                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Edit") }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Text("Campaigns", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (state.campaigns.isEmpty()) item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()) {
                Text("Scheduled group calls will appear here.", modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(state.campaigns, key = { it.id }) { campaign ->
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(campaign.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondary.copy(alpha = .10f)) {
                            Text(campaign.status.replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Text("${runCatching { java.time.OffsetDateTime.parse(campaign.scheduled_time)
                        .atZoneSameInstant(java.time.ZoneId.of(timezone)).format(DateTimeFormatter.ofPattern("d MMM, h:mm a z"))
                    }.getOrDefault(campaign.scheduled_time)} · ${campaign.total} calls",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (campaign.results.isNotEmpty()) Text(campaign.results.entries.joinToString { "${it.key}: ${it.value}" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (campaign.status == "scheduled") TextButton(onClick = hapticClick { model.cancel(campaign.id) },
                        modifier = Modifier.align(Alignment.End)) { Text("Cancel campaign") }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
    if (creatingGroup) AlertDialog(onDismissRequest = { creatingGroup = false },
        title = { Text("Create a group") },
        text = { OutlinedTextField(groupName, { groupName = it }, label = { Text("Group name") },
            singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = hapticClick {
            model.createGroup(groupName.trim()); creatingGroup = false; groupName = ""
        }, enabled = groupName.isNotBlank() && !state.busy) { Text("Create") } },
        dismissButton = { TextButton(onClick = { creatingGroup = false }) { Text("Cancel") } })
    deletingGroup?.let { group -> AlertDialog(onDismissRequest = { deletingGroup = null },
        title = { Text("Delete ${group.name}?") },
        text = { Text("This removes the group and its saved contacts.") },
        confirmButton = { TextButton(onClick = hapticClick {
            model.delete(group.id); if (selectedGroup == group.id) selectedGroup = null; deletingGroup = null
        }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deletingGroup = null }) { Text("Keep group") } }) }
    editingGroup?.let { group ->
        var name by remember(group.id) { mutableStateOf(group.name) }
        AlertDialog(onDismissRequest = { editingGroup = null }, title = { Text("Rename group") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = hapticClick { model.rename(group.id, name); editingGroup = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingGroup = null }) { Text("Cancel") } })
    }
    if (adding && chosen != null) ContactEditor(null, onDismiss = { adding = false },
        onSave = { name, number, consent -> model.add(chosen.id, name, number, consent); adding = false })
    editingContact?.let { contact -> if (chosen != null) ContactEditor(contact, onDismiss = { editingContact = null },
        onSave = { name, number, consent -> model.edit(chosen.id, contact.id, name, number, consent); editingContact = null },
        onOptOut = { model.optOut(chosen.id, contact.id); editingContact = null },
        onRemove = { model.remove(chosen.id, contact.id); editingContact = null }) }
    if (importing && chosen != null) AlertDialog(onDismissRequest = { importing = false },
        title = { Text("Import into ${chosen.name}") },
        text = { Text("Choose a CSV file or connect a Google Sheet. You will review all rows before saving.") },
        confirmButton = { TextButton(onClick = hapticClick { importing = false; csvPicker.launch("text/*") }) { Text("CSV file") } },
        dismissButton = { TextButton(onClick = hapticClick { importing = false; onSheet(chosen.name); review = true }) { Text("Google Sheet") } })
    if (review && state.import != null) ImportReview(state.import!!, state.error, state.busy,
        onDismiss = { review = false }, onColumns = model::chooseColumns,
        onApprove = { rows, consent -> model.approve(rows, consent) })
    if (campaigning && chosen != null) CampaignComposer(chosen, state.draft, state.busy, state.error, campaignPrompt, timezone,
        onDismiss = { campaigning = false; model.clearDraft() },
        onPreview = { prompt, recipients, templateId -> model.preview(chosen.id, prompt, recipients, templateId) },
        onEdit = model::clearDraft,
        onConfirm = model::schedule, templates = state.templates, onLoadTemplates = model::loadTemplates)
}

@Composable
private fun ContactEditor(contact: BusinessContact?, onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit, onOptOut: (() -> Unit)? = null, onRemove: (() -> Unit)? = null) {
    var name by remember(contact?.id) { mutableStateOf(contact?.name.orEmpty()) }
    var number by remember(contact?.id) { mutableStateOf(contact?.number.orEmpty()) }
    var consent by remember(contact?.id) { mutableStateOf(contact?.consent_status == "approved") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (contact == null) "Add contact" else "Edit contact") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            OutlinedTextField(number, { number = it }, label = { Text("Indian mobile number") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(consent, { consent = it })
                Text("Consent for automated calls confirmed", style = MaterialTheme.typography.bodySmall)
            }
            if (contact != null) { onOptOut?.let { TextButton(onClick = hapticClick(it)) { Text("Opt out") } }
                onRemove?.let { TextButton(onClick = hapticClick(it)) { Text("Remove from group") } } }
        } },
        confirmButton = { TextButton(onClick = hapticClick { onSave(name, number, consent) }, enabled = name.isNotBlank() && number.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ImportReview(result: ImportResult, error: String?, busy: Boolean, onDismiss: () -> Unit,
    onColumns: (Int, Int) -> Unit, onApprove: (List<Map<String, Any>>, Boolean) -> Unit) {
    var nameColumn by remember(result.import_id) { mutableIntStateOf(0) }
    var phoneColumn by remember(result.import_id) { mutableIntStateOf(1) }
    val selected = remember(result.import_id) { mutableStateMapOf<Int, Boolean>() }
    val names = remember(result.import_id) { mutableStateMapOf<Int, String>() }
    val numbers = remember(result.import_id) { mutableStateMapOf<Int, String>() }
    var consent by remember(result.import_id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (result.status == "needs_columns") "Select columns" else "Review ${result.group_name}") },
        text = { Column(Modifier.heightIn(max = 470.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (result.status == "needs_columns") {
                Text("Choose the recipient name and mobile number columns.")
                result.headers.forEachIndexed { index, header ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(header, Modifier.weight(1f))
                        RadioButton(nameColumn == index, { nameColumn = index })
                        RadioButton(phoneColumn == index, { phoneColumn = index })
                    }
                }
                Text("Name                         Phone", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("${result.found} found · ${result.duplicates} duplicates · ${result.invalid} invalid")
                Text("Review numbers and remove anyone without consent.", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(result.contacts, key = { it.row }) { row ->
                        val checked = selected[row.row] ?: (row.status == "valid")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, { selected[row.row] = it })
                            Column(Modifier.weight(1f)) {
                                OutlinedTextField(names[row.row] ?: row.name, { names[row.row] = it }, label = { Text("Name") }, singleLine = true)
                                OutlinedTextField(numbers[row.row] ?: (row.phone.ifBlank { row.raw_number.orEmpty() }),
                                    { numbers[row.row] = it }, label = { Text("Number · ${row.status}") }, singleLine = true)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(consent, { consent = it }); Text("I confirm consent to automated calls", style = MaterialTheme.typography.bodySmall)
                }
            }
        } },
        confirmButton = { TextButton(onClick = hapticClick {
            if (result.status == "needs_columns") onColumns(nameColumn, phoneColumn)
            else onApprove(result.contacts.filter { selected[it.row] ?: (it.status == "valid") }.map {
                mapOf("row" to it.row, "name" to (names[it.row] ?: it.name),
                    "number" to (numbers[it.row] ?: it.phone)) }, consent)
        }, enabled = !busy && (result.status == "needs_columns" || result.contacts.isNotEmpty())) { Text(if (result.status == "needs_columns") "Continue" else "Save reviewed contacts") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

