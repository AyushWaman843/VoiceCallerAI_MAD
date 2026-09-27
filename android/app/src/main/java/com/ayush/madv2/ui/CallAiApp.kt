package com.ayush.madv2.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.provider.ContactsContract
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayush.madv2.R
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
import java.io.File
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import kotlin.math.sin

enum class AppTab {
    Home,
    Scheduled,
    Contacts,
}

private const val CONTACTS_PREVIEW_COUNT = 10

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
        onSetContacts = callAiViewModel::setContacts,
        onSetRecording = callAiViewModel::setRecording,
        onTranscribeAudio = callAiViewModel::transcribeAudio,
        onShowError = callAiViewModel::showError,
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
    onSetContacts: (List<ContactDraft>) -> Unit,
    onSetRecording: (Boolean) -> Unit,
    onTranscribeAudio: (File) -> Unit,
    onShowError: (String) -> Unit,
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
                        onSetContacts = onSetContacts,
                        onSetRecording = onSetRecording,
                        onTranscribeAudio = onTranscribeAudio,
                        onShowError = onShowError,
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
                    ContactsTab(
                        uiState = uiState,
                        onOpenSettings = onOpenHomeSettings,
                        onSetContacts = onSetContacts,
                        onShowError = onShowError,
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
    onSetContacts: (List<ContactDraft>) -> Unit,
    onSetRecording: (Boolean) -> Unit,
    onTranscribeAudio: (File) -> Unit,
    onShowError: (String) -> Unit,
    onMissingFieldChange: (String, String) -> Unit,
    onClearMessages: () -> Unit,
) {
    val context = LocalContext.current
    val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            onSetContacts(loadPhoneContacts(context))
        } else {
            onShowError("Contacts permission is needed to match people from your phonebook.")
        }
    }
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            onShowError("Microphone permission is needed for voice prompts.")
        }
    }
    val hasContactsPermission = hasPermission(context, Manifest.permission.READ_CONTACTS)
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(hasContactsPermission) {
        if (hasContactsPermission && uiState.contacts.isEmpty()) {
            onSetContacts(loadPhoneContacts(context))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.runCatching {
                reset()
                release()
            }
            recordingFile?.delete()
        }
    }

    fun stopRecordingAndTranscribe() {
        val activeRecorder = recorder
        val audioFile = recordingFile
        recorder = null
        recordingFile = null
        onSetRecording(false)
        if (activeRecorder == null || audioFile == null) {
            onShowError("No voice recording was captured.")
            return
        }
        runCatching {
            activeRecorder.stop()
            activeRecorder.reset()
            activeRecorder.release()
        }.onSuccess {
            onTranscribeAudio(audioFile)
        }.onFailure {
            audioFile.delete()
            onShowError("Could not finish the recording. Please try again.")
        }
    }

    fun startRecording() {
        runCatching {
            val (newRecorder, newFile) = createVoiceRecorder(context)
            recorder = newRecorder
            recordingFile = newFile
            onSetRecording(true)
        }.onFailure {
            recordingFile?.delete()
            recorder = null
            recordingFile = null
            onSetRecording(false)
            onShowError("Could not start recording from the microphone.")
        }
    }

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

            Spacer(modifier = Modifier.height(52.dp))

            WelcomeHeader(
                greeting = greeting,
                userName = uiState.userName.ifBlank { "Ayush" },
            )
            Text(
                text = "What would you like me to call?",
                modifier = Modifier.padding(top = 18.dp),
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
                onMicClick = {
                    if (uiState.isRecording) {
                        stopRecordingAndTranscribe()
                    } else if (!hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        startRecording()
                    }
                },
                isProcessing = uiState.isProcessing,
                isRecording = uiState.isRecording,
                processingMessage = uiState.processingMessage,
                modifier = Modifier.padding(top = 48.dp),
            )

            if (!hasContactsPermission) {
                ActionPill(
                    label = "Allow contacts access",
                    onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                )
            } else if (uiState.contacts.isNotEmpty()) {
                Text(
                    text = "Using ${uiState.contacts.size} phone contacts for matching.",
                    modifier = Modifier.padding(top = 18.dp),
                    color = AppMuted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }

            if (uiState.errorMessage != null || uiState.infoMessage != null) {
                MessageBanner(
                    message = uiState.errorMessage ?: uiState.infoMessage.orEmpty(),
                    isError = uiState.errorMessage != null,
                    modifier = Modifier.padding(top = 16.dp),
                    onDismiss = onClearMessages,
                )
            }

            AnimatedVisibility(
                visible = uiState.isProcessing && !isInlineComposerState(uiState.processingMessage),
                modifier = Modifier.padding(top = 20.dp),
            ) {
                ProcessingCard(
                    headline = "Thinking through your call",
                    detail = when {
                        !uiState.processingMessage.isNullOrBlank() -> uiState.processingMessage
                        else -> "Matching the contact, finding the time, and preparing the spoken message."
                    },
                )
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
                onBackendBaseUrlChange = onBackendBaseUrlChange,
                onUserIdChange = onUserIdChange,
                onUserNameChange = onUserNameChange,
            )
        }
    }
}

