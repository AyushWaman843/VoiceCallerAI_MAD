package com.ayush.madv2.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayush.madv2.data.*
import com.ayush.madv2.network.*
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET

data class PaymentOrder(val order_id: String, val key_id: String, val amount: Int, val credits: Int)
data class PaymentProof(val order_id: String, val payment_id: String, val signature: String)
data class PaymentReceipt(val credits: Int, val amount: Int, val payment_id: String, val balance: Int)
data class CreditPurchase(val pack: String, val credits: Int, val amount: Int, val payment_id: String, val created_at: String)
data class PlanDto(val id: String, val name: String, val edition: String, val price_paise: Int,
                   val credits: Int, val groups: Boolean, val campaigns: Boolean)
data class PlansResponse(val plans: List<PlanDto>)
data class PaymentStatus(val has_purchase: Boolean, val plan_id: String? = null, val edition: String? = null,
                         val balance: Int = 0, val organization_name: String? = null,
                         val timezone: String? = null,
                         val purchases: List<CreditPurchase> = emptyList())
interface PaymentApi {
    @GET("payments/plans") suspend fun plans(): Response<PlansResponse>
    @GET("payments/status") suspend fun status(): Response<PaymentStatus>
    @POST("payments/orders") suspend fun order(@Body pack: Map<String, String>): Response<PaymentOrder>
    @POST("payments/verify") suspend fun verify(@Body proof: PaymentProof): Response<PaymentReceipt>
}
data class PaymentState(val busy: Boolean = false, val error: String? = null,
                        val pending: Boolean = false, val receipt: PaymentReceipt? = null,
                        val plans: List<PlanDto> = emptyList())

class PaymentViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val gson = Gson()
    private val mutable = MutableStateFlow(PaymentState(pending = saved.get<String>("proof") != null))
    val state = mutable.asStateFlow()

    fun start() {
        val owner = AccountSession.account.value?.user?.id
        if (saved.get<String>("owner") != owner) { saved.remove<String>("proof"); saved.remove<String>("order") }
        saved["owner"] = owner
        mutable.value = PaymentState(pending = saved.get<String>("proof") != null, plans = mutable.value.plans)
        viewModelScope.launch {
            try {
                val account = AccountSession.account.value ?: return@launch
                val plans = NetworkModule.createService(account.backendUrl, PaymentApi::class.java)
                    .plans().authBody().plans
                mutable.value = mutable.value.copy(plans = plans)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = mutable.value.copy(error = "Couldn't load plans. Check the backend connection.")
            }
        }
    }

    private suspend fun api(): PaymentApi {
        val account = AccountSession.account.value ?: throw IllegalStateException("Please sign in again.")
        check(account.user.id == saved.get<String>("owner")) { "Please reopen credit packs." }
        return NetworkModule.createService(account.backendUrl, PaymentApi::class.java,
            AccountSession.accessToken(account.backendUrl))
    }

    fun purchase(pack: String, open: (PaymentOrder) -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val order = api().order(mapOf("pack" to pack)).authBody()
                saved["order"] = order.order_id
                check(order.key_id.startsWith("rzp_test_")) { "Only test payments are supported." }
                open(order)
            } catch (e: Exception) { failed(e) }
        }
    }

    fun received(order: String?, payment: String?, signature: String?) {
        if (order == null || order != saved.get<String>("order") || payment == null || signature == null) {
            cancelled("Payment details were incomplete. Please contact support before retrying payment.")
            return
        }
        saved["proof"] = gson.toJson(PaymentProof(order, payment, signature))
        verify()
    }

    fun verify() {
        val proof = saved.get<String>("proof") ?: return
        mutable.value = mutable.value.copy(busy = true, pending = true, error = null)
        viewModelScope.launch {
            try {
                val receipt = api().verify(gson.fromJson(proof, PaymentProof::class.java)).authBody()
                saved.remove<String>("proof")
                mutable.value = mutable.value.copy(busy = false, pending = false, receipt = receipt)
            } catch (e: Exception) { failed(e) }
        }
    }

    fun cancelled(message: String = "Checkout cancelled or payment failed. No credits were added.") {
        mutable.value = mutable.value.copy(busy = false, error = message, pending = saved.get<String>("proof") != null)
    }
    private fun failed(e: Exception) {
        if (e is CancellationException) throw e
        cancelled(e.message ?: "Unable to reach the backend. Please retry.")
    }
}
