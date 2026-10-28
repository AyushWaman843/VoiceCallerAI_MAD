package com.ayush.madv2.ui.screens

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayush.madv2.R
import com.ayush.madv2.ui.AuthUiState
import com.ayush.madv2.ui.components.*

@Composable
fun AuthScreen(
    state: AuthUiState, onSubmit: (String, String, String) -> Unit,
    onSwitchMode: () -> Unit, onBack: () -> Unit, onBackend: (String) -> Unit,
    onRetry: () -> Unit, onSignOut: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are deliberately excluded from saved instance state.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var connectionDialog by remember { mutableStateOf(false) }
    var backend by remember(state.backendUrl) { mutableStateOf(state.backendUrl) }
    val keyboard = LocalSoftwareKeyboardController.current
    val ink = Color(0xFF080D19)
    val muted = Color(0xFF727D90)
    val haptics = rememberPocketHaptics()
    LaunchedEffect(state.signup, state.notice) { password = ""; visible = false }
    LaunchedEffect(state.error) { if (state.error != null) haptics.error() }
    LaunchedEffect(state.notice) { if (state.notice != null) haptics.success() }
    fun submit() { keyboard?.hide(); onSubmit(name, email, password) }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.landing_bg), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .89f)))
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                Box(Modifier.fillMaxWidth().height(64.dp)) {
                    Box(Modifier.align(Alignment.CenterStart)) { PocketIconButton(Glyph.Back, "Back", !state.busy, onBack) }
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Image(painterResource(R.drawable.voxie_landing), null, modifier = Modifier.size(28.dp))
                        Text("Voxie", color = ink, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(if (state.signup) "Create your\naccount" else "Welcome\nback", color = ink,
                    fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 37.sp, letterSpacing = (-.7).sp)
                Spacer(Modifier.height(9.dp))
                Text(if (state.signup) "Let's get you started with Voxie." else "Sign in to pick up where you left off.",
                    color = muted, fontSize = 15.sp)
                Spacer(Modifier.height(26.dp))
                if (state.restoring) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = ink, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp)); Text("Restoring your session...")
                    }
                } else if (state.restoreFailed) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = hapticClick(onRetry), modifier = Modifier.fillMaxWidth()) { Text("Reconnect") }
                    TextButton(onClick = hapticClick(onSignOut)) { Text("Use another account") }
                } else {
                    androidx.compose.animation.AnimatedVisibility(state.signup) {
                        Column {
                        AccountField(name, { name = it }, "Full name", R.drawable.outline_account_circle_24, !state.busy)
                        Spacer(Modifier.height(14.dp))
                        }
                    }
                    AccountField(email, { email = it }, "Email address", R.drawable.ic_auth_mail, !state.busy, KeyboardType.Email)
                    Spacer(Modifier.height(14.dp))
                    AccountField(password, { password = it }, "Password", R.drawable.ic_auth_lock, !state.busy,
                        KeyboardType.Password, if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        onDone = { submit() }, trailing = {
                            IconButton(onClick = hapticClick { visible = !visible }, enabled = !state.busy) {
                                Icon(painterResource(R.drawable.ic_auth_eye), if (visible) "Hide password" else "Show password",
                                    tint = if (visible) ink else muted, modifier = Modifier.size(21.dp))
                            }
                        })
                    if (state.signup) Text("Use at least 8 characters.", color = muted, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                    state.notice?.let {
                        Surface(color = Color(0xFFE9F4EE), shape = RoundedCornerShape(14.dp), modifier = Modifier.padding(top = 16.dp)) {
                            Text(it, color = Color(0xFF245C42), modifier = Modifier.padding(14.dp), fontSize = 14.sp)
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp), fontSize = 14.sp) }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = hapticClick { submit() }, enabled = !state.busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF060809), contentColor = Color.White),
                        shape = CircleShape, elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else {
                            Text(if (state.signup) "Create Account" else "Sign In", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Spacer(Modifier.width(16.dp)); PocketIcon(Glyph.Forward, color = LocalContentColor.current)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.signup) "Already have an account?" else "New to Voxie?", color = muted, fontSize = 13.sp)
                        TextButton(onClick = hapticClick(onSwitchMode), enabled = !state.busy) {
                            Text(if (state.signup) "Sign in" else "Create account", color = ink, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = hapticClick { connectionDialog = true }, enabled = !state.busy && !state.restoring,
                    modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Connection settings", color = muted, fontSize = 12.sp) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (connectionDialog) AlertDialog(onDismissRequest = { connectionDialog = false },
        title = { Text("Backend connection") },
        text = { OutlinedTextField(backend, { backend = it }, label = { Text("Server URL") }, singleLine = true) },
        confirmButton = { TextButton(onClick = hapticClick { onBackend(backend); connectionDialog = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = hapticClick { connectionDialog = false }) { Text("Cancel") } })
}

@Composable
private fun AccountField(
    value: String, onValue: (String) -> Unit, label: String, icon: Int, enabled: Boolean,
    keyboard: KeyboardType = KeyboardType.Text, transformation: VisualTransformation = VisualTransformation.None,
    onDone: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(value, onValue, enabled = enabled, singleLine = true, label = { Text(label, fontSize = 13.sp) },
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White.copy(alpha = .9f),
            focusedContainerColor = Color.White, unfocusedBorderColor = Color(0xFFE5E7EC), focusedBorderColor = Color(0xFF6D8CB6),
            unfocusedLabelColor = Color(0xFF778294)),
        leadingIcon = { Icon(painterResource(icon), null, tint = Color(0xFF778294), modifier = Modifier.size(22.dp)) },
        trailingIcon = trailing, visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (onDone == null) ImeAction.Next else ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (enabled) onDone?.invoke() }))
}
