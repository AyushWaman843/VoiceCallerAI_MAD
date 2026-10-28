package com.ayush.madv2.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ayush.madv2.network.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedAccount(val user: AccountUser, val accessToken: String, val refreshToken: String,
                        val expiresAt: Long, val backendUrl: String)

class SignInRequired : IllegalStateException("Your session has expired. Please sign in again.")

internal fun <T> Response<T>.authBody(): T {
    if (isSuccessful) return body() ?: error("The account service returned an empty response.")
    if (code() == 401) throw SignInRequired()
    val message = runCatching { Gson().fromJson(errorBody()?.string(), BasicResponseDto::class.java)?.message }.getOrNull()
    throw IllegalStateException(message ?: if (code() == 404)
        "This backend is missing the requested feature. Restart Flask with the latest project and check the backend URL."
        else "The server could not complete this request (${code()}).")
}

object AccountSession {
    private val current = MutableStateFlow<SavedAccount?>(null)
    val account = current.asStateFlow()
    private var context: Context? = null
    private val refreshLock = Mutex()
    private const val keyAlias = "pocketcall_account_key"
    private val gson = Gson()

    fun initialize(appContext: Context) {
        if (context != null) return
        context = appContext.applicationContext
        val encrypted = preferences().getString("session", null) ?: return
        current.value = runCatching {
            val pieces = encrypted.split(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
            val json = cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
            gson.fromJson(json, SavedAccount::class.java)
        }.getOrElse { preferences().edit().remove("session").commit(); null }
    }

    private fun preferences() = requireNotNull(context).getSharedPreferences("pocketcall_account", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized
    fun save(response: AuthResponse, backendUrl: String, expected: SavedAccount? = null) {
        if (expected != null && current.value != expected) throw SignInRequired()
        val saved = SavedAccount(requireNotNull(response.user), requireNotNull(response.accessToken),
            requireNotNull(response.refreshToken), System.currentTimeMillis() + response.expiresIn * 1000, backendUrl)
        persist(saved)
    }

    private fun persist(saved: SavedAccount) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(gson.toJson(saved).toByteArray(Charsets.UTF_8))
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(preferences().edit().putString("session", value).commit()) { "Unable to save your session on this device." }
        current.value = saved
    }

    @Synchronized
    fun clear() {
        context?.let { preferences().edit().clear().commit() }
        current.value = null
    }

    @Synchronized
    fun updateUser(user: AccountUser, expected: SavedAccount) {
        if (current.value != expected || user.id != expected.user.id) throw SignInRequired()
        persist(expected.copy(user = user))
    }

    suspend fun accessToken(backendUrl: String): String = refreshLock.withLock {
        val saved = current.value ?: throw SignInRequired()
        // Never send a saved account's token to a newly entered server address.
        if (saved.backendUrl.trimEnd('/') != backendUrl.trimEnd('/')) throw SignInRequired()
        if (saved.expiresAt > System.currentTimeMillis() + 60_000) return@withLock saved.accessToken
        try {
            val refreshed = NetworkModule.createService(backendUrl, AuthApi::class.java)
                .refresh(RefreshCredentials(saved.refreshToken)).authBody()
            if (current.value != saved) throw SignInRequired()
            save(refreshed, backendUrl, expected = saved)
            requireNotNull(current.value).accessToken
        } catch (error: SignInRequired) {
            if (current.value == saved) clear()
            throw error
        }
    }
}
