package com.ayush.madv2.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import com.ayush.madv2.ui.screens.AuthScreen
import com.ayush.madv2.ui.theme.MADv2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class AuthScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun signupLoginAndConfirmation() {
        val state = mutableStateOf(AuthUiState())
        var submitted = emptyList<String>()
        compose.setContent {
            MADv2Theme(darkTheme = false) {
                AuthScreen(state.value, onSubmit = { name, email, password -> submitted = listOf(name, email, password) },
                    onSwitchMode = { state.value = state.value.copy(signup = !state.value.signup) }, onBack = {},
                    onBackend = {}, onRetry = {}, onSignOut = {})
            }
        }
        compose.onNodeWithText("Continue with Google").assertDoesNotExist()
        compose.onNodeWithText("Full name").performTextInput("Ayush Waman")
        compose.onNodeWithText("Email address").performTextInput("ayush@example.com")
        compose.onNodeWithText("Password").performTextInput("test-password")
        compose.onNodeWithContentDescription("Show password").performClick()
        compose.onNodeWithContentDescription("Hide password").assertExists()
        compose.onNodeWithText("Create Account").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("Ayush Waman", "ayush@example.com", "test-password"), submitted) }
        compose.onNodeWithText("Sign in").performScrollTo().performClick()
        compose.onNodeWithText("Full name").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(notice = "Check your email to confirm your account.") }
        compose.onNodeWithText("Check your email to confirm your account.").assertExists()
        compose.runOnIdle { state.value = AuthUiState() }
        compose.onNodeWithText("Create your\naccount").performScrollTo()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "voxie-signup.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
