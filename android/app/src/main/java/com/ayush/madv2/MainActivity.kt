package com.ayush.madv2

import android.app.UiModeManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject
import com.ayush.madv2.ui.PaymentViewModel
import com.ayush.madv2.ui.PaymentOrder
import com.ayush.madv2.ui.screens.CreditPackScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayush.madv2.data.AccountSession
import com.ayush.madv2.data.SavedAccount
import com.ayush.madv2.ui.AuthViewModel
import com.ayush.madv2.ui.CallAiUiState
import com.ayush.madv2.ui.CallAiViewModel
import com.ayush.madv2.ui.BusinessViewModel
import com.ayush.madv2.ui.ProfileViewModel
import com.ayush.madv2.ui.screens.AuthScreen
import com.ayush.madv2.ui.CallAiApp
import com.ayush.madv2.ui.screens.VoxieLandingScreen
import com.ayush.madv2.ui.theme.MADv2Theme
import com.ayush.madv2.ui.components.VoxieTransition

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    private val payments: PaymentViewModel by viewModels()

    private fun applyLaunchTheme(choice: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val systemDark = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = choice == "Dark" || (choice == "System" && systemDark)
        getSystemService(UiModeManager::class.java).setApplicationNightMode(
            if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
    }

    override fun onPaymentSuccess(paymentId: String?, data: PaymentData?) {
        payments.received(data?.orderId, paymentId, data?.signature)
    }

    override fun onPaymentError(code: Int, response: String?, data: PaymentData?) {
        payments.cancelled()
    }

    private fun openCheckout(order: PaymentOrder) {
        val checkout = Checkout()
        checkout.setKeyID(order.key_id)
        checkout.open(this, JSONObject().apply {
            put("name", "Voxie")
            put("description", "${order.credits} Call Credits · Test Mode")
            put("order_id", order.order_id)
            put("amount", order.amount)
            put("currency", "INR")
            put("theme", JSONObject().put("color", "#7762FF"))
            put("prefill", JSONObject().put("email", AccountSession.account.value?.user?.email.orEmpty()))
        })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedTheme = getSharedPreferences("pocketcall_preferences", MODE_PRIVATE).getString("theme", "System")
        applyLaunchTheme(savedTheme ?: "System")
        val systemDark = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        window.decorView.setBackgroundColor(if (savedTheme == "Dark" || (savedTheme == "System" && systemDark))
            android.graphics.Color.rgb(32, 33, 35) else android.graphics.Color.rgb(247, 247, 249))
        enableEdgeToEdge()
        setContent {
            var theme by remember { mutableStateOf(getSharedPreferences("pocketcall_preferences", MODE_PRIVATE).getString("theme", "System")) }
            val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
            val auth: AuthViewModel = viewModel()
            val authState by auth.state.collectAsStateWithLifecycle()
            val account by AccountSession.account.collectAsStateWithLifecycle()
            var showLanding by rememberSaveable { mutableStateOf(account == null) }
            var buyingCredits by rememberSaveable { mutableStateOf(false) }
            val paymentState by payments.state.collectAsStateWithLifecycle()
            LaunchedEffect(account?.user?.id) {
                if (account != null) payments.start()
                else buyingCredits = false
            }
            BackHandler(!showLanding && account == null && !authState.busy) { showLanding = true }
            MADv2Theme(dynamicColor = false, darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val stage = when {
                        showLanding -> "welcome"
                        account == null || authState.restoring || authState.restoreFailed -> "auth"
                        buyingCredits || authState.needsCredits -> "pricing"
                        else -> "app"
                    }
                    VoxieTransition(stage, Modifier.fillMaxSize()) { visibleStage ->
                    if (visibleStage != "app") {
                        SideEffect {
                            WindowCompat.getInsetsController(window, window.decorView).apply {
                                isAppearanceLightStatusBars = !dark
                                isAppearanceLightNavigationBars = !dark
                            }
                        }
                        when {
                            visibleStage == "welcome" -> VoxieLandingScreen(onGetStarted = { showLanding = false })
                            visibleStage == "auth" ->
                                AuthScreen(authState, auth::submit, auth::switchMode,
                                    onBack = { showLanding = true }, onBackend = auth::setBackend,
                                    onRetry = auth::restore, onSignOut = auth::signOut)
                            else -> CreditPackScreen(paymentState,
                                onPay = { payments.purchase(it, ::openCheckout) }, onRetry = payments::verify,
                                onReload = payments::start,
                                onBack = { if (authState.needsCredits) auth.signOut() else buyingCredits = false },
                                onDone = { auth.creditPurchaseCompleted(); buyingCredits = false })
                        }
                    } else {
                        account?.let { signedIn -> key(signedIn.user.id) {
                            AccountApp(signedIn, auth::signOut, auth::setBackend,
                                edition = authState.edition ?: "personal",
                                onCredits = { payments.start(); buyingCredits = true },
                                onThemeChanged = { theme = it; applyLaunchTheme(it) })
                        } }
                    }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (getSharedPreferences("pocketcall_preferences", MODE_PRIVATE).getString("theme", "System") == "System")
            applyLaunchTheme("System")
    }
}

@Composable
private fun AccountApp(account: SavedAccount, onSignOut: () -> Unit, onBackend: (String) -> Unit,
                       edition: String, onCredits: () -> Unit, onThemeChanged: (String) -> Unit) {
    // Disposing the account screen cancels its work and removes the previous user's calls.
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val calls: CallAiViewModel = viewModel(viewModelStoreOwner = owner, factory = viewModelFactory {
        initializer { CallAiViewModel(initialState = CallAiUiState(userId = account.user.id,
            userName = account.user.fullName, backendBaseUrl = account.backendUrl)) }
    })
    val business: BusinessViewModel = viewModel(viewModelStoreOwner = owner)
    val profile: ProfileViewModel = viewModel(viewModelStoreOwner = owner)
    CallAiApp(calls, onSignOut, onBackend, mode = edition, onCredits = onCredits,
        business = business, profile = profile, onThemeChanged = onThemeChanged)
}