@Composable
private fun PromptComposer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSubmitPrompt: () -> Unit,
    onMicClick: () -> Unit,
    isProcessing: Boolean,
    isRecording: Boolean,
    processingMessage: String?,
    modifier: Modifier = Modifier,
) {
    val inlineMessage = when {
        isRecording -> "Listening for your prompt"
        isInlineComposerState(processingMessage) -> processingMessage.orEmpty()
        else -> ""
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = AppText,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                    ),
                    maxLines = 3,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 26.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (prompt.isBlank()) {
                                Text(
                                    text = "Tell me who to call and what to say...",
                                    color = Color(0xFFA694B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp,
                                    ),
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SmallCircleButton(
                        onClick = onMicClick,
                        containerColor = if (isRecording) AppDanger else Color.Transparent,
                        contentColor = if (isRecording) Color.White else AppText,
                    ) {
                        if (isRecording) {
                            StopGlyph()
                        } else {
                            DrawableGlyph(
                                drawableRes = R.drawable.baseline_mic_24,
                                tint = AppText,
                            )
                        }
                    }
                    SmallCircleButton(
                        onClick = onSubmitPrompt,
                        containerColor = if (isProcessing || isRecording) AppAccentSoft else AppAccent,
                        contentColor = Color.White,
                    ) {
                        ArrowGlyph()
                    }
                }
            }

            AnimatedVisibility(
                visible = isRecording || inlineMessage.isNotBlank(),
            ) {
                ComposerStatusStrip(
                    label = if (isRecording) "Listening..." else inlineMessage,
                    isRecording = isRecording,
                    isThinking = !isRecording,
                    modifier = Modifier.padding(top = 14.dp, end = 8.dp),
                )
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
private fun ComposerStatusStrip(
    label: String,
    isRecording: Boolean,
    isThinking: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (isRecording) AppDanger else AppAccent,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                ),
            )
            Text(
                text = if (isRecording) "Tap the square to stop" else "Turning your voice into text",
                modifier = Modifier.padding(top = 4.dp),
                color = AppMuted,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        VoiceWaveform(
            active = isRecording || isThinking,
            tint = if (isRecording) AppDanger else AppAccent,
        )
    }
}

@Composable
private fun VoiceWaveform(
    active: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "voice-wave")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 1100 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "voice-wave-progress",
    )

    Canvas(
        modifier = modifier
            .width(94.dp)
            .height(28.dp)
    ) {
        val bars = 12
        val barWidth = size.width / (bars * 1.8f)
        val gap = barWidth * 0.8f
        for (index in 0 until bars) {
            val oscillation = (sin((progress * 6f) + index * 0.55f) + 1f) / 2f
            val normalizedHeight = if (active) 0.25f + (oscillation * 0.75f) else 0.2f
            val barHeight = size.height * normalizedHeight
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = tint.copy(alpha = 0.28f + (oscillation * 0.64f)),
                topLeft = Offset(left, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth, barWidth),
            )
        }
    }
}

