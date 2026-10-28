package com.ayush.madv2.ui

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.ayush.madv2.data.AccountSession
import com.ayush.madv2.data.SignInRequired
import com.ayush.madv2.network.AccountUser
import com.ayush.madv2.network.AuthResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccountSessionTest {
    @Test fun tokensAreEncryptedAndBoundToTheirServer() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AccountSession.initialize(context)
        AccountSession.clear()
        try {
            val response = AuthResponse("authenticated", user = AccountUser("test-user", "test@example.com", "Test"),
                accessToken = "private-access-token", refreshToken = "private-refresh-token")
            AccountSession.save(response, "https://backend.example/")
            val stored = context.getSharedPreferences("pocketcall_account", Context.MODE_PRIVATE).getString("session", "")!!
            assertFalse(stored.contains("private-access-token"))
            assertFalse(stored.contains("private-refresh-token"))
            assertEquals("private-access-token", AccountSession.accessToken("https://backend.example/"))
            try {
                AccountSession.accessToken("https://different.example/")
                fail("A saved token must not be sent to a different server")
            } catch (_: SignInRequired) { }
            val old = AccountSession.account.value!!
            AccountSession.clear()
            try {
                AccountSession.save(response, old.backendUrl, expected = old)
                fail("A late refresh must not restore a signed-out session")
            } catch (_: SignInRequired) { }
            assertNull(AccountSession.account.value)
        } finally { AccountSession.clear() }
    }
}
