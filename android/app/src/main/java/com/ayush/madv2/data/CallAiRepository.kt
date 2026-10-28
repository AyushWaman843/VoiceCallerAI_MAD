package com.ayush.madv2.data

import com.ayush.madv2.network.BasicResponseDto
import com.ayush.madv2.network.CallAiApi
import com.ayush.madv2.network.AudioPreviewResponseDto
import com.ayush.madv2.network.CallsResponseDto
import com.ayush.madv2.network.NetworkModule
import com.ayush.madv2.network.ScheduleCallRequestDto
import com.ayush.madv2.network.ScheduleCallResponseDto
import com.ayush.madv2.network.TranscriptionResponseDto
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File

class CallAiRepository(
    private val gson: Gson = Gson(),
) {
    suspend fun transcribeAudio(baseUrl: String, file: File): TranscriptionResponseDto {
        val requestBody = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
        val audioPart = MultipartBody.Part.createFormData("audio", file.name, requestBody)
        return authenticated(baseUrl) { it.transcribeAudio(audioPart) }
    }

    suspend fun scheduleCall(baseUrl: String, request: ScheduleCallRequestDto): ScheduleCallResponseDto {
        return authenticated(baseUrl) { it.scheduleCall(request) }
    }

    suspend fun previewAudio(baseUrl: String, message: String): AudioPreviewResponseDto =
        authenticated(baseUrl) { it.previewAudio(mapOf("message" to message)) }

    suspend fun getCalls(baseUrl: String, userId: String): CallsResponseDto {
        return authenticated(baseUrl) { it.getCalls(userId) }
    }

    suspend fun cancelCall(baseUrl: String, jobId: String): BasicResponseDto {
        return authenticated(baseUrl) { it.cancelCall(jobId) }
    }

    fun toPrettyJson(value: Any): String = gson.toJson(value)

    private suspend fun <T> authenticated(baseUrl: String, request: suspend (CallAiApi) -> Response<T>): T {
        val token = AccountSession.accessToken(baseUrl)
        val response = request(NetworkModule.createApi(baseUrl, token))
        if (response.code() == 401) {
            // A response from an old request must not sign out a newer session.
            if (AccountSession.account.value?.accessToken == token) AccountSession.clear()
            throw SignInRequired()
        }
        return response.bodyOrThrow()
    }

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (isSuccessful) {
            return body() ?: throw IllegalStateException("The server returned an empty response.")
        }

        val errorBody = errorBody()?.string().orEmpty()
        val parsedMessage = runCatching {
            gson.fromJson(errorBody, BasicResponseDto::class.java).message
        }.getOrNull()
        throw IllegalStateException(parsedMessage ?: "Request failed with HTTP ${code()}.")
    }
}
