package com.ayush.madv2.ui

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.ayush.madv2.R
import com.ayush.madv2.data.AccountSession
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayush.madv2.ui.components.*
import com.ayush.madv2.ui.screens.*
import com.ayush.madv2.ui.theme.MADv2Theme
import com.ayush.madv2.ui.theme.PocketMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class AppTab { Home, Scheduled, Contacts }
private enum class Destination { Home, Request, Upcoming, History, Calendar, Contacts, Groups, Settings, Credits, Templates }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallAiApp(callAiViewModel: CallAiViewModel = viewModel(), onSignOut: () -> Unit = {},
              onBackendChanged: (String) -> Unit = {}, mode: String = "personal",
              onCredits: () -> Unit = {}, business: BusinessViewModel = viewModel(),
              profile: ProfileViewModel = viewModel()) {
    val state by callAiViewModel.uiState.collectAsStateWithLifecycle()
    val account by AccountSession.account.collectAsStateWithLifecycle()
    val profileState by profile.state.collectAsStateWithLifecycle()
    val businessState by business.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("pocketcall_preferences", Context.MODE_PRIVATE) }
    val confirmationKey = "confirm_calls_${account?.user?.id ?: "signed_out"}"
    var confirmationRequired by remember(confirmationKey) { mutableStateOf(preferences.getBoolean(confirmationKey, true)) }
    var theme by remember { mutableStateOf(preferences.getString("theme", "System") ?: "System") }
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var focusRequest by remember { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Overview) }
    val homeFocus = remember { FocusRequester() }
    var contactPicker by remember { mutableStateOf(false) }
    var sheetGroup by remember { mutableStateOf<String?>(null) }
    var pendingCampaignGroup by remember { mutableStateOf<String?>(null) }
    var pendingCampaignPrompt by remember { mutableStateOf("") }
    var addPromptItem by remember { mutableStateOf(false) }
    var templatePickerOnly by remember { mutableStateOf(false) }
    var attachedGroup by remember(account?.user?.id) { mutableStateOf<BusinessGroup?>(null) }
    var attachedTemplate by remember(account?.user?.id) { mutableStateOf<MessageTemplate?>(null) }
    var selectedCall by remember { mutableStateOf<ScheduledCallUiModel?>(null) }
    var selectedContact by remember { mutableStateOf<ContactDraft?>(null) }
    var contactsGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    var loadingContacts by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val imeOverlap = (WindowInsets.ime.getBottom(density) - WindowInsets.navigationBars.getBottom(density)).coerceAtLeast(0)
    val bottomBarClearance = (68.dp - with(density) { imeOverlap.toDp() }).coerceAtLeast(0.dp)
    val haptics = rememberPocketHaptics()
    val capture = rememberVoiceCapture { callAiViewModel.setRecording(false) }
    val screenStates = rememberSaveableStateHolder()

    fun syncContacts() {
        if (loadingContacts) return
        scope.launch {
            loadingContacts = true
            runCatching { withContext(Dispatchers.IO) { loadPhoneContacts(context) } }
                .onSuccess(callAiViewModel::setContacts)
                .onFailure { callAiViewModel.showError("Couldn't read your contacts. Check PocketCall's contact permission.") }
            loadingContacts = false
        }
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsGranted = granted
        if (granted) syncContacts()
        else {
            callAiViewModel.setContacts(emptyList())
            callAiViewModel.showError("Contacts access is off. You can still enter a phone number in your message.")
        }
    }
    fun startRecording() {
        keyboard?.hide()
        focus.clearFocus()
        runCatching { capture.start() }
            .onSuccess { callAiViewModel.setRecording(true) }
            .onFailure { callAiViewModel.showError("Couldn't start the microphone. Please try again.") }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else callAiViewModel.showError("Allow microphone access in your phone's app settings to record a message.")
    }
    fun recordVoice() {
        destination = Destination.Request
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            startRecording()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun submitHomePrompt(prompt: String = state.prompt.trim()) {
        if (prompt.isBlank() || state.isProcessing || businessState.busy) return
        callAiViewModel.beginNewRequest()
        destination = Destination.Request
        fun submitIndividual() {
            attachedTemplate?.let { callAiViewModel.setPrompt("Use template \"${it.name}\" to $prompt") }
            callAiViewModel.submitPrompt()
        }
        if (mode == "business") {
            if (attachedGroup != null) business.selectGroup(attachedGroup!!, prompt)
            else business.resolvePrompt(prompt, attachedTemplate?.id) {
                submitIndividual()
            }
        } else {
            submitIndividual()
        }
    }
    fun finishVoice() {
        val audio = capture.finish()
        callAiViewModel.setRecording(false)
        if (audio != null) {
            destination = Destination.Request
            callAiViewModel.transcribeAudio(audio, onReady = {
                if (mode == "business" && state.missingFieldsDraft == null) submitHomePrompt(it)
                else callAiViewModel.submitPrompt()
            })
        }
        else callAiViewModel.showError("That recording was too short. Please try again.")
    }
    LaunchedEffect(state.conversation.size) {
        // Transcription submits through the existing agent; reveal its conversation when ready.
        if (destination == Destination.Home && state.conversation.isNotEmpty()) destination = Destination.Request
    }
    LaunchedEffect(businessState.error) {
        if (businessState.selection == null && destination in listOf(Destination.Home, Destination.Request))
            businessState.error?.let(callAiViewModel::showError)
    }
    LaunchedEffect(businessState.completedCampaign?.id) {
        businessState.completedCampaign?.let { completed ->
            callAiViewModel.campaignScheduled(completed.recipients.map { it.name })
            attachedGroup = null
            attachedTemplate = null
            business.acknowledgeScheduled()
            profile.refresh()
        }
    }
    fun requestContacts() {
        contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (contactsGranted) syncContacts() else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
    }
    fun newCall() {
        if (state.isProcessing || capture.recording) return
        focusRequest = false
        attachedGroup = null
        attachedTemplate = null
        callAiViewModel.newConversation()
        destination = Destination.Request
    }
    fun chooseContact(contact: ContactDraft) {
        if (state.missingFieldsDraft != null) {
            callAiViewModel.updateMissingField("contact_name", contact.name)
            callAiViewModel.updateMissingField("contact_number", contact.number)
        } else {
            val existing = state.prompt.trim()
            callAiViewModel.setPrompt("Call ${contact.name} at ${contact.number}" +
                if (existing.isEmpty()) " and " else ". $existing")
        }
        selectedContact = null
        contactPicker = false
        destination = Destination.Request
    }

    LaunchedEffect(Unit) {
        profile.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (contactsGranted) syncContacts() else callAiViewModel.setContacts(emptyList())
    }
    LaunchedEffect(callAiViewModel, haptics) {
        var previous = callAiViewModel.uiState.value
        callAiViewModel.uiState.collect { current ->
            // Observe transitions rather than recomposition or existing messages on launch.
            when {
                current.errorMessage != null && current.errorMessage != previous.errorMessage -> haptics.error()
                previous.isProcessing && !current.isProcessing && current.errorMessage == null -> haptics.success()
                current.conversation.lastOrNull()?.let { it.fromUser && it.id != previous.conversation.lastOrNull()?.id } == true &&
                    previous.isTranscribing -> haptics.tap()
                current.infoMessage != null && current.infoMessage != previous.infoMessage &&
                    !current.isProcessing && !previous.isProcessing -> haptics.success()
            }
            previous = current
        }
    }
    SideEffect {
        (context as? Activity)?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MADv2Theme(darkTheme = dark) {
        CompositionLocalProvider(LocalCallConfirmation provides CallConfirmationPreference(confirmationRequired) { required ->
            confirmationRequired = required
            preferences.edit().putBoolean(confirmationKey, required).apply()
        }) {
        fun navigate(target: Destination) {
            keyboard?.hide()
            focus.clearFocus()
            focusRequest = false
            if (target == Destination.Settings && destination != Destination.Settings) settingsPage = SettingsPage.Overview
            destination = target
            if (target in listOf(Destination.Upcoming, Destination.History, Destination.Calendar)) callAiViewModel.refreshCalls()
        }
        BackHandler(destination != Destination.Home || capture.recording) {
            when {
                capture.recording -> { capture.cancel(); callAiViewModel.setRecording(false) }
                destination == Destination.Settings && settingsPage != SettingsPage.Overview -> settingsPage = SettingsPage.Overview
                else -> destination = Destination.Home
            }
        }
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                if (!dark) Image(painterResource(R.drawable.landing_bg), null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alpha = .55f)
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                    if (destination != Destination.Home && destination != Destination.Request) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        PocketIconButton(Glyph.Back, "Back to home",
                            enabled = !capture.recording) {
                            if (destination == Destination.Settings && settingsPage != SettingsPage.Overview)
                                settingsPage = SettingsPage.Overview
                            else navigate(Destination.Home)
                        }
                        Image(painterResource(R.drawable.ic_voxie_mark), null, Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(when(destination) {
                            Destination.Home -> "Voxie"
                            Destination.Request -> "Voxie"
                            Destination.Upcoming -> "Scheduled calls"
                            Destination.History -> "Call history"
                            Destination.Calendar -> "Calendar"
                            Destination.Contacts -> "Contacts"
                            Destination.Groups -> "Groups"
                            Destination.Templates -> "Templates"
                            Destination.Settings -> if (settingsPage == SettingsPage.Overview) "Settings" else settingsPage.name
                            Destination.Credits -> "Credits"
                        }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        when(destination) {
                            Destination.Request -> PocketIconButton(Glyph.New, "New call", !state.isProcessing && !capture.recording, ::newCall)
                            Destination.Upcoming -> {
                                PocketIconButton(Glyph.Calendar, "Open calendar") { navigate(Destination.Calendar) }
                                PocketIconButton(Glyph.Refresh, "Refresh calls", !state.isRefreshingCalls, callAiViewModel::refreshCalls)
                            }
                            Destination.History, Destination.Calendar ->
                                PocketIconButton(Glyph.Refresh, "Refresh calls", !state.isRefreshingCalls, callAiViewModel::refreshCalls)
                            Destination.Contacts -> PocketIconButton(Glyph.Refresh, "Refresh contacts", !loadingContacts, ::requestContacts)
                            else -> Unit
                        }
                    }
                    VoxieTransition(destination,
                        modifier = Modifier.weight(1f).padding(bottom = bottomBarClearance)) { screen ->
                        screenStates.SaveableStateProvider(screen.name) {
                        when(screen) {
                            Destination.Home -> VoxieHomeScreen(state.copy(userName = account?.user?.fullName ?: state.userName), onMakeCall = ::newCall,
                                onContacts = { navigate(if (mode == "business") Destination.Groups else Destination.Contacts);
                                    if (mode != "business") requestContacts() }, onCall = { selectedCall = it },
                                business = mode == "business", credits = profileState.balance,
                                onGroups = { navigate(Destination.Groups) },
                                onTemplates = { navigate(Destination.Templates) },
                                onViewAll = { recent -> navigate(if (recent) Destination.History else Destination.Upcoming) }) {
                                if (businessState.busy) ThinkingIndicator("Finding your contacts")
                                if (mode == "business" && (attachedGroup != null || attachedTemplate != null)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        attachedGroup?.let { group -> TextButton(onClick = { attachedGroup = null }) { Text("${group.name}  x") } }
                                        attachedTemplate?.let { template -> TextButton(onClick = { attachedTemplate = null }) { Text("${template.name}  x") } }
                                    }
                                }
                                PocketComposer(state.prompt, callAiViewModel::setPrompt, state.isRecording, state.isTranscribing,
                                    state.isProcessing || businessState.busy, capture.samples, homeFocus,
                                    onPlus = { destination = Destination.Request; addPromptItem = true
                                        templatePickerOnly = true; business.loadTemplates() }, onMic = ::recordVoice,
                                    onCancel = { capture.cancel(); callAiViewModel.setRecording(false) }, onFinish = ::finishVoice,
                                    onSend = { keyboard?.hide(); focus.clearFocus(); submitHomePrompt() },
                                    homeStyle = true, onFocus = { focusRequest = true; destination = Destination.Request })
                            }
                            Destination.Request -> VoxieRequestScreen(state.copy(
                                isProcessing = state.isProcessing || businessState.busy,
                                processingMessage = businessState.processingMessage ?: state.processingMessage), capture.samples,
                                onPrompt = callAiViewModel::setPrompt,
                                onSubmit = { keyboard?.hide(); focus.clearFocus(); focusRequest = false
                                    if (state.missingFieldsDraft == null && mode == "business") submitHomePrompt()
                                    else callAiViewModel.submitPrompt() },
                                onConfirm = callAiViewModel::confirmSchedule,
                                onPreviewAudio = callAiViewModel::previewAudio,
                                onEdit = callAiViewModel::editConfirmation,
                                onField = callAiViewModel::updateMissingField,
                                onPlus = { keyboard?.hide(); addPromptItem = true; templatePickerOnly = true; business.loadTemplates() },
                                onMic = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                                        startRecording()
                                    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onCancelRecording = { capture.cancel(); callAiViewModel.setRecording(false) },
                                onFinishRecording = {
                                    val audio = capture.finish()
                                    callAiViewModel.setRecording(false)
                                    if (audio != null) callAiViewModel.transcribeAudio(audio, onReady = {
                                        if (mode == "business" && state.missingFieldsDraft == null) submitHomePrompt(it)
                                        else callAiViewModel.submitPrompt()
                                    })
                                    else callAiViewModel.showError("That recording was too short. Please try again.")
                                },
                                onSearchContacts = { contactPicker = true },
                                onViewCall = { id ->
                                    selectedCall = (state.upcomingCalls + state.historyCalls).find { it.id == id }
                                    if (selectedCall == null) navigate(Destination.Upcoming)
                                },
                                onDismissError = callAiViewModel::clearMessages,
                                mode = mode,
                                timezone = profileState.timezone,
                                focusComposer = focusRequest,
                                templateName = attachedTemplate?.name,
                                onClearTemplate = { attachedTemplate = null },
                            )
                            Destination.Upcoming -> CallsScreen(state.upcomingCalls, state.isRefreshingCalls, state.errorMessage,
                                onCall = { selectedCall = it }, onRefresh = callAiViewModel::refreshCalls,
                                onHistory = { navigate(if (it) Destination.History else Destination.Upcoming) })
                            Destination.History -> CallsScreen(state.historyCalls, state.isRefreshingCalls, state.errorMessage,
                                history = true, onCall = { selectedCall = it }, onRefresh = callAiViewModel::refreshCalls,
                                onHistory = { navigate(if (it) Destination.History else Destination.Upcoming) })
                            Destination.Calendar -> CallCalendar(state, Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                onSchedule = { day ->
                                    val zone = ZoneId.of(profileState.timezone)
                                    val time = if (day == LocalDate.now(zone)) LocalTime.now(zone).plusMinutes(10) else LocalTime.of(9, 0)
                                    callAiViewModel.newConversation()
                                    callAiViewModel.startManualCall(day.atTime(time).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                    destination = Destination.Request
                                }) {
                                CallListRow(it, onClick = { selectedCall = it })
                            }
                            Destination.Contacts -> ContactsScreen(state.contacts, contactsGranted, loadingContacts,
                                onPermission = ::requestContacts, onContact = { selectedContact = it })
                            Destination.Templates -> TemplatesScreen(business, onUse = { template ->
                                callAiViewModel.newConversation()
                                callAiViewModel.setPrompt("Use \"${template.name}\" template to call ")
                                navigate(Destination.Request)
                            })
                            Destination.Groups -> BusinessScreen(business,
                                timezone = profileState.timezone,
                                initialGroupId = pendingCampaignGroup, initialPrompt = pendingCampaignPrompt,
                                onInitialConsumed = { pendingCampaignGroup = null; pendingCampaignPrompt = "" },
                                onIndividual = { contact ->
                                    callAiViewModel.newConversation()
                                    callAiViewModel.setPrompt("Call ${contact.name} at ${contact.number} and say ")
                                    navigate(Destination.Request)
                                }, onSheet = { sheetGroup = it })
                            Destination.Settings -> SettingsScreen(state.copy(userName = account?.user?.fullName ?: state.userName), theme,
                                onTheme = { theme = it; preferences.edit().putString("theme", it).apply() },
                                onSignOut = onSignOut,
                                onBackend = onBackendChanged,
                                onPermissions = {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                }, mode = mode, profile = profileState, onSaveProfile = profile::save,
                                page = settingsPage, onPage = { settingsPage = it })
                            Destination.Credits -> CreditsScreen(onBuy = onCredits, balance = profileState.balance,
                                plan = profileState.plan)
                        }
                        }
                    }
                }
                Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                    VoxieBottomBar(selected = when (destination) {
                        Destination.Home, Destination.Request -> "Home"
                        Destination.Settings -> "Profile"
                        Destination.Credits -> "Credits"
                        Destination.Contacts -> if (mode == "business") "" else "Contacts"
                        Destination.Templates -> ""
                        Destination.Groups -> "Groups"
                        else -> "Calls"
                    }, enabled = !state.isProcessing && !capture.recording,
                        onHome = { navigate(Destination.Home) }, onCalls = { navigate(Destination.Upcoming) },
                        onCredits = { navigate(Destination.Credits) }, onProfile = { navigate(Destination.Settings) },
                        onGroups = { if (mode == "business") navigate(Destination.Groups)
                        else { navigate(Destination.Contacts); requestContacts() } }, business = mode == "business")
                }
                }
            }
        businessState.selection?.let { selection ->
            CampaignComposer(selection, businessState.draft, businessState.busy, businessState.error,
                businessState.selectionPrompt, profileState.timezone,
                chooseRecipientsFirst = businessState.chooseRecipientsFirst,
                onDismiss = business::clearSelection,
                onPreview = { prompt, recipients, templateId -> business.preview(selection.id, prompt, recipients, templateId ?: attachedTemplate?.id) },
                onEdit = business::clearDraft, onConfirm = business::schedule,
                templates = businessState.templates, onLoadTemplates = business::loadTemplates)
        }
        if (addPromptItem) PromptAttachmentPicker(businessState.groups, businessState.templates, businessState.busy,
            templatesOnly = templatePickerOnly,
            onDismiss = { addPromptItem = false },
            onGroup = { attachedGroup = it; addPromptItem = false },
            onTemplate = { attachedTemplate = it; addPromptItem = false })
        if (contactPicker) ModalBottomSheet(onDismissRequest = { contactPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(.8f)) {
                ContactsScreen(state.contacts, contactsGranted, loadingContacts, ::requestContacts, ::chooseContact)
            }
        }
        sheetGroup?.let { group -> GoogleSheetImport(group, onDismiss = { sheetGroup = null },
            onRows = { name, rows -> business.sheet(name, rows) }) }
        selectedContact?.let { contact ->
            ModalBottomSheet(onDismissRequest = { selectedContact = null }, containerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(contact.name, style = MaterialTheme.typography.titleLarge)
                    Text(contact.number, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = hapticClick { chooseContact(contact) }, enabled = !state.isProcessing) { Text("Ask PocketCall to call") }
                }
            }
        }
        selectedCall?.let { original ->
            val call = (state.upcomingCalls + state.historyCalls).find { it.id == original.id } ?: original
            CallDetailSheet(call, onDismiss = { selectedCall = null },
                onCancel = { callAiViewModel.cancelCall(call.id); selectedCall = null },
                onCallAgain = {
                    newCall()
                    callAiViewModel.setPrompt("Call ${call.contactName} at ${call.contactNumber} and say: ${call.message}")
                    selectedCall = null
                }, canStartNew = !state.isProcessing && !capture.recording)
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptAttachmentPicker(groups: List<BusinessGroup>, templates: List<MessageTemplate>, busy: Boolean,
    templatesOnly: Boolean = false, onDismiss: () -> Unit, onGroup: (BusinessGroup) -> Unit, onTemplate: (MessageTemplate) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (templatesOnly) "Choose a template" else "Add to your call", style = MaterialTheme.typography.titleLarge)
            Text(if (templatesOnly) "Use a saved message for this call." else "Pick a saved group or message template.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!templatesOnly) {
                Text("Groups", style = MaterialTheme.typography.titleMedium)
                groups.forEach { group ->
                OutlinedButton(onClick = { onGroup(group) }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)) { Text(group.name) }
                }
                if (groups.isEmpty() && !busy) Text("No saved groups yet.")
            }
            Text("Templates", style = MaterialTheme.typography.titleMedium)
            templates.forEach { template ->
                OutlinedButton(onClick = { onTemplate(template) }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)) { Text(template.name) }
            }
            if (templates.isEmpty() && !busy) Text("No saved templates yet.")
        }
    }
}

// Kept as a small calendar entry point for existing callers and instrumentation.
@Composable
internal fun ScheduledTab(uiState: CallAiUiState, onRefreshCalls: () -> Unit, onCancelCall: (String) -> Unit) {
    var selected by remember { mutableStateOf<ScheduledCallUiModel?>(null) }
    CallCalendar(uiState, Modifier.fillMaxSize().padding(16.dp)) { call ->
        CallListRow(call, onClick = { selected = call })
    }
    selected?.let { call ->
        CallDetailSheet(call, { selected = null }, { onCancelCall(call.id); selected = null }, {}, false)
    }
}