@Composable
private fun ProcessingCard(
    headline: String,
    detail: String,
) {
    val transition = rememberInfiniteTransition(label = "processing-card")
    val glow by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "processing-card-glow",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            AppAccentSoft.copy(alpha = glow),
                        )
                    )
                )
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VoiceWaveform(active = true, tint = AppAccent)
            Text(
                text = headline,
                modifier = Modifier.padding(top = 16.dp),
                color = AppText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = detail,
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
    onBackendBaseUrlChange: (String) -> Unit,
    onUserIdChange: (String) -> Unit,
    onUserNameChange: (String) -> Unit,
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
        }
    }
}

@Composable
internal fun ScheduledTab(
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
                    text = "Your calls, one day at a time.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    ),
                    color = AppMuted,
                )
            }
            SmallCircleButton(
                onClick = onRefreshCalls,
                containerColor = if (uiState.isRefreshingCalls) AppAccentSoft else Color.White,
                contentColor = AppAccent,
            ) {
                DrawableGlyph(
                    drawableRes = R.drawable.refresh,
                    tint = AppAccent,
                )
            }
        }

        CallCalendar(
            uiState = uiState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 20.dp),
        ) { call ->
            ScheduledCallItem(call = call, onCancelCall = onCancelCall)
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
private fun ContactsTab(
    uiState: CallAiUiState,
    onOpenSettings: () -> Unit,
    onSetContacts: (List<ContactDraft>) -> Unit,
    onShowError: (String) -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showAllContacts by remember { mutableStateOf(false) }
    val hasContactsPermission = hasPermission(context, Manifest.permission.READ_CONTACTS)
    val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            onSetContacts(loadPhoneContacts(context))
        } else {
            onShowError("Contacts permission is needed to sync the phonebook.")
        }
    }

    LaunchedEffect(hasContactsPermission) {
        if (hasContactsPermission && uiState.contacts.isEmpty()) {
            onSetContacts(loadPhoneContacts(context))
        }
    }

    val filteredContacts = uiState.contacts.filter { contact ->
        if (searchQuery.isBlank()) {
            true
        } else {
            val needle = searchQuery.trim().lowercase()
            contact.name.lowercase().contains(needle) || contact.number.contains(needle)
        }
    }
    val visibleContacts = if (showAllContacts || searchQuery.isNotBlank()) filteredContacts else filteredContacts.take(CONTACTS_PREVIEW_COUNT)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Contacts",
                    color = AppAccent,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    ),
                )
                Text(
                    text = if (hasContactsPermission) "Search your phonebook" else "Bring in your phone contacts",
                    modifier = Modifier.padding(top = 8.dp),
                    color = AppText,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                    ),
                )
            }
            Surface(
                modifier = Modifier.size(48.dp),
                color = AppAccentSoft,
                shape = RoundedCornerShape(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    DrawableGlyph(
                        drawableRes = R.drawable.outline_account_circle_24,
                        tint = AppAccent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Text(
            text = if (hasContactsPermission) {
                "These ${uiState.contacts.size} contacts are what the scheduling agent uses to match names from your prompt."
            } else {
                "Grant access once and the app will use your actual phonebook instead of hardcoded testing contacts."
            },
            modifier = Modifier.padding(top = 12.dp),
            color = AppMuted,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                lineHeight = 21.sp,
            ),
        )
        Spacer(modifier = Modifier.height(22.dp))
        if (!hasContactsPermission) {
            FilledActionButton(
                label = "Allow contacts access",
                onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
            )
        } else {
            ActionPill(
                label = "Refresh contacts",
                onClick = { onSetContacts(loadPhoneContacts(context)) },
            )
            Spacer(modifier = Modifier.height(18.dp))
            FieldInput(
                value = searchQuery,
                placeholder = "Search name or number",
                onValueChange = {
                    searchQuery = it
                    if (it.isNotBlank()) {
                        showAllContacts = true
                    }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (uiState.contacts.isEmpty()) {
                EmptyCard(text = "No phone contacts were found.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    items(visibleContacts, key = { it.id }) { contact ->
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = contact.name,
                                    color = AppText,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = contact.number,
                                    modifier = Modifier.padding(top = 6.dp),
                                    color = AppMuted,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                )
                            }
                        }
                    }
                    if (!showAllContacts && searchQuery.isBlank() && filteredContacts.size > CONTACTS_PREVIEW_COUNT) {
                        item {
                            ActionPill(
                                label = "...more",
                                onClick = { showAllContacts = true },
                            )
                        }
                    }
                    if (filteredContacts.isEmpty()) {
                        item {
                            EmptyCard(text = "No contacts matched your search.")
                        }
                    }
                    item { Spacer(modifier = Modifier.height(110.dp)) }
                }
            }
        }

        if (!hasContactsPermission) {
            Spacer(modifier = Modifier.height(12.dp))
            ActionPill(
                label = "Open testing setup",
                onClick = onOpenSettings,
            )
        }
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
                DrawableGlyph(
                    drawableRes = R.drawable.outline_account_circle_24,
                    tint = if (selectedTab == AppTab.Contacts) AppAccent else AppMuted,
                )
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
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            color = AppText,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
        )
    }
}

