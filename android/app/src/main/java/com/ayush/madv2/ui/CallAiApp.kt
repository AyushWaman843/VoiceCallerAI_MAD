package com.ayush.madv2.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayush.madv2.ui.theme.AppAccent
import com.ayush.madv2.ui.theme.AppAccentSoft
import com.ayush.madv2.ui.theme.AppBackground
import com.ayush.madv2.ui.theme.AppDanger
import com.ayush.madv2.ui.theme.AppDangerSoft
import com.ayush.madv2.ui.theme.AppLine
import com.ayush.madv2.ui.theme.AppMuted
import com.ayush.madv2.ui.theme.AppPanel
import com.ayush.madv2.ui.theme.AppPanelAlt
import com.ayush.madv2.ui.theme.AppText
import com.ayush.madv2.ui.theme.MADv2Theme
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId

enum class AppTab {
    Home,
    Scheduled,
    Contacts,
}

data class QuickAction(
    val title: String,
    val prompt: String,
)

@Composable
fun CallAiApp(
    callAiViewModel: CallAiViewModel = viewModel(),
) {
    val uiState by callAiViewModel.uiState.collectAsState()
    val quickActions = remember {
        listOf(
            QuickAction(
                title = "Call someone",
                prompt = "Call Ayush and tell him he owes me 50 rupees tomorrow at 3pm",
            ),
            QuickAction(
                title = "Schedule a call",
                prompt = "Call Rahul after 2 hours and ask him about the project update",
            ),
            QuickAction(
                title = "Follow up with...",
                prompt = "Call 8879279251 tomorrow at 9am and tell him the meeting is confirmed",
            ),
        )
    }

    CallAiAppContent(
        uiState = uiState,
        quickActions = quickActions,
        onSelectTab = callAiViewModel::selectTab,
        onToggleSettings = callAiViewModel::toggleSettings,
        onOpenHomeSettings = callAiViewModel::openHomeSettings,
        onPromptChange = callAiViewModel::setPrompt,
        onSubmitPrompt = callAiViewModel::submitPrompt,
        onConfirmSchedule = callAiViewModel::confirmSchedule,
        onQuickActionClick = callAiViewModel::useQuickPrompt,
        onUserNameChange = callAiViewModel::setUserName,
        onUserIdChange = callAiViewModel::setUserId,
        onBackendBaseUrlChange = callAiViewModel::setBackendBaseUrl,
        onAddContact = callAiViewModel::addContact,
        onRemoveContact = callAiViewModel::removeContact,
        onContactNameChange = callAiViewModel::updateContactName,
        onContactNumberChange = callAiViewModel::updateContactNumber,
        onMissingFieldChange = callAiViewModel::updateMissingField,
        onRefreshCalls = callAiViewModel::refreshCalls,
        onCancelCall = callAiViewModel::cancelCall,
        onClearMessages = callAiViewModel::clearMessages,
    )
}

