package com.ayush.madv2.ui

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayush.madv2.data.CallAiRepository
import com.ayush.madv2.network.AgentResultDto
import com.ayush.madv2.network.CallJobDto
import com.ayush.madv2.network.ContactDto
import com.ayush.madv2.network.ScheduleCallRequestDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.File

private const val EMULATOR_BASE_URL = "http://10.0.2.2:5000/"
private const val DEVICE_BASE_URL = "http://10.125.38.233:5000/"

internal fun defaultBaseUrl(): String {
    return if (isProbablyRunningOnEmulator()) EMULATOR_BASE_URL else DEVICE_BASE_URL
}

private fun isProbablyRunningOnEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = Build.BRAND.lowercase()
    val device = Build.DEVICE.lowercase()
    val product = Build.PRODUCT.lowercase()

    return fingerprint.contains("generic") ||
        fingerprint.contains("emulator") ||
        fingerprint.contains("sdk_gphone") ||
        model.contains("emulator") ||
        model.contains("android sdk built for") ||
        manufacturer.contains("genymotion") ||
        (brand.contains("generic") && device.contains("generic")) ||
        product.contains("sdk") ||
        product.contains("emulator")
}

data class ContactDraft(
    val id: Long,
    val name: String,
    val number: String,
)

data class MissingFieldsDraft(
    val fields: List<String>,
    val contactName: String,
    val contactNumber: String,
    val message: String,
    val scheduledTime: String,
)

data class ConfirmationDraft(
    val contactName: String,
    val contactNumber: String,
    val messagePreview: String,
    val scheduledTime: String?,
    val agentResult: AgentResultDto?,
    val audioPreviewId: String? = null,
    val audioPreviewUrl: String? = null,
    val confirmationKey: String = java.util.UUID.randomUUID().toString(),
)

data class ScheduledCallUiModel(
    val id: String,
    val contactName: String,
    val contactNumber: String,
    val timeLabel: String,
    val message: String,
    val status: String,
    val canCancel: Boolean,
    val scheduledTime: String = "",
)

data class CallAiUiState(
    val selectedTab: AppTab = AppTab.Home,
    val showSettings: Boolean = false,
    val backendBaseUrl: String = defaultBaseUrl(),
    val userId: String = "",
    val userName: String = "",
    val prompt: String = "",
    val contacts: List<ContactDraft> = emptyList(),
    val isProcessing: Boolean = false,
    val processingMessage: String? = null,
    val isRecording: Boolean = false,
    val isRefreshingCalls: Boolean = false,
    val feedbackJson: String = "",
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val missingFieldsDraft: MissingFieldsDraft? = null,
    val confirmationDraft: ConfirmationDraft? = null,
    val upcomingCalls: List<ScheduledCallUiModel> = emptyList(),
    val historyCalls: List<ScheduledCallUiModel> = emptyList(),
    val conversation: List<ChatEntry> = emptyList(),
    val conversationId: Int = 0,
    val isTranscribing: Boolean = false,
)

data class ChatEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val fromUser: Boolean = false,
    val callId: String? = null,
)

