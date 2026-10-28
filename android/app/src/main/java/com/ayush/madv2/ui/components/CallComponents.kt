package com.ayush.madv2.ui.components

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ayush.madv2.ui.ScheduledCallUiModel

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PocketIcon(Glyph.Search)
            BasicTextField(value, onChange, singleLine = true, modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner -> Box {
                    if (value.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                } })
        }
    }
}

@Composable
fun MenuRow(title: String, glyph: Glyph, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = hapticClick(onClick), enabled = enabled, color = androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PocketIcon(glyph)
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
}

@Composable
fun QuietNotice(text: String) {
    Text(text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun DrawerCallRow(call: ScheduledCallUiModel, onClick: () -> Unit) {
    Surface(onClick = hapticClick(onClick), color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(call.contactName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(call.timeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            PocketIcon(Glyph.Next, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CallListRow(call: ScheduledCallUiModel, onClick: () -> Unit) {
    Surface(onClick = hapticClick(onClick), shape = RoundedCornerShape(24.dp), shadowElevation = 1.dp) {
    Column(Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(call.contactName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondary.copy(alpha = .1f)) {
                Text(if (call.status == "pending") "Scheduled" else call.status.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Text(call.timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(call.message, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailSheet(
    call: ScheduledCallUiModel, onDismiss: () -> Unit, onCancel: () -> Unit,
    onCallAgain: () -> Unit, canStartNew: Boolean,
) {
    var confirmingCancel by remember(call.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VoxiePageHeader(if (confirmingCancel) "A change of plans?" else "Your call", "Every detail, in one place.")
            Text(if (confirmingCancel) "Cancel scheduled call?" else call.contactName,
                style = MaterialTheme.typography.titleLarge)
            if (confirmingCancel) Text(call.contactName, style = MaterialTheme.typography.titleMedium)
            Text(call.contactNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(call.timeLabel)
            if (confirmingCancel) {
                Text("This call will not be placed. You can schedule a new one later.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = hapticClick { confirmingCancel = false }) { Text("Keep call") }
                    Button(onClick = hapticClick(onCancel), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Cancel call")
                    }
                }
            } else {
                Text(call.status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Message", style = MaterialTheme.typography.labelLarge)
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .5f)) {
                    Text(call.message, modifier = Modifier.fillMaxWidth().padding(18.dp), style = MaterialTheme.typography.bodyLarge)
                }
                Text("Voice: configured account voice", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (call.canCancel) OutlinedButton(onClick = hapticClick { confirmingCancel = true }) { Text("Cancel scheduled call") }
                else Button(onClick = hapticClick(onCallAgain), enabled = canStartNew) { Text("Call again") }
            }
        }
    }
}