@Composable
private fun WelcomeHeader(
    greeting: String,
    userName: String,
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = greeting.uppercase(),
                color = AppAccent,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                ),
            )
            Text(
                text = userName,
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 44.sp,
                    lineHeight = 46.sp,
                    textAlign = TextAlign.Center,
                ),
                color = AppText,
            )
            Canvas(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .width(112.dp)
                    .height(10.dp)
            ) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            AppAccentSoft.copy(alpha = 0.15f),
                            AppAccent.copy(alpha = 0.75f),
                            AppAccentSoft.copy(alpha = 0.15f),
                        )
                    ),
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(size.height, size.height),
                )
            }
        }
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
private fun DrawableGlyph(
    @DrawableRes drawableRes: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(id = drawableRes),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(18.dp),
    )
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

private fun isInlineComposerState(processingMessage: String?): Boolean {
    val message = processingMessage.orEmpty().lowercase()
    return message.contains("transcrib") || message.contains("voice")
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun loadPhoneContacts(context: Context): List<ContactDraft> {
    val contactsByNumber = linkedMapOf<String, ContactDraft>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
    )

    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        null,
        null,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        var nextId = 1L

        while (cursor.moveToNext()) {
            val rawName = cursor.getString(nameIndex).orEmpty().trim()
            val rawNumber = cursor.getString(numberIndex).orEmpty().trim()
            val normalizedNumber = normalizePhoneNumber(rawNumber)
            if (rawName.isBlank() || normalizedNumber.isBlank() || contactsByNumber.containsKey(normalizedNumber)) {
                continue
            }
            contactsByNumber[normalizedNumber] = ContactDraft(
                id = nextId++,
                name = rawName,
                number = normalizedNumber,
            )
        }
    }

    return contactsByNumber.values.toList()
}

private fun normalizePhoneNumber(rawNumber: String): String {
    val digits = rawNumber.filter { it.isDigit() }
    return when {
        digits.length == 10 && digits.first() in "6789" -> "+91$digits"
        digits.length == 12 && digits.startsWith("91") -> "+$digits"
        digits.length == 11 && digits.startsWith("0") && digits[1] in "6789" -> "+91${digits.drop(1)}"
        rawNumber.trim().startsWith("+") && digits.isNotBlank() -> rawNumber.trim()
        else -> rawNumber.trim()
    }
}

private fun createVoiceRecorder(context: Context): Pair<MediaRecorder, File> {
    val audioFile = File.createTempFile("voice_prompt_", ".m4a", context.cacheDir)
    val recorder = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioSamplingRate(16000)
        setAudioEncodingBitRate(96000)
        setOutputFile(audioFile.absolutePath)
        prepare()
        start()
    }
    return recorder to audioFile
}

@Composable
private fun StopGlyph(color: Color = Color.White) {
    Canvas(modifier = Modifier.size(14.dp)) {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
            size = Size(size.width * 0.64f, size.height * 0.64f),
            cornerRadius = CornerRadius(4f, 4f),
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
            onSetContacts = {},
            onSetRecording = {},
            onTranscribeAudio = {},
            onShowError = {},
            onMissingFieldChange = { _, _ -> },
            onRefreshCalls = {},
            onCancelCall = {},
            onClearMessages = {},
        )
    }
}
