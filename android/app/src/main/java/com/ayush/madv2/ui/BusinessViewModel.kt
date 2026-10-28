package com.ayush.madv2.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayush.madv2.data.AccountSession
import com.ayush.madv2.network.NetworkModule
import com.ayush.madv2.data.authBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.*

data class BusinessContact(val id: String, val name: String, val number: String, val consent_status: String, val group_name: String? = null)
data class RecipientResolution(val status: String, val selection: BusinessGroup? = null)
data class BusinessGroup(val id: String, val name: String, val contacts: List<BusinessContact>)
data class GroupsResult(val groups: List<BusinessGroup>)
data class GroupResult(val group: BusinessGroup)
data class ContactResult(val contact: BusinessContact)
data class ImportRow(val row: Int, val name: String, val phone: String, val raw_number: String?, val status: String)
data class ImportResult(val status: String, val import_id: String? = null, val group_name: String = "",
                        val headers: List<String> = emptyList(), val contacts: List<ImportRow> = emptyList(),
                        val found: Int = 0, val duplicates: Int = 0, val invalid: Int = 0)
data class CampaignRecipient(val id: String, val name: String, val number: String, val scheduled_time: String? = null)
data class MessageTemplate(val id: String, val name: String, val message: String)
data class TemplatesResult(val templates: List<MessageTemplate>)
data class TemplateResult(val template: MessageTemplate)
data class CampaignDetail(val id: String, val title: String, val group_id: String, val message: String,
                          val audio_url: String, val scheduled_time: String, val timezone: String,
                          val status: String, val recipients: List<CampaignRecipient>, val eligible_count: Int,
                          val estimated_credits: Int, val balance: Int, val estimated_provider_cost_paise: Int?,
                          val template_name: String? = null)
data class CampaignResult(val status: String? = null, val campaign: CampaignDetail? = null)
data class CampaignRow(val id: String, val title: String, val scheduled_time: String, val status: String,
                       val total: Int, val results: Map<String, Int>)
data class CampaignsResult(val campaigns: List<CampaignRow>)