@Composable
private fun CallAiAppContent(
    uiState: CallAiUiState,
    quickActions: List<QuickAction>,
    onSelectTab: (AppTab) -> Unit,
    onToggleSettings: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onPromptChange: (String) -> Unit,
    onSubmitPrompt: () -> Unit,
    onConfirmSchedule: () -> Unit,
    onQuickActionClick: (String) -> Unit,
    onUserNameChange: (String) -> Unit,
    onUserIdChange: (String) -> Unit,
    onBackendBaseUrlChange: (String) -> Unit,
    onAddContact: () -> Unit,
    onRemoveContact: (Long) -> Unit,
    onContactNameChange: (Long, String) -> Unit,
    onContactNumberChange: (Long, String) -> Unit,
    onMissingFieldChange: (String, String) -> Unit,
    onRefreshCalls: () -> Unit,
    onCancelCall: (String) -> Unit,
    onClearMessages: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
        color = AppBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            when (uiState.selectedTab) {
                AppTab.Home -> {
                    HomeTab(
                        uiState = uiState,
                        quickActions = quickActions,
                        onToggleSettings = onToggleSettings,
                        onPromptChange = onPromptChange,
                        onSubmitPrompt = onSubmitPrompt,
                        onConfirmSchedule = onConfirmSchedule,
                        onQuickActionClick = onQuickActionClick,
                        onUserNameChange = onUserNameChange,
                        onUserIdChange = onUserIdChange,
                        onBackendBaseUrlChange = onBackendBaseUrlChange,
                        onAddContact = onAddContact,
                        onRemoveContact = onRemoveContact,
                        onContactNameChange = onContactNameChange,
                        onContactNumberChange = onContactNumberChange,
                        onMissingFieldChange = onMissingFieldChange,
                        onClearMessages = onClearMessages,
                    )
                }

                AppTab.Scheduled -> {
                    ScheduledTab(
                        uiState = uiState,
                        onRefreshCalls = onRefreshCalls,
                        onCancelCall = onCancelCall,
                    )
                }

                AppTab.Contacts -> {
                    ContactsPlaceholder(
                        onOpenSettings = onOpenHomeSettings,
                    )
                }
            }

            BottomNavigationBar(
                selectedTab = uiState.selectedTab,
                onTabSelected = onSelectTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun HomeTab(
    uiState: CallAiUiState,
    quickActions: List<QuickAction>,
    onToggleSettings: () -> Unit,
    onPromptChange: (String) -> Unit,
    onSubmitPrompt: () -> Unit,
    onConfirmSchedule: () -> Unit,
    onQuickActionClick: (String) -> Unit,
    onUserNameChange: (String) -> Unit,
    onUserIdChange: (String) -> Unit,
    onBackendBaseUrlChange: (String) -> Unit,
    onAddContact: () -> Unit,
    onRemoveContact: (Long) -> Unit,
    onContactNameChange: (Long, String) -> Unit,
    onContactNumberChange: (Long, String) -> Unit,
    onMissingFieldChange: (String, String) -> Unit,
    onClearMessages: () -> Unit,
) {
    val currentHour = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).hour
    val greeting = when {
        currentHour < 12 -> "Good morning,"
        currentHour < 17 -> "Good afternoon,"
        else -> "Good evening,"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                SmallCircleButton(
                    onClick = onToggleSettings,
                    containerColor = Color.White,
                    label = "+",
                    contentColor = AppMuted,
                )
            }

            Spacer(modifier = Modifier.height(76.dp))

            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 36.sp,
                    lineHeight = 42.sp,
                    textAlign = TextAlign.Center,
                ),
                color = AppText,
            )
            Text(
                text = uiState.userName.ifBlank { "Ayush" },
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 36.sp,
                    lineHeight = 42.sp,
                    textAlign = TextAlign.Center,
                ),
                color = AppText,
            )
            Text(
                text = "What would you like me to call?",
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                ),
                color = AppMuted,
            )

            PromptComposer(
                prompt = uiState.prompt,
                onPromptChange = onPromptChange,
                onSubmitPrompt = onSubmitPrompt,
                isProcessing = uiState.isProcessing,
                modifier = Modifier.padding(top = 48.dp),
            )

            if (uiState.errorMessage != null || uiState.infoMessage != null) {
                MessageBanner(
                    message = uiState.errorMessage ?: uiState.infoMessage.orEmpty(),
                    isError = uiState.errorMessage != null,
                    modifier = Modifier.padding(top = 16.dp),
                    onDismiss = onClearMessages,
                )
            }

            AnimatedVisibility(
                visible = uiState.isProcessing,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                ProcessingCard()
            }

            AnimatedVisibility(
                visible = uiState.missingFieldsDraft != null,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                uiState.missingFieldsDraft?.let { draft ->
                    MissingFieldsCard(
                        draft = draft,
                        onFieldChange = onMissingFieldChange,
                        onContinue = onSubmitPrompt,
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.confirmationDraft != null,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                uiState.confirmationDraft?.let { draft ->
                    ConfirmationCard(
                        draft = draft,
                        onConfirm = onConfirmSchedule,
                    )
                }
            }

            Text(
                text = "Try: \"Call John tomorrow at 4 PM and tell him he sucks.\"",
                modifier = Modifier.padding(top = 18.dp),
                color = AppMuted,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                ),
            )

            FlowActions(
                quickActions = quickActions,
                onQuickActionClick = onQuickActionClick,
                modifier = Modifier.padding(top = 20.dp),
            )

            DebugCard(
                feedbackJson = uiState.feedbackJson,
                modifier = Modifier.padding(top = 20.dp),
            )

            Spacer(modifier = Modifier.height(128.dp))
        }

        AnimatedVisibility(
            visible = uiState.showSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 52.dp)
        ) {
            SettingsCard(
                backendBaseUrl = uiState.backendBaseUrl,
                userId = uiState.userId,
                userName = uiState.userName,
                contacts = uiState.contacts,
                onBackendBaseUrlChange = onBackendBaseUrlChange,
                onUserIdChange = onUserIdChange,
                onUserNameChange = onUserNameChange,
                onAddContact = onAddContact,
                onRemoveContact = onRemoveContact,
                onContactNameChange = onContactNameChange,
                onContactNumberChange = onContactNumberChange,
            )
        }
    }
}

