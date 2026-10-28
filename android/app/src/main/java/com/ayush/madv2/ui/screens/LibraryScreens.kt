package com.ayush.madv2.ui.screens

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.Crossfade
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ayush.madv2.ui.*
import com.ayush.madv2.ui.components.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CallsScreen(
    calls: List<ScheduledCallUiModel>, loading: Boolean, error: String?,
    history: Boolean = false, onCall: (ScheduledCallUiModel) -> Unit, onRefresh: () -> Unit,
    onHistory: ((Boolean) -> Unit)? = null,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(calls, search, history) {
        val selected = calls.filter { it.contactName.contains(search, true) || it.message.contains(search, true) }
        if (history) selected.sortedByDescending { calendarCall(it)?.time?.toInstant() }
        else selected.sortedBy { calendarCall(it)?.time?.toInstant() }
    }
    val today = LocalDate.now(CallCalendarZone)
    val groups = filtered.groupBy { calendarCall(it)?.time?.toLocalDate() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        VoxiePageHeader(if (history) "Little moments,\nbig connections." else "Your calls,\nall lined up.",
            if (history) "Look back at the people you've reached." else "Voxie will take it from here.")
        onHistory?.let { choose ->
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape).padding(4.dp)) {
                listOf("Upcoming", "Recent").forEachIndexed { index, label ->
                    Surface(onClick = hapticClick { choose(index == 1) }, shape = CircleShape,
                        color = if (history == (index == 1)) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.weight(1f)) {
                        Box(Modifier.height(42.dp), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }
        SearchField(search, { search = it }, "Search calls")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
            if (loading) item { Box(Modifier.padding(16.dp)) { ThinkingIndicator("Refreshing calls") } }
            error?.let { item { InlineError(it, !loading, onRefresh) } }
            if (filtered.isEmpty() && !loading) item {
                QuietNotice(if (error != null) "Your calls aren't available right now."
                    else if (search.isNotBlank()) "No matching calls."
                    else if (history) "Your past calls will appear here." else "No upcoming calls. Ask PocketCall to schedule one.")
            }
            groups.forEach { (date, entries) ->
                item(key = "day-$date") { SectionLabel(when(date) {
                    today -> "TODAY"
                    today.plusDays(1) -> "TOMORROW"
                    null -> "DATE UNAVAILABLE"
                    else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)).uppercase()
                }) }
                items(entries, key = { it.id }) { call ->
                    CallListRow(call, onClick = { onCall(call) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
fun ContactsScreen(
    contacts: List<ContactDraft>, permissionGranted: Boolean, loading: Boolean,
    onPermission: () -> Unit, onContact: (ContactDraft) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("voxie_contact_favourites_device", android.content.Context.MODE_PRIVATE) }
    var favourites by remember { mutableStateOf(preferences.getStringSet("numbers", emptySet())?.toSet().orEmpty()) }
    var onlyFavourites by rememberSaveable { mutableStateOf(false) }
    val favouriteHaptics = rememberPocketHaptics()
    val filtered = remember(contacts, search, favourites, onlyFavourites) {
        contacts.filter { (!onlyFavourites || it.number in favourites) && (it.name.contains(search, true) || it.number.contains(search, true)) }
            .sortedBy { it.name.lowercase() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        VoxiePageHeader("Your people", "Contacts from this phone, not your Voxie account.")
        if (!permissionGranted) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(20.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                PocketIcon(Glyph.Contacts)
                Spacer(Modifier.height(16.dp))
                Text("Find people by name", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text("Allow PocketCall to read your phone contacts so you can say who to call.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = hapticClick(onPermission)) { Text("Allow contacts") }
                Text("You can also type a phone number in chat.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            SearchField(search, { search = it }, "Search all names or numbers")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlyFavourites, onClick = hapticClick { onlyFavourites = false }, label = { Text("All contacts") })
                FilterChip(selected = onlyFavourites, onClick = hapticClick { onlyFavourites = true }, label = { Text("Favourites") })
            }
            Text("${contacts.size} contacts on this phone", modifier = Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (loading) item { ThinkingIndicator("Reading contacts") }
                if (filtered.isEmpty() && !loading) item { QuietNotice(if (search.isBlank()) "No phone contacts found." else "No contacts match your search.") }
                filtered.groupBy { it.name.firstOrNull()?.uppercase() ?: "#" }.forEach { (initial, entries) ->
                    item("letter-$initial") { SectionLabel(initial) }
                    items(entries, key = { it.id }) { contact ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface).clickable(onClick = hapticClick { onContact(contact) }).heightIn(min = 72.dp).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(contact.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase(),
                                    style = MaterialTheme.typography.labelLarge)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(contact.number, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            IconToggleButton(checked = contact.number in favourites,
                                onCheckedChange = { checked ->
                                    favouriteHaptics.tap()
                                    favourites = if (checked) favourites + contact.number else favourites - contact.number
                                    preferences.edit().putStringSet("numbers", favourites).apply()
                                }, modifier = Modifier.semantics {
                                    contentDescription = if (contact.number in favourites) "Remove ${contact.name} from favourites" else "Favourite ${contact.name}"
                                }) {
                                Text(if (contact.number in favourites) "★" else "☆", style = MaterialTheme.typography.headlineSmall,
                                    color = if (contact.number in favourites) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class SettingsPage { Overview, Account, Appearance, Calling, Permissions, Connection, About }

private data class SettingsCategory(val page: SettingsPage, val title: String, val description: String,
    val glyph: Glyph, val keywords: String)

@Composable
fun SettingsScreen(
    state: CallAiUiState, theme: String, onTheme: (String) -> Unit, onSignOut: () -> Unit,
    onBackend: (String) -> Unit, onPermissions: () -> Unit,
    mode: String = "personal", profile: ProfileState = ProfileState(),
    onSaveProfile: (String, String?, String) -> Unit = { _, _, _ -> },
    page: SettingsPage = SettingsPage.Overview, onPage: (SettingsPage) -> Unit = {},
) {
    var search by rememberSaveable { mutableStateOf("") }
    var backend by rememberSaveable(state.backendBaseUrl) { mutableStateOf(state.backendBaseUrl) }
    var displayName by remember(profile.name) { mutableStateOf(profile.name) }
    var organization by remember(profile.organization) { mutableStateOf(profile.organization) }
    var timezone by remember(profile.timezone) { mutableStateOf(profile.timezone) }
    var backendError by remember { mutableStateOf(false) }
    val categories = listOf(
        SettingsCategory(SettingsPage.Account, "Account", "Name, organization and timezone", Glyph.Contacts, "profile display name business sign out"),
        SettingsCategory(SettingsPage.Appearance, "Appearance", "Theme and display", Glyph.Sun, "system light dark mode"),
        SettingsCategory(SettingsPage.Calling, "Calling", "Call confirmations", Glyph.Calls, "schedule automatic confirm"),
        SettingsCategory(SettingsPage.Permissions, "Permissions", "Microphone and contacts access", Glyph.Check, "phone app settings"),
        SettingsCategory(SettingsPage.Connection, "Connection", "Backend server address", Glyph.Refresh, "url network server"),
        SettingsCategory(SettingsPage.About, "About Voxie", "Version and app details", Glyph.Bell, "help information"),
    )
    val results = categories.filter { category ->
        search.isBlank() || listOf(category.title, category.description, category.keywords)
            .any { it.contains(search.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        SearchField(search, { search = it }, "Search settings")
        Spacer(Modifier.height(14.dp))
        Crossfade(targetState = if (search.isNotBlank()) SettingsPage.Overview else page,
            label = "Settings section", modifier = Modifier.weight(1f)) { currentPage ->
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (currentPage == SettingsPage.Overview) {
                    if (search.isBlank()) {
                        item {
                            Text("Your settings", style = MaterialTheme.typography.headlineMedium)
                            Text("The little things that make Voxie yours.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        item {
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
                                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    PocketIcon(Glyph.Contacts)
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(state.userName.trim().ifBlank { "Your account" }, style = MaterialTheme.typography.titleMedium)
                                        Text("${if (mode == "business") "Business" else "Personal"} · ${profile.balance} test credits",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
                            Column {
                                results.forEachIndexed { index, category ->
                                    SettingsCategoryRow(category) { search = ""; onPage(category.page) }
                                    if (index != results.lastIndex) HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 18.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                if (results.isEmpty()) Text("No matching settings.", Modifier.padding(20.dp))
                            }
                        }
                    }
                } else {
                    item {
                        Text(categories.first { it.page == currentPage }.title,
                            style = MaterialTheme.typography.headlineMedium)
                    }
                    when (currentPage) {
                        SettingsPage.Account -> {
                            item {
                                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
                                        if (mode == "business") OutlinedTextField(organization, { organization = it },
                                            label = { Text("Organization name") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(timezone, { timezone = it }, label = { Text("Timezone") }, modifier = Modifier.fillMaxWidth())
                                        Button(onClick = hapticClick {
                                            onSaveProfile(displayName, organization.takeIf { mode == "business" }, timezone)
                                        }, enabled = !profile.busy && displayName.isNotBlank(),
                                            modifier = Modifier.fillMaxWidth()) { Text("Save profile") }
                                    }
                                }
                            }
                            profile.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                            profile.notice?.let { item { Text(it) } }
                            item { OutlinedButton(onClick = hapticClick(onSignOut),
                                enabled = !state.isProcessing && !state.isRecording,
                                modifier = Modifier.fillMaxWidth()) { Text("Sign out") } }
                        }
                        SettingsPage.Appearance -> item { ThemeModeSwitch(theme, onTheme) }
                        SettingsPage.Calling -> item {
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CallConfirmationSetting()
                                    HorizontalDivider()
                                    Text("Time zone: ${profile.timezone}", style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = hapticClick { onPage(SettingsPage.Account) }) { Text("Change timezone") }
                                }
                            }
                        }
                        SettingsPage.Permissions -> item {
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Voxie uses your microphone for voice prompts and contacts only when you choose to share them.")
                                    Button(onClick = hapticClick(onPermissions), modifier = Modifier.fillMaxWidth()) {
                                        Text("Open app permissions")
                                    }
                                }
                            }
                        }
                        SettingsPage.Connection -> item {
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(backend, { backend = it; backendError = false },
                                        label = { Text("Backend URL") },
                                        supportingText = { Text(if (backendError) "Enter a valid http:// or https:// URL."
                                            else "Use a stable HTTPS URL for calls across networks.") },
                                        isError = backendError, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    Button(onClick = hapticClick {
                                        val valid = runCatching { java.net.URI(backend.trim()) }.getOrNull()
                                        backendError = valid?.scheme !in listOf("http", "https") || valid?.host.isNullOrBlank()
                                        if (!backendError) onBackend(backend.trim().trimEnd('/') + "/")
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Save connection") }
                                }
                            }
                        }
                        SettingsPage.About -> item {
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Voxie 1.0", style = MaterialTheme.typography.titleMedium)
                                    Text("Your AI calling assistant", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        SettingsPage.Overview -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(category: SettingsCategory, onClick: () -> Unit) {
    val light = MaterialTheme.colorScheme.background.luminance() > .5f
    val iconBackground = if (light) Color(0xFFE7F1FF) else Color(0xFF29425C)
    val iconColor = if (light) Color(0xFF315B86) else Color(0xFFD5E8FF)
    Row(Modifier.fillMaxWidth().clickable(onClick = hapticClick(onClick))
        .heightIn(min = 74.dp).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Surface(color = iconBackground, shape = RoundedCornerShape(15.dp)) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                PocketIcon(category.glyph, color = iconColor)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(category.title, style = MaterialTheme.typography.titleSmall)
            Text(category.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PocketIcon(Glyph.Next, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThemeModeSwitch(theme: String, onTheme: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val light = colors.background.luminance() > .5f
    Surface(color = colors.surface, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Choose a theme", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().background(colors.surfaceContainer, CircleShape).padding(4.dp)) {
                listOf(Triple("System", Glyph.Device, "Follow phone theme"),
                    Triple("Light", Glyph.Sun, "Light theme"),
                    Triple("Dark", Glyph.Moon, "Dark theme")).forEach { (choice, icon, label) ->
                    val selected = theme == choice
                    Surface(onClick = hapticClick { onTheme(choice) }, modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        color = if (selected) colors.surface else androidx.compose.ui.graphics.Color.Transparent,
                        shadowElevation = if (selected) 2.dp else 0.dp) {
                        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                            PocketIcon(icon, label, if (selected) colors.onSurface else colors.onSurfaceVariant)
                        }
                    }
                }
            }
            Surface(color = if (light) Color(0xFFE7F1FF) else Color(0xFF29425C), shape = CircleShape) {
                Text("${theme} mode enabled", Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (light) Color(0xFF315B86) else Color(0xFFD5E8FF))
            }
        }
    }
}
