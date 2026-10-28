package com.ayush.madv2.ui.screens

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayush.madv2.R
import com.ayush.madv2.ui.PaymentState
import com.ayush.madv2.ui.components.*

@Composable
fun CreditPackScreen(state: PaymentState, onPay: (String) -> Unit, onRetry: () -> Unit,
                     onBack: () -> Unit, onDone: () -> Unit, onReload: () -> Unit = {}) {
    var selected by rememberSaveable { mutableStateOf("plus") }
    val purple = Color(0xFF7762FF)
    val muted = Color(0xFF747A8A)
    val haptics = rememberPocketHaptics()
    LaunchedEffect(state.receipt) { if (state.receipt != null) haptics.success() }
    BackHandler { if (!state.busy) { if (state.receipt != null) onDone() else onBack() } }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Image(painterResource(R.drawable.landing_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.receipt == null) PocketIconButton(Glyph.Back, "Back", !state.busy, onBack)
                Spacer(Modifier.weight(1f))
                Text("TEST MODE", color = purple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            VoxieTransition(state.receipt, Modifier.weight(1f)) { receipt ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (receipt == null) {
                    Image(painterResource(R.drawable.voxie_landing), null,
                        Modifier.size(100.dp).align(Alignment.End))
                    Text("Choose your plan", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101321))
                    Text("Find the right pack for your calls.", color = muted, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
                    state.plans.forEach { pack ->
                        val chosen = selected == pack.id
                        Column(Modifier.padding(bottom = 12.dp).fillMaxWidth()
                            .border(if (chosen) 1.5.dp else 1.dp, if (chosen) purple else Color(0xFFE9E9EF), RoundedCornerShape(20.dp))
                            .background(if (chosen) Color(0xFFF3F0FF) else Color.White, RoundedCornerShape(20.dp))
                            .clickable(enabled = !state.busy && !state.pending, onClick = hapticClick { selected = pack.id })
                            .padding(16.dp)) {
                            if (pack.id == "plus") Text("MOST POPULAR", color = purple, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(chosen, null, colors = RadioButtonDefaults.colors(selectedColor = purple))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pack.name, fontWeight = FontWeight.Bold)
                                    Text("${pack.credits} call credits", fontSize = 12.sp, color = muted)
                                    Text("₹${pack.price_paise / 100}", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(if (pack.edition == "business") "Groups and campaigns" else "Individual calls", color = muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Text("Razorpay test checkout · No real money is charged.", color = muted, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    Spacer(Modifier.height(28.dp))
                    Box(Modifier.size(92.dp).align(Alignment.CenterHorizontally).background(Color(0xFFD7F8E2), CircleShape),
                        contentAlignment = Alignment.Center) { PocketIcon(Glyph.Check, color = Color(0xFF178452)) }
                    Text("Payment successful!", fontSize = 27.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
                    Text("Your test credits are ready.", color = muted,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 32.dp))
                    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFFE9E9EF), RoundedCornerShape(20.dp)).padding(20.dp)) {
                        Text("Payment details", fontWeight = FontWeight.Bold)
                        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color(0xFFF0F0F4))
                        listOf("Credits added" to "${receipt.credits}", "Amount" to "₹${receipt.amount / 100}",
                            "Payment ID" to receipt.payment_id, "Status" to "Successful", "Test credit balance" to "${receipt.balance}").forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(label, color = muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                state.error?.let { Text(it, color = Color(0xFFB42318), fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                if (state.plans.isEmpty() && state.receipt == null) {
                    TextButton(onClick = hapticClick(onReload)) { Text("Retry loading plans") }
                }
            }
            }
            Button(onClick = hapticClick {
                when { state.receipt != null -> onDone(); state.pending -> onRetry(); else -> onPay(selected) }
            }, enabled = !state.busy && (state.receipt != null || state.pending || state.plans.any { it.id == selected }), modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(56.dp),
                shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    Text(when { state.receipt != null -> "Go to Voxie"; state.pending -> "Retry verification"; else -> "Continue to Pay" })
                    if (!state.pending) {
                        Spacer(Modifier.width(16.dp))
                        PocketIcon(Glyph.Forward, color = LocalContentColor.current)
                    }
                }
            }
        }
    }
}
