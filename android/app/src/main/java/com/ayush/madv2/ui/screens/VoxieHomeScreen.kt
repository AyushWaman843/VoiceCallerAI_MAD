package com.ayush.madv2.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayush.madv2.R
import com.ayush.madv2.ui.*
import com.ayush.madv2.ui.components.*
import java.time.LocalTime

@Composable
fun VoxieHomeScreen(state: CallAiUiState, onMakeCall: () -> Unit, onContacts: () -> Unit,
                    onCall: (ScheduledCallUiModel) -> Unit, business: Boolean = false,
                    credits: Int = 0,
                    onGroups: () -> Unit = {}, onTemplates: () -> Unit = {},
                    onViewAll: (Boolean) -> Unit = {}, composer: @Composable () -> Unit) {
    var recent by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val name = state.userName.trim().ifBlank { "there" }
    val calls = if (recent) state.historyCalls else state.upcomingCalls
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_voxie_mark), null, Modifier.size(30.dp))
                Text("Voxie", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Box(Modifier.size(48.dp).semantics { contentDescription = "Notifications, coming soon"; disabled() }, contentAlignment = Alignment.Center) {
                    PocketIcon(Glyph.Bell)
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 128.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$greeting,\n$name! 👋", fontSize = 25.sp, lineHeight = 31.sp,
                    letterSpacing = (-.7).sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Image(painterResource(R.drawable.voxie_landing), "Voxie", Modifier.size(135.dp))
            }
            composer()
            Text("Try: Wish Dad a happy birthday tomorrow at 9 AM. Voxie will ask for anything missing.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$credits test credits available", color = colors.onSurfaceVariant, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction("Make a call", Glyph.Calls, Color(0xFF7561FB), Modifier.weight(1f), onMakeCall)
                    if (business) QuickAction("Call a group", Glyph.Groups, Color(0xFFF25D93), Modifier.weight(1f), onGroups)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(if (business) "Saved groups" else "Phone contacts", Glyph.Contacts, Color(0xFF21AA69), Modifier.weight(1f), onContacts)
                    QuickAction("Templates", Glyph.Templates, Color(0xFF7561FB), Modifier.weight(1f), onTemplates)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().background(colors.surfaceContainer, CircleShape).padding(4.dp)) {
                listOf("Upcoming", "Recent").forEachIndexed { index, label ->
                    val selected = recent == (index == 1)
                    Surface(onClick = hapticClick { recent = index == 1 }, shape = CircleShape,
                        color = if (selected) colors.surface else Color.Transparent,
                        shadowElevation = if (selected) 2.dp else 0.dp, modifier = Modifier.weight(1f)) {
                        Box(Modifier.height(42.dp), contentAlignment = Alignment.Center) {
                            Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) colors.onSurface else colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (calls.isEmpty()) item {
            Surface(shape = RoundedCornerShape(22.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.isRefreshingCalls) ThinkingIndicator("Loading calls")
                    Text(if (state.isRefreshingCalls) "Loading calls…" else if (recent) "No recent calls" else "Your next call starts here", fontWeight = FontWeight.SemiBold)
                    Text(if (recent) "Your past calls will appear here." else "Tell Voxie who to call and what to say.", fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
        }
        items(calls.take(5), key = { it.id }) { call ->
            Surface(onClick = hapticClick { onCall(call) }, shape = RoundedCornerShape(22.dp),
                color = colors.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PocketIcon(Glyph.Calendar)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(call.contactName, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(call.timeLabel.replace(" at ", " - ").removeSuffix(" IST"), fontSize = 11.sp,
                            color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val pending = call.status.lowercase() == "pending"
                    Surface(color = if (pending) Color(0xFFD8FAE3) else colors.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
                        Text(if (pending) "Scheduled" else call.status.replaceFirstChar { it.uppercase() }, fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, color = if (pending) Color(0xFF238652) else colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                }
            }
        }
        if (calls.size > 5) item {
            TextButton(onClick = hapticClick { onViewAll(recent) }, modifier = Modifier.fillMaxWidth()) {
                Text("See all ${if (recent) "recent" else "upcoming"} calls")
                Spacer(Modifier.width(6.dp))
                PocketIcon(Glyph.Next)
            }
        }
        state.errorMessage?.let { message -> item { Text(message, color = colors.error, fontSize = 13.sp) } }
    }
}

@Composable
private fun QuickAction(label: String, glyph: Glyph, tint: Color, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val content: @Composable () -> Unit = {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(30.dp).background(tint.copy(alpha = .1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                PocketIcon(glyph, color = tint)
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
    if (onClick != null) Surface(onClick = hapticClick(onClick), modifier = modifier.pressMotion(interaction), interactionSource = interaction, shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface, content = content)
    else Surface(modifier = modifier.semantics { contentDescription = "$label, coming soon"; disabled() },
        shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surface, content = content)
}

@Composable
fun VoxieBottomBar(selected: String, enabled: Boolean, onHome: () -> Unit, onCalls: () -> Unit,
                   onCredits: () -> Unit, onProfile: () -> Unit, onGroups: () -> Unit = {}, business: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            val tabs = listOf(Triple("Home", Glyph.Home, onHome), Triple("Calls", Glyph.Calls, onCalls),
                Triple(if (business) "Groups" else "Contacts", Glyph.Groups, onGroups), Triple("Credits", Glyph.Wallet, onCredits), Triple("Profile", Glyph.Contacts, onProfile))
            tabs.forEach { (label, glyph, action) ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) .86f else 1f, spring(dampingRatio = .6f, stiffness = 500f), label = "Tab bounce")
                val glow by animateColorAsState(if (selected == label || pressed) MaterialTheme.colorScheme.secondary.copy(alpha = .10f) else Color.Transparent, label = "Tab glow")
                val color = if (selected == label) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                val content: @Composable () -> Unit = {
                    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(28.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                            .background(glow, CircleShape), contentAlignment = Alignment.Center) { PocketIcon(glyph, color = color) }
                        Text(label, color = color, fontSize = 9.sp, fontWeight = if (selected == label) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Surface(onClick = hapticClick(action), enabled = enabled, modifier = Modifier.weight(1f),
                    interactionSource = interaction, shape = CircleShape, color = Color.Transparent, content = content)
            }
        }
    }
}