@Composable
private fun PromptComposer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSubmitPrompt: () -> Unit,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 16.dp, end = 14.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 2.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = AppText,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                ),
                decorationBox = { innerTextField ->
                    if (prompt.isBlank()) {
                        Text(
                            text = "Tell me who to call and what to say...",
                            color = Color(0xFFB7A7C6),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                            ),
                        )
                    }
                    innerTextField()
                },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SmallCircleButton(
                    onClick = {},
                    containerColor = Color.Transparent,
                    contentColor = AppText,
                ) {
                    MicGlyph()
                }
                SmallCircleButton(
                    onClick = onSubmitPrompt,
                    containerColor = if (isProcessing) AppAccentSoft else AppAccent,
                    contentColor = Color.White,
                ) {
                    ArrowGlyph()
                }
            }
        }
    }
}

@Composable
private fun MessageBanner(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isError) AppDangerSoft else AppAccentSoft,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = if (isError) AppDanger else AppAccent,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
            )
            Text(
                text = "Dismiss",
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
                color = if (isError) AppDanger else AppAccent,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun ProcessingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                repeat(5) { index ->
                    Surface(
                        modifier = Modifier
                            .height((10 + index * 4).dp)
                            .size(width = 6.dp, height = (10 + index * 4).dp),
                        color = AppAccent,
                        shape = RoundedCornerShape(999.dp),
                    ) {}
                }
            }
            Text(
                text = "Thinking through your call",
                modifier = Modifier.padding(top = 16.dp),
                color = AppText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Matching the contact, finding the time, and preparing the spoken message.",
                modifier = Modifier.padding(top = 8.dp),
                color = AppMuted,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun MissingFieldsCard(
    draft: MissingFieldsDraft,
    onFieldChange: (String, String) -> Unit,
    onContinue: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Text(
                text = "One more thing",
                color = AppAccent,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
            )
            Text(
                text = "Fill only what is missing.",
                modifier = Modifier.padding(top = 8.dp),
                color = AppText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )

            draft.fields.forEach { field ->
                Spacer(modifier = Modifier.height(14.dp))
                FieldLabel(labelFor(field))
                FieldInput(
                    value = valueFor(field, draft),
                    placeholder = placeholderFor(field),
                    onValueChange = { onFieldChange(field, it) },
                )
            }

            FilledActionButton(
                label = "Continue",
                modifier = Modifier.padding(top = 18.dp),
                onClick = onContinue,
            )
        }
    }
}

@Composable
private fun ConfirmationCard(
    draft: ConfirmationDraft,
    onConfirm: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Text(
                text = "Here's what I understood",
                color = AppAccent,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
            )
            Text(
                text = "Confirm this call",
                modifier = Modifier.padding(top = 8.dp),
                color = AppText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )

            Spacer(modifier = Modifier.height(16.dp))
            ConfirmationItem(label = "Call", value = "${draft.contactName} / ${draft.contactNumber}")
            Spacer(modifier = Modifier.height(10.dp))
            ConfirmationItem(label = "When", value = formatTime(draft.scheduledTime))
            Spacer(modifier = Modifier.height(10.dp))
            ConfirmationItem(label = "Say", value = draft.messagePreview)

            FilledActionButton(
                label = "Schedule call",
                modifier = Modifier.padding(top = 18.dp),
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun ConfirmationItem(
    label: String,
    value: String,
) {
    Surface(
        color = AppPanelAlt,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Text(
                text = label.uppercase(),
                color = AppMuted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                ),
            )
            Text(
                text = value,
                modifier = Modifier.padding(top = 6.dp),
                color = AppText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
            )
        }
    }
}

