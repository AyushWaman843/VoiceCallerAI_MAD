package com.ayush.madv2.ui.screens

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val SHEETS_READONLY = "https://www.googleapis.com/auth/spreadsheets.readonly"

private fun authorizationError(cause: Throwable): String = when (cause) {
    is ApiException -> "Google Sheets authorization failed (code ${cause.statusCode}). Check the app's Google OAuth setup and test users."
    else -> cause.message ?: "Google Sheets authorization failed."
}

@Composable
fun GoogleSheetImport(groupName: String, onDismiss: () -> Unit,
                      onRows: (String, List<List<String>>) -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    suspend fun read(token: String) {
        loading = true
        try {
            val id = Regex("/spreadsheets/d/([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("Paste a Google Sheets link.")
            val rows = withContext(Dispatchers.IO) {
                fun fetch(path: String): com.google.gson.JsonObject {
                    val connection = URL("https://sheets.googleapis.com/v4/spreadsheets/$id/$path")
                        .openConnection() as HttpsURLConnection
                    try {
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = 10_000
                        connection.readTimeout = 20_000
                        if (connection.responseCode != 200) throw IllegalStateException("Google Sheets returned ${connection.responseCode}.")
                        return JsonParser.parseReader(connection.inputStream.reader()).asJsonObject
                    } finally { connection.disconnect() }
                }
                val gid = Regex("[?&#]gid=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()
                val range = if (gid == null) "A1:ZZ1001" else {
                    val metadata = fetch("?fields=sheets(properties(sheetId,title))")
                    val title = metadata.getAsJsonArray("sheets")?.firstOrNull { sheet ->
                        sheet.asJsonObject.getAsJsonObject("properties").get("sheetId").asInt == gid
                    }?.asJsonObject?.getAsJsonObject("properties")?.get("title")?.asString
                        ?: throw IllegalArgumentException("This sheet tab was not found.")
                    Uri.encode("'${title.replace("'", "''")}'!A1:ZZ1001")
                }
                fetch("values/$range").getAsJsonArray("values")
                    ?.map { line -> line.asJsonArray.map { it.asString } }.orEmpty()
            }
            if (rows.isEmpty()) throw IllegalStateException("This sheet has no contact rows.")
            onRows(groupName, rows)
            onDismiss()
        } catch (e: Exception) { error = e.message ?: "Couldn't read the sheet." }
        finally { loading = false }
    }
    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching { Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(result.data).accessToken }
                .onSuccess { token ->
                    if (token.isNullOrBlank()) error = "Google did not grant Sheets read access. Please approve the permission and try again."
                    else scope.launch { read(token) }
                }
                .onFailure { error = authorizationError(it) }
        } else {
            val failure = result.data?.let { data ->
                runCatching { Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data) }.exceptionOrNull()
            }
            error = failure?.let(::authorizationError)
                ?: "Google Sheets access was not granted. If you selected an account, check the app's Google OAuth setup and test users."
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Import Google Sheet") },
        text = { Column {
            Text("Read-only access. Copy the link while viewing the tab to import. Rows are reviewed before saving.")
            OutlinedTextField(url, { url = it }, label = { Text("Google Sheets link") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = {
            error = null
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(SHEETS_READONLY))).build()
            Identity.getAuthorizationClient(activity).authorize(request)
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        val sender = result.pendingIntent?.intentSender
                        if (sender != null) resolution.launch(IntentSenderRequest.Builder(sender).build())
                    } else {
                        val token = result.accessToken
                        if (token.isNullOrBlank()) error = "Google did not grant Sheets read access. Please approve the permission and try again."
                        else scope.launch { read(token) }
                    }
                }
                .addOnFailureListener { error = authorizationError(it) }
        }, enabled = !loading && url.isNotBlank()) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
