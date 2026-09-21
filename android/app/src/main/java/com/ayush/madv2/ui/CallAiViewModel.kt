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
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private const val EMULATOR_BASE_URL = "http://10.0.2.2:5000/"
private const val DEVICE_BASE_URL = "http://192.168.29.220:5000/"

private fun defaultBaseUrl(): String {
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
)

data class ScheduledCallUiModel(
    val id: String,
    val contactName: String,
    val contactNumber: String,
    val timeLabel: String,
    val message: String,
    val status: String,
    val canCancel: Boolean,
)

data class CallAiUiState(
    val selectedTab: AppTab = AppTab.Home,
    val showSettings: Boolean = false,
    val backendBaseUrl: String = defaultBaseUrl(),
    val userId: String = "user_001",
    val userName: String = "Ayush",
    val prompt: String = "",
    val contacts: List<ContactDraft> = listOf(ContactDraft(1L, "Ayush Waman", "+918879279251")),
    val isProcessing: Boolean = false,
    val isRefreshingCalls: Boolean = false,
    val feedbackJson: String = "",
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val missingFieldsDraft: MissingFieldsDraft? = null,
    val confirmationDraft: ConfirmationDraft? = null,
    val upcomingCalls: List<ScheduledCallUiModel> = emptyList(),
    val historyCalls: List<ScheduledCallUiModel> = emptyList(),
)

class CallAiViewModel(
    private val repository: CallAiRepository = CallAiRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(CallAiUiState())
    val uiState: StateFlow<CallAiUiState> = _uiState.asStateFlow()

    private var nextContactId = 2L

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
        if (state.prompt.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a prompt first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    errorMessage = null,
                    infoMessage = null,
                    confirmationDraft = null,
                )
            }

            runCatching {
                val response = repository.scheduleCall(
                    baseUrl = state.backendBaseUrl.trim(),
                    request = buildScheduleRequest(confirmed = false),
                )
                handleScheduleResponse(response)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = error.asUserMessage(),
                    )
                }
            }
        }
    }

    fun confirmSchedule() {
        val state = uiState.value
        if (state.confirmationDraft?.agentResult == null) {
            _uiState.update { it.copy(errorMessage = "Nothing is ready to confirm yet.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
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
                    ),
                )
                handleScheduleResponse(response)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = error.asUserMessage(),
                    )
                }
            }
        }
    }

    fun refreshCalls() {
        val state = uiState.value
        if (state.userId.isBlank()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingCalls = true, errorMessage = null) }

            runCatching {
                repository.getCalls(
                    baseUrl = state.backendBaseUrl.trim(),
                    userId = state.userId.trim(),
                )
            }.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isRefreshingCalls = false,
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
    ): ScheduleCallRequestDto {
        val state = uiState.value
        val missingDraft = state.missingFieldsDraft

        return ScheduleCallRequestDto(
            userId = state.userId.trim(),
            userName = state.userName.trim(),
            rawPrompt = state.prompt.trim(),
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
            agentResult = agentResultOverride,
        )
    }

    private fun handleScheduleResponse(response: com.ayush.madv2.network.ScheduleCallResponseDto) {
        val currentState = uiState.value
        val prettyJson = repository.toPrettyJson(response)

        when (response.status) {
            "needs_info" -> {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
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
                        feedbackJson = prettyJson,
                        infoMessage = "Call scheduled successfully.",
                        missingFieldsDraft = null,
                        confirmationDraft = null,
                        selectedTab = AppTab.Scheduled,
                        showSettings = false,
                    )
                }
                refreshCalls()
            }

            else -> {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
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
        val dateLabel = runCatching {
            OffsetDateTime.parse(job.scheduledTime).format(DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a"))
        }.getOrElse { job.scheduledTime }

        return ScheduledCallUiModel(
            id = job.id,
            contactName = job.contactName,
            contactNumber = job.contactNumber,
            timeLabel = dateLabel,
            message = job.rephrasedMessage ?: job.originalMessage,
            status = job.status.replaceFirstChar { it.uppercase() },
            canCancel = job.status == "pending",
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

    private fun Throwable.asUserMessage(): String {
        return when (this) {
            is IOException -> "Could not reach the backend. Check the emulator URL and make sure Flask is running."
            is IllegalStateException -> message ?: "The backend returned an unexpected response."
            else -> message ?: "Something went wrong while talking to the backend."
        }
    }
}