@Composable
private fun FlowActions(
    quickActions: List<QuickAction>,
    onQuickActionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            quickActions.take(2).forEach { action ->
                ActionPill(
                    label = action.title,
                    onClick = { onQuickActionClick(action.prompt) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        ActionPill(
            label = quickActions.last().title,
            onClick = { onQuickActionClick(quickActions.last().prompt) },
        )
    }
}

@Composable
private fun DebugCard(
    feedbackJson: String,
    modifier: Modifier = Modifier,
) {
    if (feedbackJson.isBlank()) {
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Text(
                text = "Agent response",
                color = AppAccent,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
            )
            Text(
                text = feedbackJson,
                modifier = Modifier.padding(top = 10.dp),
                color = AppMuted,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}

@Composable
private fun SettingsCard(
    backendBaseUrl: String,
    userId: String,
    userName: String,
    contacts: List<ContactDraft>,
    onBackendBaseUrlChange: (String) -> Unit,
    onUserIdChange: (String) -> Unit,
    onUserNameChange: (String) -> Unit,
    onAddContact: () -> Unit,
    onRemoveContact: (Long) -> Unit,
    onContactNameChange: (Long, String) -> Unit,
    onContactNumberChange: (Long, String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppPanel,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = "Testing Setup",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                ),
                color = AppText,
            )
            Text(
                text = "Physical phone default: http://192.168.29.220:5000/ on the same Wi-Fi. Emulator default: http://10.0.2.2:5000/.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                color = AppMuted,
            )

            Spacer(modifier = Modifier.height(14.dp))
            FieldLabel("Backend URL")
            FieldInput(
                value = backendBaseUrl,
                placeholder = "http://192.168.29.220:5000/",
                onValueChange = onBackendBaseUrlChange,
            )

            Spacer(modifier = Modifier.height(12.dp))
            FieldLabel("User ID")
            FieldInput(
                value = userId,
                placeholder = "user_001",
                onValueChange = onUserIdChange,
            )

            Spacer(modifier = Modifier.height(12.dp))
            FieldLabel("Your name")
            FieldInput(
                value = userName,
                placeholder = "Ayush",
                onValueChange = onUserNameChange,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Contacts",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                ActionPill(
                    label = "Add",
                    onClick = onAddContact,
                )
            }

            contacts.forEach { contact ->
                Spacer(modifier = Modifier.height(12.dp))
                FieldInput(
                    value = contact.name,
                    placeholder = "Contact name",
                    onValueChange = { onContactNameChange(contact.id, it) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                FieldInput(
                    value = contact.number,
                    placeholder = "+918879279251",
                    onValueChange = { onContactNumberChange(contact.id, it) },
                )
                Text(
                    text = "Remove",
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onRemoveContact(contact.id) },
                        ),
                    color = AppDanger,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun ScheduledTab(
    uiState: CallAiUiState,
    onRefreshCalls: () -> Unit,
    onCancelCall: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = "CallAI",
                    color = AppAccent,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    ),
                )
                Text(
                    text = "Scheduled",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                    ),
                    color = AppText,
                )
                Text(
                    text = "Your upcoming automated conversations.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    ),
                    color = AppMuted,
                )
            }
            ActionPill(
                label = if (uiState.isRefreshingCalls) "Loading..." else "Refresh",
                onClick = onRefreshCalls,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionHeader(title = "Upcoming", count = uiState.upcomingCalls.size)
            }

            if (uiState.upcomingCalls.isEmpty()) {
                item {
                    EmptyCard(text = "No upcoming calls yet.")
                }
            }

            items(uiState.upcomingCalls) { call ->
                ScheduledCallItem(call = call, onCancelCall = onCancelCall)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(title = "History", count = uiState.historyCalls.size, muted = true)
            }

            if (uiState.historyCalls.isEmpty()) {
                item {
                    EmptyCard(text = "No past calls yet.")
                }
            }

            items(uiState.historyCalls) { call ->
                ScheduledCallItem(call = call, onCancelCall = onCancelCall)
            }

            item {
                Spacer(modifier = Modifier.height(110.dp))
            }
        }
    }
}

