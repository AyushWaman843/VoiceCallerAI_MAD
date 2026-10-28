package com.ayush.madv2.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayush.madv2.data.AccountSession
import com.ayush.madv2.network.AuthApi
import com.ayush.madv2.network.NetworkModule
import com.ayush.madv2.data.authBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileState(val name: String = "", val organization: String = "", val timezone: String = "Asia/Kolkata",
                        val balance: Int = 0, val plan: String = "", val busy: Boolean = false,
                        val error: String? = null, val notice: String? = null)

class ProfileViewModel : ViewModel() {
    private val mutable = MutableStateFlow(ProfileState(name = AccountSession.account.value?.user?.fullName.orEmpty()))
    val state = mutable.asStateFlow()
    fun refresh() {
        viewModelScope.launch {
            try {
                val account = AccountSession.account.value ?: return@launch
                val token = AccountSession.accessToken(account.backendUrl)
                val status = NetworkModule.createService(account.backendUrl, PaymentApi::class.java, token).status().authBody()
                mutable.value = mutable.value.copy(name = account.user.fullName,
                    organization = status.organization_name.orEmpty(), balance = status.balance,
                    plan = status.plan_id.orEmpty(), timezone = status.timezone?.takeIf { it.isNotBlank() } ?: "Asia/Kolkata", error = null)
            } catch (e: Exception) { if (e is CancellationException) throw e
                mutable.value = mutable.value.copy(error = e.message ?: "Could not load account settings.") }
        }
    }
    fun save(name: String, organization: String?, timezone: String) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val account = AccountSession.account.value ?: error("Sign in again.")
                val body = mutableMapOf("full_name" to name, "timezone" to timezone)
                if (organization != null) body["organization_name"] = organization
                val response = NetworkModule.createService(account.backendUrl, AuthApi::class.java,
                    AccountSession.accessToken(account.backendUrl)).profile(body).authBody()
                AccountSession.updateUser(response.user, account)
                mutable.value = mutable.value.copy(name = response.user.fullName,
                    organization = response.organization_name.orEmpty(),
                    timezone = response.timezone?.takeIf { it.isNotBlank() } ?: "Asia/Kolkata",
                    notice = "Profile saved.")
            } catch (e: Exception) { if (e is CancellationException) throw e
                mutable.value = mutable.value.copy(error = e.message ?: "Couldn't save profile.") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
