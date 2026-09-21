package com.ayush.madv2.network

import com.google.gson.annotations.SerializedName

data class ContactDto(
    @SerializedName("name")
    val name: String,
    @SerializedName("number")
    val number: String,
)

data class AgentResultDto(
    @SerializedName("contact_name")
    val contactName: String = "",
    @SerializedName("contact_number")
    val contactNumber: String = "",
    @SerializedName("message")
    val message: String = "",
    @SerializedName("rephrased_message")
    val rephrasedMessage: String = "",
    @SerializedName("scheduled_time")
    val scheduledTime: String? = null,
    @SerializedName("time_extracted")
    val timeExtracted: Boolean = false,
    @SerializedName("missing_fields")
    val missingFields: List<String> = emptyList(),
)

data class ScheduleCallRequestDto(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("user_name")
    val userName: String,
    @SerializedName("raw_prompt")
    val rawPrompt: String,
    @SerializedName("contacts")
    val contacts: List<ContactDto>,
    @SerializedName("chosen_time")
    val chosenTime: String? = null,
    @SerializedName("confirmed_number")
    val confirmedNumber: String? = null,
    @SerializedName("confirmed_contact_name")
    val confirmedContactName: String? = null,
    @SerializedName("confirmed_message")
    val confirmedMessage: String? = null,
    @SerializedName("confirmed")
    val confirmed: Boolean = false,
    @SerializedName("agent_result")
    val agentResult: AgentResultDto? = null,
)

data class ScheduleCallResponseDto(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("job_id")
    val jobId: String? = null,
    @SerializedName("call_time")
    val callTime: String? = null,
    @SerializedName("scheduled_time")
    val scheduledTime: String? = null,
    @SerializedName("contact_name")
    val contactName: String = "",
    @SerializedName("contact_number")
    val contactNumber: String = "",
    @SerializedName("message_preview")
    val messagePreview: String = "",
    @SerializedName("audio_url")
    val audioUrl: String? = null,
    @SerializedName("time_extracted")
    val timeExtracted: Boolean = false,
    @SerializedName("missing_fields")
    val missingFields: List<String> = emptyList(),
    @SerializedName("agent_result")
    val agentResult: AgentResultDto? = null,
)

data class CallJobDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("contact_name")
    val contactName: String,
    @SerializedName("contact_number")
    val contactNumber: String,
    @SerializedName("original_message")
    val originalMessage: String,
    @SerializedName("rephrased_message")
    val rephrasedMessage: String? = null,
    @SerializedName("scheduled_time")
    val scheduledTime: String,
    @SerializedName("status")
    val status: String,
    @SerializedName("audio_url")
    val audioUrl: String? = null,
)

data class CallsResponseDto(
    @SerializedName("upcoming")
    val upcoming: List<CallJobDto> = emptyList(),
    @SerializedName("past")
    val past: List<CallJobDto> = emptyList(),
)

data class BasicResponseDto(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String? = null,
)