@Composable
private fun ScheduledCallItem(
    call: ScheduledCallUiModel,
    onCancelCall: (String) -> Unit,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = call.status.uppercase(),
                        color = if (call.status == "Connected") AppAccent else AppMuted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                    )
                    Text(
                        text = call.contactName,
                        modifier = Modifier.padding(top = 8.dp),
                        color = AppText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                        ),
                    )
                }
                StatusBadge(label = call.status)
            }

            Text(
                text = "${call.timeLabel} / ${call.contactNumber}",
                modifier = Modifier.padding(top = 10.dp),
                color = AppMuted,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            )
            Text(
                text = call.message,
                modifier = Modifier.padding(top = 12.dp),
                color = AppText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                ),
            )

            if (call.canCancel) {
                Text(
                    text = "Cancel",
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onCancelCall(call.id) },
                        ),
                    color = AppDanger,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Surface(
        color = AppPanel,
        shape = RoundedCornerShape(18.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = AppMuted,
                style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
            )
        }
    }
}

@Composable
private fun ContactsPlaceholder(
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(132.dp))
        Surface(
            modifier = Modifier.size(72.dp),
            color = AppAccentSoft,
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                PersonGlyph(color = AppAccent)
            }
        }
        Text(
            text = "Contacts will come from the phone later.",
            modifier = Modifier.padding(top = 20.dp),
            color = AppText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Text(
            text = "For now, use the testing setup drawer to change the backend URL, your name, and the contacts used for matching.",
            modifier = Modifier.padding(top = 10.dp),
            color = AppMuted,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(modifier = Modifier.height(22.dp))
        ActionPill(
            label = "Open testing setup",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun BottomNavigationBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppPanel,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            NavItem(
                label = "Home",
                isActive = selectedTab == AppTab.Home,
                onClick = { onTabSelected(AppTab.Home) },
            ) {
                HomeGlyph(color = if (selectedTab == AppTab.Home) AppAccent else AppMuted)
            }
            NavItem(
                label = "Scheduled",
                isActive = selectedTab == AppTab.Scheduled,
                onClick = { onTabSelected(AppTab.Scheduled) },
            ) {
                CalendarGlyph(color = if (selectedTab == AppTab.Scheduled) AppAccent else AppMuted)
            }
            NavItem(
                label = "Contacts",
                isActive = selectedTab == AppTab.Contacts,
                onClick = { onTabSelected(AppTab.Contacts) },
            ) {
                PersonGlyph(color = if (selectedTab == AppTab.Contacts) AppAccent else AppMuted)
            }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (isActive) AppAccentSoft else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(
            text = label,
            modifier = Modifier.padding(top = 6.dp),
            color = if (isActive) AppAccent else AppMuted,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp,
            ),
        )
    }
}

@Composable
private fun ActionPill(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        color = AppPanelAlt,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            color = AppText,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
        )
    }
}

@Composable
private fun FilledActionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        color = AppAccent,
        shape = RoundedCornerShape(999.dp),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun SmallCircleButton(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    label: String? = null,
    content: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        color = containerColor,
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (content != null) {
                content()
            } else {
                Text(
                    text = label.orEmpty(),
                    color = contentColor,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = AppMuted,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        ),
    )
}