interface BusinessApi {
    @POST("campaigns/resolve") suspend fun resolve(@Body body: Map<String, String>): Response<RecipientResolution>
    @GET("templates") suspend fun templates(): Response<TemplatesResult>
    @POST("templates") suspend fun createTemplate(@Body body: Map<String, String>): Response<TemplateResult>
    @PATCH("templates/{id}") suspend fun editTemplate(@Path("id") id: String, @Body body: Map<String, String>): Response<TemplateResult>
    @DELETE("templates/{id}") suspend fun deleteTemplate(@Path("id") id: String): Response<Map<String, String>>
    @GET("groups") suspend fun groups(): Response<GroupsResult>
    @POST("groups") suspend fun create(@Body body: Map<String, String>): Response<GroupResult>
    @PATCH("groups/{id}") suspend fun rename(@Path("id") id: String, @Body body: Map<String, String>): Response<GroupResult>
    @DELETE("groups/{id}") suspend fun delete(@Path("id") id: String): Response<Map<String, String>>
    @POST("groups/{id}/contacts") suspend fun add(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any>): Response<ContactResult>
    @PATCH("groups/{id}/contacts/{contact}") suspend fun edit(@Path("id") id: String,
        @Path("contact") contact: String, @Body body: Map<String, @JvmSuppressWildcards Any>): Response<ContactResult>
    @DELETE("groups/{id}/contacts/{contact}") suspend fun remove(@Path("id") id: String,
        @Path("contact") contact: String): Response<Map<String, String>>
    @Multipart @POST("groups/imports/preview") suspend fun csv(@Part file: MultipartBody.Part,
        @Part("group_name") name: okhttp3.RequestBody,
        @Part("name_column") nameColumn: okhttp3.RequestBody?,
        @Part("phone_column") phoneColumn: okhttp3.RequestBody?): Response<ImportResult>
    @POST("groups/imports/preview") suspend fun sheet(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<ImportResult>
    @POST("groups/imports/{id}/approve") suspend fun approve(@Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>
    @POST("campaigns/preview") suspend fun preview(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<CampaignResult>
    @PATCH("campaigns/{id}") suspend fun editCampaign(@Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>): Response<CampaignResult>
    @POST("campaigns/{id}/schedule") suspend fun schedule(@Path("id") id: String,
        @Body body: Map<String, Boolean>): Response<Map<String, Any>>
    @GET("campaigns") suspend fun campaigns(): Response<CampaignsResult>
    @POST("campaigns/{id}/cancel") suspend fun cancel(@Path("id") id: String): Response<Map<String, Any>>
}

data class BusinessState(val groups: List<BusinessGroup> = emptyList(), val campaigns: List<CampaignRow> = emptyList(),
    val selection: BusinessGroup? = null, val selectionPrompt: String = "",
    val chooseRecipientsFirst: Boolean = false,
    val templates: List<MessageTemplate> = emptyList(),
    val import: ImportResult? = null, val draft: CampaignDetail? = null, val busy: Boolean = false,
    val error: String? = null, val notice: String? = null,
    val processingMessage: String? = null, val completedCampaign: CampaignDetail? = null)

class BusinessViewModel : ViewModel() {
    private val mutable = MutableStateFlow(BusinessState())
    val state = mutable.asStateFlow()
    private var csvBytes: ByteArray? = null
    private var sheetRows: List<List<String>>? = null
    private var importGroup = ""

    private suspend fun api(): BusinessApi {
        val account = AccountSession.account.value ?: error("Sign in again.")
        return NetworkModule.createService(account.backendUrl, BusinessApi::class.java,
            AccountSession.accessToken(account.backendUrl))
    }
    private fun run(action: suspend (BusinessApi) -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            try { action(api()) }
            catch (e: Exception) { if (e is CancellationException) throw e
                mutable.value = mutable.value.copy(error = e.message ?: "Couldn't reach the backend.") }
            finally { mutable.value = mutable.value.copy(busy = false, processingMessage = null) }
        }
    }
    private suspend fun refresh(api: BusinessApi) = coroutineScope {
        val groups = async { api.groups().authBody().groups }
        val campaigns = async { api.campaigns().authBody().campaigns }
        val loadedGroups = groups.await()
        val loadedCampaigns = campaigns.await()
        mutable.value = mutable.value.copy(groups = loadedGroups, campaigns = loadedCampaigns)
    }
    fun refresh() = run { refresh(it) }
    fun refreshCampaigns() {
        if (mutable.value.busy) return
        viewModelScope.launch {
            runCatching { api().campaigns().authBody().campaigns }
                .onSuccess { campaigns -> mutable.value = mutable.value.copy(campaigns = campaigns, error = null) }
        }
    }
    fun createGroup(name: String) = run { api -> api.create(mapOf("name" to name)).authBody(); refresh(api) }
    fun rename(id: String, name: String) = run { api -> api.rename(id, mapOf("name" to name)).authBody(); refresh(api) }
    fun delete(id: String) = run { api -> api.delete(id).authBody(); refresh(api) }
    fun add(group: String, name: String, number: String, consent: Boolean) = run { api ->
        api.add(group, mapOf("name" to name, "number" to number, "consent_confirmed" to consent)).authBody(); refresh(api)
    }
    fun edit(group: String, contact: String, name: String, number: String, consent: Boolean) = run { api ->
        api.edit(group, contact, mapOf("name" to name, "number" to number, "consent_confirmed" to consent)).authBody(); refresh(api)
    }
    fun optOut(group: String, contact: String) = run { api ->
        api.edit(group, contact, mapOf("opt_out" to true)).authBody(); refresh(api)
    }
    fun remove(group: String, contact: String) = run { api -> api.remove(group, contact).authBody(); refresh(api) }
    fun csv(group: String, bytes: ByteArray, nameColumn: Int? = null, phoneColumn: Int? = null) {
        csvBytes = bytes; sheetRows = null; importGroup = group
        run { api ->
            val part = MultipartBody.Part.createFormData("file", "contacts.csv", bytes.toRequestBody("text/csv".toMediaType()))
            fun field(value: String?) = value?.toRequestBody("text/plain".toMediaType())
            mutable.value = mutable.value.copy(import = api.csv(part, field(group)!!,
                field(nameColumn?.toString()), field(phoneColumn?.toString())).authBody())
        }
    }
    fun sheet(group: String, rows: List<List<String>>, nameColumn: Int? = null, phoneColumn: Int? = null) {
        csvBytes = null; sheetRows = rows; importGroup = group
        run { api ->
            val data = mutableMapOf<String, Any>("group_name" to group, "rows" to rows)
            if (nameColumn != null) data["name_column"] = nameColumn
            if (phoneColumn != null) data["phone_column"] = phoneColumn
            mutable.value = mutable.value.copy(import = api.sheet(data).authBody())
        }
    }
    fun chooseColumns(name: Int, phone: Int) {
        csvBytes?.let { csv(importGroup, it, name, phone) } ?: sheetRows?.let { sheet(importGroup, it, name, phone) }
    }
    fun approve(contacts: List<Map<String, Any>>, consent: Boolean) = run { api ->
        val batch = mutable.value.import?.import_id ?: error("Review the import first.")
        api.approve(batch, mapOf("contacts" to contacts, "consent_confirmed" to consent)).authBody()
        mutable.value = mutable.value.copy(import = null, notice = "Contacts saved.")
        refresh(api)
    }
    fun preview(group: String, prompt: String, recipients: List<String>, templateId: String? = null) = run { api ->
        val body = mutableMapOf<String, Any>("group_id" to group, "recipient_ids" to recipients)
        if (prompt.isNotBlank()) body["raw_prompt"] = prompt
        if (templateId != null) body["template_id"] = templateId
        mutable.value = mutable.value.copy(draft = api.preview(body).authBody().campaign)
    }
    fun editDraft(message: String, time: String, recipients: List<String>) = run { api ->
        val draft = mutable.value.draft ?: error("Preview the campaign first.")
        val body = mutableMapOf<String, Any>("recipient_ids" to recipients)
        if (message.isNotBlank()) body["message"] = message
        if (time.isNotBlank()) body["scheduled_time"] = time
        mutable.value = mutable.value.copy(draft = api.editCampaign(draft.id, body).authBody().campaign)
    }
    fun schedule() = run { api ->
        val draft = mutable.value.draft ?: error("Preview the campaign first.")
        mutable.value = mutable.value.copy(processingMessage = "Scheduling your calls")
        api.schedule(draft.id, mapOf("confirmed" to true)).authBody()
        val scheduled = CampaignRow(draft.id, draft.title, draft.scheduled_time, "scheduled",
            draft.eligible_count, mapOf("pending" to draft.eligible_count))
        mutable.value = mutable.value.copy(draft = null, selection = null, selectionPrompt = "", notice = "Campaign scheduled.",
            completedCampaign = draft, campaigns = listOf(scheduled) + mutable.value.campaigns.filterNot { it.id == draft.id })
    }
    fun acknowledgeScheduled() { mutable.value = mutable.value.copy(completedCampaign = null) }
    fun cancel(id: String) = run { api -> api.cancel(id).authBody(); refresh(api) }
    fun clearDraft() { mutable.value = mutable.value.copy(draft = null) }
    fun resolvePrompt(prompt: String, templateId: String? = null, onPersonal: () -> Unit) = run { api ->
        mutable.value = mutable.value.copy(processingMessage = "Finding your contacts")
        val result = api.resolve(mapOf("raw_prompt" to prompt)).authBody()
        if (result.status == "not_group") onPersonal()
        else {
            val selection = result.selection ?: error("No recipients were found. Please clarify who to call.")
            mutable.value = mutable.value.copy(selection = selection, selectionPrompt = prompt, draft = null,
                chooseRecipientsFirst = false,
                processingMessage = "Preparing your message and audio")
            // A complete home prompt needs only the final review, not another prompt/selection step.
            mutable.value = mutable.value.copy(draft = api.preview(buildMap<String, Any> {
                put("group_id", selection.id); put("raw_prompt", prompt)
                put("recipient_ids", selection.contacts.filter { it.consent_status == "approved" }.map { it.id })
                if (templateId != null) put("template_id", templateId)
            }).authBody().campaign)
        }
    }
    fun selectGroup(group: BusinessGroup, prompt: String) {
        mutable.value = mutable.value.copy(selection = group, selectionPrompt = prompt, draft = null,
            chooseRecipientsFirst = true, error = null)
    }
    fun clearSelection() { mutable.value = mutable.value.copy(selection = null, selectionPrompt = "", draft = null,
        chooseRecipientsFirst = false, error = null) }
    fun loadComposerOptions() = run { api ->
        val groups = api.groups().authBody().groups
        val templates = api.templates().authBody().templates
        mutable.value = mutable.value.copy(groups = groups, templates = templates)
    }
    fun loadTemplates() = run { api -> mutable.value = mutable.value.copy(templates = api.templates().authBody().templates) }
    fun saveTemplate(id: String?, name: String, message: String) = run { api ->
        val body = mapOf("name" to name, "message" to message)
        if (id == null) api.createTemplate(body).authBody() else api.editTemplate(id, body).authBody()
        mutable.value = mutable.value.copy(templates = api.templates().authBody().templates, notice = "Template saved.")
    }
    fun deleteTemplate(id: String) = run { api ->
        api.deleteTemplate(id).authBody()
        mutable.value = mutable.value.copy(templates = api.templates().authBody().templates, notice = "Template deleted.")
    }
}
