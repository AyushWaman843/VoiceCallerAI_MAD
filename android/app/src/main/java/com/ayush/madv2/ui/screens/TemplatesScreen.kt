package com.ayush.madv2.ui.screens

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ayush.madv2.ui.BusinessViewModel
import com.ayush.madv2.ui.MessageTemplate
import com.ayush.madv2.ui.components.*

@Composable
fun TemplatesScreen(model: BusinessViewModel, onUse: (MessageTemplate) -> Unit) {
    val state by model.state.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<MessageTemplate?>(null) }
    var deleting by remember { mutableStateOf<MessageTemplate?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { model.loadTemplates() }
    LaunchedEffect(state.notice) { if (state.notice == "Template saved.") editing = false }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { VoxiePageHeader("Your templates", "Save a message once. Ask Voxie to use it for any call.") }
        item {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = model::loadTemplates, enabled = !state.busy) { Text("Retry") }
            }
            Button(onClick = hapticClick { current = null; editing = true }, shape = CircleShape,
                enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("+  Create a template") }
        }
        item { OutlinedTextField(query, { query = it }, placeholder = { Text("Search templates") },
            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(), singleLine = true) }
        if (state.templates.isEmpty() && !state.busy) item { Text("No templates yet. Add reminders, announcements or your everyday messages.") }
        items(state.templates.filter { it.name.contains(query, true) || it.message.contains(query, true) }, key = { it.id }) { template ->
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(template.name, style = MaterialTheme.typography.titleMedium)
                    Text(template.message)
                    Text("Say: Use \"${template.name}\" template", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = hapticClick { onUse(template) }, shape = CircleShape) { Text("Use") }
                        TextButton(onClick = { current = template; editing = true }, enabled = !state.busy) { Text("Edit") }
                        TextButton(onClick = { deleting = template }, enabled = !state.busy) { Text("Delete") }
                    }
                }
            }
        }
    }
    if (editing) {
        var name by remember(current?.id) { mutableStateOf(current?.name.orEmpty()) }
        var message by remember(current?.id) { mutableStateOf(current?.message.orEmpty()) }
        AlertDialog(onDismissRequest = { if (!state.busy) editing = false }, title = { Text(if (current == null) "New template" else "Edit template") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(name, { if (it.length <= 120) name = it }, label = { Text("Template name") }, singleLine = true)
                OutlinedTextField(message, { if (it.length <= 2000) message = it }, label = { Text("Message to speak") }, minLines = 3, maxLines = 8)
                Text("Save only the message. Choose recipients and time when you make a call.", style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { Button(onClick = { model.saveTemplate(current?.id, name, message) },
                enabled = !state.busy && name.isNotBlank() && message.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }, enabled = !state.busy) { Text("Cancel") } })
    }
    deleting?.let { template -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${template.name}?") },
        text = { Text("Already scheduled calls will keep their original message.") },
        confirmButton = { TextButton(onClick = { model.deleteTemplate(template.id); deleting = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }) }
}