@Composable
private fun FieldInput(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = AppText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        color = AppMuted,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        ),
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    muted: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = AppText,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            ),
        )
        Surface(
            color = if (muted) AppPanelAlt else AppAccentSoft,
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                color = if (muted) AppMuted else AppAccent,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun StatusBadge(label: String) {
    val background = when (label.lowercase()) {
        "connected" -> AppAccentSoft
        "pending" -> Color(0xFFF6EFE3)
        "failed", "cancelled" -> AppDangerSoft
        else -> AppPanelAlt
    }
    val color = when (label.lowercase()) {
        "connected" -> AppAccent
        "pending" -> Color(0xFFB6782D)
        "failed", "cancelled" -> AppDanger
        else -> AppMuted
    }

    Surface(
        color = background,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

private fun labelFor(field: String): String = when (field) {
    "contact_name" -> "Contact name"
    "contact_number" -> "Contact number"
    "message" -> "Message"
    "scheduled_time" -> "Scheduled time"
    else -> field
}

private fun placeholderFor(field: String): String = when (field) {
    "contact_name" -> "Ayush Waman"
    "contact_number" -> "+918879279251"
    "message" -> "Tell me what should be said"
    "scheduled_time" -> "2026-08-29T17:30:00+05:30"
    else -> field
}

private fun valueFor(field: String, draft: MissingFieldsDraft): String = when (field) {
    "contact_name" -> draft.contactName
    "contact_number" -> draft.contactNumber
    "message" -> draft.message
    "scheduled_time" -> draft.scheduledTime
    else -> ""
}

private fun formatTime(rawTime: String?): String {
    if (rawTime.isNullOrBlank()) {
        return "Not provided"
    }

    return runCatching {
        OffsetDateTime.parse(rawTime).format(DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a"))
    }.getOrElse { rawTime }
}

@Composable
private fun MicGlyph(color: Color = AppText) {
    Canvas(modifier = Modifier.size(16.dp)) {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.32f, size.height * 0.1f),
            size = Size(size.width * 0.36f, size.height * 0.48f),
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(width = 2f)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.2f, size.height * 0.48f),
            end = Offset(size.width * 0.8f, size.height * 0.48f),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.58f),
            end = Offset(size.width * 0.5f, size.height * 0.86f),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ArrowGlyph(color: Color = Color.White) {
    Canvas(modifier = Modifier.size(16.dp)) {
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.84f),
            end = Offset(size.width * 0.5f, size.height * 0.2f),
            strokeWidth = 2.3f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.2f, size.height * 0.48f),
            end = Offset(size.width * 0.5f, size.height * 0.18f),
            strokeWidth = 2.3f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.8f, size.height * 0.48f),
            end = Offset(size.width * 0.5f, size.height * 0.18f),
            strokeWidth = 2.3f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun HomeGlyph(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.1f)
            lineTo(size.width * 0.9f, size.height * 0.38f)
            lineTo(size.width * 0.9f, size.height * 0.88f)
            lineTo(size.width * 0.1f, size.height * 0.88f)
            lineTo(size.width * 0.1f, size.height * 0.38f)
            close()
        }
        drawPath(path = path, color = color, style = Stroke(width = 2.2f))
    }
}

@Composable
private fun CalendarGlyph(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.12f, size.height * 0.22f),
            size = Size(size.width * 0.76f, size.height * 0.64f),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 2.2f)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.12f, size.height * 0.38f),
            end = Offset(size.width * 0.88f, size.height * 0.38f),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun PersonGlyph(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        drawCircle(
            color = color,
            radius = size.minDimension * 0.18f,
            center = Offset(size.width * 0.5f, size.height * 0.3f),
            style = Stroke(width = 2.2f),
        )
        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.18f, size.height * 0.42f),
            size = Size(size.width * 0.64f, size.height * 0.42f),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F6F2)
@Composable
private fun CallAiAppPreview() {
    MADv2Theme(dynamicColor = false) {
        CallAiAppContent(
            uiState = CallAiUiState(),
            quickActions = listOf(
                QuickAction("Call someone", "Call Ayush"),
                QuickAction("Schedule a call", "Call Rahul tomorrow"),
                QuickAction("Follow up with...", "Call 8879279251"),
            ),
            onSelectTab = {},
            onToggleSettings = {},
            onOpenHomeSettings = {},
            onPromptChange = {},
            onSubmitPrompt = {},
            onConfirmSchedule = {},
            onQuickActionClick = {},
            onUserNameChange = {},
            onUserIdChange = {},
            onBackendBaseUrlChange = {},
            onAddContact = {},
            onRemoveContact = {},
            onContactNameChange = { _, _ -> },
            onContactNumberChange = { _, _ -> },
            onMissingFieldChange = { _, _ -> },
            onRefreshCalls = {},
            onCancelCall = {},
            onClearMessages = {},
        )
    }
}
