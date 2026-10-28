package com.ayush.madv2.ui.screens

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayush.madv2.data.*
import com.ayush.madv2.network.NetworkModule
import com.ayush.madv2.ui.*
import com.ayush.madv2.ui.components.*
import kotlinx.coroutines.CancellationException

@Composable
fun CreditsScreen(onBuy: () -> Unit, balance: Int = 0, plan: String = "") {
    var status by remember { mutableStateOf<PaymentStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        error = null
        try {
            val account = AccountSession.account.value ?: throw SignInRequired()
            status = NetworkModule.createService(account.backendUrl, PaymentApi::class.java,
                AccountSession.accessToken(account.backendUrl)).status().authBody()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "Couldn't load your credits. Please check the connection and try again."
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { VoxiePageHeader("Your credits", "A little credit. A lot of connection.") }
        if (error != null) item { InlineError(error!!, true, { retry++ }) }
        val wallet = status
        if (wallet == null && error == null) item { ThinkingIndicator("Checking your balance") }
        if (wallet != null) {
            item {
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("TEST CREDIT BALANCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text("${wallet.balance}", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                        Text(if (wallet.has_purchase) "${wallet.plan_id?.replaceFirstChar { it.uppercase() } ?: "Credit"} plan active" else "No plan active yet", fontWeight = FontWeight.SemiBold)
                        Text("One test credit is reserved per call and refunded if cancelled or failed.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { OutlinedButton(onClick = hapticClick(onBuy), modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Text("Add more test credits") } }
            if (wallet.purchases.isNotEmpty()) item { Text("Your purchases", style = MaterialTheme.typography.titleMedium) }
            items(wallet.purchases, key = { it.payment_id }) { purchase ->
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${purchase.pack.replaceFirstChar { it.uppercase() }} · ${purchase.credits} credits", fontWeight = FontWeight.SemiBold)
                        Text("₹${purchase.amount / 100} · Verified", color = MaterialTheme.colorScheme.secondary)
                        Text(purchase.payment_id, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
