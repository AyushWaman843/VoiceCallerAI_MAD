package com.ayush.madv2.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

data class CallConfirmationPreference(val required: Boolean = true, val update: (Boolean) -> Unit = {})
val LocalCallConfirmation = compositionLocalOf { CallConfirmationPreference() }

@Composable
fun AutoScheduleReadyCall(key: String?, ready: Boolean, onSchedule: () -> Unit) {
    val preference = LocalCallConfirmation.current
    // Snapshot the preference for this draft: checking the box must not submit this call twice.
    val automatic = rememberSaveable(key) { !preference.required }
    var attempted by rememberSaveable(key) { mutableStateOf(false) }
    val schedule by rememberUpdatedState(onSchedule)
    LaunchedEffect(key, ready, preference.required) {
        if (key != null && ready && automatic && !preference.required && !attempted) {
            attempted = true // A failed request needs an explicit retry, never an automatic retry loop.
            schedule()
        }
    }
}

@Composable
fun ConfirmCallButton(label: String, enabled: Boolean, onConfirm: () -> Unit) {
    val preference = LocalCallConfirmation.current
    var skipNext by rememberSaveable { mutableStateOf(!preference.required) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(skipNext, { skipNext = it }, enabled = enabled)
                Spacer(Modifier.width(4.dp))
                Text("Skip confirmation next time", style = MaterialTheme.typography.bodyMedium)
            }
        }
        VoxieButton(onClick = {
            preference.update(!skipNext)
            onConfirm()
        }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(label) }
    }
}

@Composable
fun CallConfirmationSetting() {
    val preference = LocalCallConfirmation.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Confirm before scheduling")
            Text(if (preference.required) "Review each call before it is scheduled."
                else "Complete requests schedule automatically, including group calls.",
                style = MaterialTheme.typography.bodySmall)
        }
        Switch(preference.required, preference.update)
    }
}