class CallAiViewModel(
    private val repository: CallAiRepository = CallAiRepository(),
    initialState: CallAiUiState = CallAiUiState(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<CallAiUiState> = _uiState.asStateFlow()

    private var nextContactId = 2L
    private var activePrompt = ""
    private var refinementResult: AgentResultDto? = null

    fun startManualCall(scheduledTime: String = "") {
        refinementResult = null
        if (uiState.value.isProcessing || uiState.value.isRecording) return
        activePrompt = "Schedule a call with the details I provide."
        _uiState.update { it.copy(prompt = "", confirmationDraft = null,
            conversation = it.conversation + ChatEntry(text = "I'd like to schedule a call.", fromUser = true),
            missingFieldsDraft = MissingFieldsDraft(
                listOf("contact_name", "contact_number", "message", "scheduled_time"), "", "", "", scheduledTime),
            errorMessage = null, infoMessage = null) }
    }

    fun newConversation() {
        refinementResult = null
        if (uiState.value.isProcessing || uiState.value.isRecording) return
        activePrompt = ""
        _uiState.update { it.copy(prompt = "", conversation = emptyList(),
            conversationId = it.conversationId + 1, missingFieldsDraft = null,
            confirmationDraft = null, errorMessage = null, infoMessage = null,
            selectedTab = AppTab.Home, showSettings = false) }
    }

    fun beginNewRequest() {
        _uiState.update { it.copy(conversation = emptyList(), missingFieldsDraft = null,
            confirmationDraft = null, errorMessage = null, infoMessage = null) }
    }

    fun editConfirmation() {
        if (uiState.value.isProcessing) return
        _uiState.update { state ->
            val draft = state.confirmationDraft ?: return@update state
            state.copy(confirmationDraft = null, missingFieldsDraft = MissingFieldsDraft(
                fields = listOf("contact_name", "contact_number", "message", "scheduled_time"),
                contactName = draft.contactName, contactNumber = draft.contactNumber,
                message = draft.agentResult?.message.orEmpty(), scheduledTime = draft.scheduledTime.orEmpty(),
            ))
        }
    }

    init {
        refreshCalls()
    }

    fun selectTab(tab: AppTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                showSettings = if (tab == AppTab.Home) it.showSettings else false,
                errorMessage = null,
            )
        }
        if (tab == AppTab.Scheduled) {
            refreshCalls()
        }
    }

    fun toggleSettings() {
        _uiState.update { it.copy(showSettings = !it.showSettings) }
    }

    fun openHomeSettings() {
        _uiState.update {
            it.copy(
                selectedTab = AppTab.Home,
                showSettings = true,
            )
        }
    }

    fun setPrompt(value: String) {
        _uiState.update { it.copy(prompt = value) }
    }

    fun setUserName(value: String) {
        _uiState.update { it.copy(userName = value) }
    }

    fun setUserId(value: String) {
        _uiState.update { it.copy(userId = value) }
    }

    fun setBackendBaseUrl(value: String) {
        _uiState.update { it.copy(backendBaseUrl = value) }
    }

    fun setContacts(contacts: List<ContactDraft>) {
        _uiState.update { state ->
            state.copy(
                contacts = contacts,
                infoMessage = if (contacts.isEmpty()) "No phone contacts were found." else "Synced ${contacts.size} contacts from your phone.",
                errorMessage = null,
            )
        }
    }

    fun setRecording(isRecording: Boolean) {
        _uiState.update { it.copy(isRecording = isRecording, errorMessage = null, infoMessage = null) }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message, infoMessage = null, isProcessing = false, processingMessage = null, isRecording = false) }
    }

    fun addContact() {
        _uiState.update {
            it.copy(
                contacts = it.contacts + ContactDraft(
                    id = nextContactId++,
                    name = "",
                    number = "",
                )
            )
        }
    }

    fun updateContactName(contactId: Long, value: String) {
        _uiState.update {
            it.copy(
                contacts = it.contacts.map { contact ->
                    if (contact.id == contactId) contact.copy(name = value) else contact
                }
            )
        }
    }

    fun updateContactNumber(contactId: Long, value: String) {
        _uiState.update {
            it.copy(
                contacts = it.contacts.map { contact ->
                    if (contact.id == contactId) contact.copy(number = value) else contact
                }
            )
        }
    }

    fun removeContact(contactId: Long) {
        _uiState.update { state ->
            val remainingContacts = state.contacts.filterNot { it.id == contactId }
            state.copy(contacts = if (remainingContacts.isEmpty()) listOf(ContactDraft(nextContactId++, "", "")) else remainingContacts)
        }
    }

    fun useQuickPrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    fun updateMissingField(fieldName: String, value: String) {
        _uiState.update { state ->
            val draft = state.missingFieldsDraft ?: return@update state
            val updatedDraft = when (fieldName) {
                "contact_name" -> draft.copy(contactName = value)
                "contact_number" -> draft.copy(contactNumber = value)
                "message" -> draft.copy(message = value)
                "scheduled_time" -> draft.copy(scheduledTime = value)
                else -> draft
            }
            state.copy(missingFieldsDraft = updatedDraft)
        }
    }

    fun submitPrompt() {
        val state = uiState.value
        if (state.isProcessing || state.isRecording) return
        val isNewPrompt = state.prompt.isNotBlank()
        if (!isNewPrompt && activePrompt.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a prompt first.") }
            return
        }

        if (isNewPrompt) {
            activePrompt = if (state.missingFieldsDraft != null && activePrompt.isNotBlank())
                "$activePrompt ${state.prompt.trim()}" else state.prompt.trim()
            refinementResult = null
        }
        val request = buildScheduleRequest(confirmed = false, includeRefinements = !isNewPrompt).let {
            if (isNewPrompt) it.copy(chosenTime = null, confirmedNumber = null,
                confirmedContactName = null, confirmedMessage = null) else it
        }
        _uiState.update { it.copy(prompt = "", missingFieldsDraft = if (isNewPrompt) null else it.missingFieldsDraft,
            conversation = if (isNewPrompt) it.conversation + ChatEntry(text = activePrompt, fromUser = true) else it.conversation) }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Matching the contact, finding the time, and preparing the spoken message.",
                    errorMessage = null,
                    infoMessage = null,
                    confirmationDraft = null,
                )
            }

            runCatching {
                val response = repository.scheduleCall(
                    baseUrl = state.backendBaseUrl.trim(),
                    request = request,
                )
                handleScheduleResponse(response)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingMessage = null,
                        errorMessage = error.asUserMessage(),
                    )
                }
            }
        }
    }

    fun confirmSchedule() {
        val state = uiState.value
        if (state.isProcessing || state.isRecording) return
        if (state.confirmationDraft?.agentResult == null) {
            _uiState.update { it.copy(errorMessage = "Nothing is ready to confirm yet.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Preparing the final call details before scheduling.",
                    errorMessage = null,
                    infoMessage = null,
                )
            }

            runCatching {
                val response = repository.scheduleCall(
                    baseUrl = state.backendBaseUrl.trim(),
                    request = buildScheduleRequest(
                        confirmed = true,
                        agentResultOverride = state.confirmationDraft.agentResult,
                    ).copy(
                        chosenTime = state.confirmationDraft.scheduledTime,
                        confirmedNumber = state.confirmationDraft.contactNumber,
                        confirmedContactName = state.confirmationDraft.contactName,
                        audioPreviewId = state.confirmationDraft.audioPreviewId,
                    ),
                )
                handleScheduleResponse(response)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingMessage = null,
                        errorMessage = error.asUserMessage(),
                    )
                }
            }
        }
    }

    fun previewAudio() {
        val state = uiState.value
        val draft = state.confirmationDraft ?: return
        if (state.isProcessing || draft.audioPreviewUrl != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, processingMessage = "Generating audio preview") }
            runCatching { repository.previewAudio(state.backendBaseUrl.trim(), draft.messagePreview) }
                .onSuccess { preview -> _uiState.update { current -> current.copy(isProcessing = false, processingMessage = null,
                    confirmationDraft = current.confirmationDraft?.copy(audioPreviewId = preview.previewId,
                        audioPreviewUrl = preview.audioUrl)) } }
                .onFailure { error -> _uiState.update { it.copy(isProcessing = false, processingMessage = null,
                    errorMessage = error.asUserMessage()) } }
        }
    }

    fun transcribeAudio(file: File, onReady: ((String) -> Unit)? = null) {
        val state = uiState.value

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    isTranscribing = true,
                    processingMessage = "Transcribing your voice note with Whisper.",
                    isRecording = false,
                    errorMessage = null,
                    infoMessage = null,
                )
            }

            runCatching {
                repository.transcribeAudio(state.backendBaseUrl.trim(), file)
            }.onSuccess { response ->
                val transcript = response.text.trim()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        isTranscribing = false,
                        processingMessage = null,
                        prompt = mergePromptWithTranscript(it.prompt, transcript),
                        infoMessage = "Voice prompt added.",
                        feedbackJson = repository.toPrettyJson(response),
                    )
                }
                if (transcript.isNotBlank()) {
                    if (onReady != null) onReady(uiState.value.prompt) else submitPrompt()
                }
                else showError("I couldn't hear a message. Please record again.")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        isTranscribing = false,
                        processingMessage = null,
                        errorMessage = error.asUserMessage(),
                    )
                }
            }

            runCatching { file.delete() }
        }
    }

    fun refreshCalls() {
        val state = uiState.value
        if (state.userId.isBlank() || state.isRefreshingCalls) {
            return
        }
        _uiState.update { it.copy(isRefreshingCalls = true, errorMessage = null) }
        viewModelScope.launch {

            runCatching {
                repository.getCalls(
                    baseUrl = state.backendBaseUrl.trim(),
                    userId = state.userId.trim(),
                )
            }.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isRefreshingCalls = false,
                        processingMessage = if (it.isProcessing) it.processingMessage else null,
                        upcomingCalls = response.upcoming.map(::toUiModel),
                        historyCalls = response.past.map(::toUiModel),
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRefreshingCalls = false,
                        errorMessage = error.asUserMessage(),
                    )
                }
            }
        }
    }

    fun campaignScheduled(recipientNames: List<String>) {
        val recipients = recipientNames.joinToString(", ").ifBlank { "your selected contacts" }
        _uiState.update { it.copy(prompt = "", confirmationDraft = null, missingFieldsDraft = null,
            errorMessage = null, infoMessage = "Calls scheduled successfully.",
            conversation = it.conversation + ChatEntry(text = "Scheduled calls for $recipients.")) }
        refreshCalls()
    }

    fun cancelCall(jobId: String) {
        val state = uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

            runCatching {
                repository.cancelCall(state.backendBaseUrl.trim(), jobId)
            }.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        infoMessage = response.message ?: "Call cancelled.",
                        feedbackJson = repository.toPrettyJson(response),
                    )
                }
                refreshCalls()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.asUserMessage()) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private fun buildScheduleRequest(
        confirmed: Boolean,
        agentResultOverride: AgentResultDto? = null,
        includeRefinements: Boolean = true,
    ): ScheduleCallRequestDto {
        val state = uiState.value
        val missingDraft = state.missingFieldsDraft.takeIf { includeRefinements }

        return ScheduleCallRequestDto(
            userId = state.userId.trim(),
            userName = state.userName.trim(),
            rawPrompt = activePrompt.ifBlank { state.prompt.trim() },
            contacts = state.contacts.map {
                ContactDto(
                    name = it.name.trim(),
                    number = it.number.trim(),
                )
            },
            chosenTime = normalizeChosenTimeInput(missingDraft?.scheduledTime),
            confirmedNumber = missingDraft?.contactNumber?.takeIf { "contact_number" in (missingDraft.fields) }?.trim()?.ifEmpty { null },
            confirmedContactName = missingDraft?.contactName?.takeIf { "contact_name" in (missingDraft.fields) }?.trim()?.ifEmpty { null },
            confirmedMessage = missingDraft?.message?.takeIf { "message" in (missingDraft.fields) }?.trim()?.ifEmpty { null },
            confirmed = confirmed,
            agentResult = agentResultOverride ?: refinementResult.takeIf { includeRefinements && missingDraft != null },
        )
    }

    private fun handleScheduleResponse(response: com.ayush.madv2.network.ScheduleCallResponseDto) {
        val currentState = uiState.value
        val prettyJson = repository.toPrettyJson(response)

        when (response.status) {
            "needs_info" -> {
                refinementResult = response.agentResult
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingMessage = null,
                        feedbackJson = prettyJson,
                        infoMessage = response.message,
                        missingFieldsDraft = MissingFieldsDraft(
                            fields = response.missingFields,
                            contactName = response.contactName,
                            contactNumber = response.contactNumber,
                            message = response.agentResult?.message.orEmpty(),
                            scheduledTime = response.agentResult?.scheduledTime.orEmpty(),
                        ),
                        confirmationDraft = null,
                    )
                }
            }

            "needs_confirmation" -> {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingMessage = null,
                        feedbackJson = prettyJson,
                        infoMessage = response.message,
                        missingFieldsDraft = null,
                        confirmationDraft = ConfirmationDraft(
                            contactName = response.contactName,
                            contactNumber = response.contactNumber,
                            messagePreview = response.messagePreview,
                            scheduledTime = response.scheduledTime,
                            agentResult = response.agentResult,
                        ),
                    )
                }
            }

            "scheduled" -> {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingMessage = null,
                        feedbackJson = prettyJson,
                        infoMessage = "Call scheduled successfully.",
                        missingFieldsDraft = null,
                        confirmationDraft = null,
                        conversation = it.conversation + ChatEntry(
                            text = "Scheduled. I'll call ${response.contactName} at ${formatCallTime(response.callTime ?: response.scheduledTime)}.",
                            callId = response.jobId,
                        ),
                        selectedTab = AppTab.Home,
                        showSettings = false,
                    )
                }
                refreshCalls()
            }

            else -> {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingMessage = null,
                        feedbackJson = prettyJson,
                        errorMessage = response.message ?: "Unexpected response from the backend.",
                    )
                }
            }
        }

        if (response.status == "scheduled" && currentState.prompt.isNotBlank()) {
            _uiState.update { it.copy(prompt = "") }
        }
    }

    private fun toUiModel(job: CallJobDto): ScheduledCallUiModel {
        val dateLabel = formatCallTime(job.scheduledTime)

        return ScheduledCallUiModel(
            id = job.id,
            contactName = job.contactName,
            contactNumber = job.contactNumber,
            timeLabel = dateLabel,
            message = job.rephrasedMessage ?: job.originalMessage,
            status = job.status.replaceFirstChar { it.uppercase() },
            canCancel = job.status == "pending",
            scheduledTime = job.scheduledTime,
        )
    }

    private fun normalizeChosenTimeInput(value: String?): String? {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) {
            return null
        }
        return when {
            Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}""").matches(normalized) -> "${normalized}:00+05:30"
            Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}""").matches(normalized) -> "$normalized+05:30"
            else -> normalized
        }
    }

    private fun mergePromptWithTranscript(existingPrompt: String, transcript: String): String {
        val cleanTranscript = transcript.trim()
        if (cleanTranscript.isBlank()) {
            return existingPrompt
        }
        val cleanPrompt = existingPrompt.trim()
        return if (cleanPrompt.isBlank()) cleanTranscript else "$cleanPrompt $cleanTranscript"
    }

    private fun Throwable.asUserMessage(): String {
        return when (this) {
            is IOException -> "Could not reach the backend. Check the phone or emulator backend URL and make sure Flask is running."
            is IllegalStateException -> message ?: "The backend returned an unexpected response."
            else -> "Something went wrong. Please try again."
        }
    }
}
