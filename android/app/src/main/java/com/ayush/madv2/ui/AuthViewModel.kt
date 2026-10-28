package com.ayush.madv2.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayush.madv2.data.*
import com.ayush.madv2.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class AuthUiState(val signup: Boolean = true, val busy: Boolean = false, val restoring: Boolean = false, val restoreFailed: Boolean = false,
                       val needsCredits: Boolean = true, val planId: String? = null, val edition: String? = null,
                       val error: String? = null, val notice: String? = null, val backendUrl: String = "")

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("pocketcall_preferences", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(AuthUiState(
        backendUrl = preferences.getString("backend", null) ?: defaultBaseUrl()))
    val state = mutableState.asStateFlow()

    init {
        AccountSession.initialize(application)
        AccountSession.account.value?.let {
            mutableState.update { state -> state.copy(backendUrl = it.backendUrl) }
            restore()
        }
    }

    fun switchMode() { mutableState.update { it.copy(signup = !it.signup, error = null, notice = null) } }

    fun setBackend(value: String) {
        val uri = runCatching { java.net.URI(value.trim()) }.getOrNull()
        if (uri?.scheme !in listOf("http", "https") || uri?.host.isNullOrBlank() || uri?.userInfo != null || uri?.query != null || uri?.fragment != null) {
            mutableState.update { it.copy(error = "Enter a valid http:// or https:// server URL.") }
            return
        }
        val normalized = value.trim().trimEnd('/') + "/"
        if (AccountSession.account.value?.backendUrl != normalized) AccountSession.clear()
        preferences.edit().putString("backend", normalized).apply()
        mutableState.update { it.copy(backendUrl = normalized, error = null, restoreFailed = false, notice = "Connection updated.") }
    }

    fun submit(name: String, email: String, password: String) {
        val snapshot = state.value
        if (snapshot.busy) return
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() || password.isBlank() ||
            (snapshot.signup && (name.isBlank() || name.trim().length > 100 || password.length < 8))) {
            mutableState.update { it.copy(error = "Enter a valid email${if (snapshot.signup) ", your name, and a password of at least 8 characters" else " and password"}.") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null, notice = null) }
            try {
                val api = NetworkModule.createService(snapshot.backendUrl, AuthApi::class.java)
                val credentials = AuthCredentials(email.trim(), password, name.trim().takeIf { snapshot.signup })
                val result = (if (snapshot.signup) api.signup(credentials) else api.login(credentials)).authBody()
                if (result.status == "confirmation_required") {
                    mutableState.update { it.copy(signup = false, notice = result.message) }
                } else {
                    val plan = NetworkModule.createService(snapshot.backendUrl, PaymentApi::class.java,
                        requireNotNull(result.accessToken)).status().authBody()
                    mutableState.update { it.copy(needsCredits = !plan.has_purchase, planId = plan.plan_id, edition = plan.edition) }
                    AccountSession.save(result, snapshot.backendUrl)
                    mutableState.update { it.copy(signup = false, restoreFailed = false) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(error = message(error)) }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    fun restore() {
        if (state.value.restoring) return
        val saved = AccountSession.account.value ?: return
        mutableState.update { it.copy(restoring = true, restoreFailed = false, error = null) }
        viewModelScope.launch {
            try {
                val token = AccountSession.accessToken(saved.backendUrl)
                val user = NetworkModule.createService(saved.backendUrl, AuthApi::class.java, token).me().authBody().user
                val plan = NetworkModule.createService(saved.backendUrl, PaymentApi::class.java, token)
                    .status().authBody()
                mutableState.update { it.copy(needsCredits = !plan.has_purchase, planId = plan.plan_id, edition = plan.edition) }
                val current = AccountSession.account.value
                if (current != null && user != null) AccountSession.updateUser(user, current)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is SignInRequired) AccountSession.clear()
                mutableState.update { it.copy(error = message(error), signup = false, restoreFailed = AccountSession.account.value != null) }
            } finally { mutableState.update { it.copy(restoring = false) } }
        }
    }

    fun refreshPlan() {
        val saved = AccountSession.account.value ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            try {
                val token = AccountSession.accessToken(saved.backendUrl)
                val plan = NetworkModule.createService(saved.backendUrl, PaymentApi::class.java, token).status().authBody()
                mutableState.update { it.copy(needsCredits = !plan.has_purchase, planId = plan.plan_id,
                    edition = plan.edition, error = null) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(error = message(error)) }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    fun signOut() {
        val saved = AccountSession.account.value
        AccountSession.clear()
        mutableState.update { it.copy(signup = false, error = null, notice = null, restoreFailed = false, needsCredits = true, planId = null, edition = null) }
        if (saved != null) viewModelScope.launch {
            try {
                NetworkModule.createService(saved.backendUrl, AuthApi::class.java, saved.accessToken).logout().authBody()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(notice = "Signed out on this device. The server could not be reached to revoke the session.") }
            }
        }
    }

    fun creditPurchaseCompleted() { refreshPlan() }

    private fun message(error: Exception) = if (error is IOException)
        "Couldn't reach the account service. Check your connection and server address."
        else error.message ?: "Couldn't complete sign-in. Please try again."
}
