package com.ayush.madv2.data

import com.ayush.madv2.network.BasicResponseDto
import com.ayush.madv2.network.CallAiApi
import com.ayush.madv2.network.CallsResponseDto
import com.ayush.madv2.network.NetworkModule
import com.ayush.madv2.network.ScheduleCallRequestDto
import com.ayush.madv2.network.ScheduleCallResponseDto
import com.google.gson.Gson
import retrofit2.Response

class CallAiRepository(
    private val gson: Gson = Gson(),
) {
    suspend fun scheduleCall(baseUrl: String, request: ScheduleCallRequestDto): ScheduleCallResponseDto {
        val response = createApi(baseUrl).scheduleCall(request)
        return response.bodyOrThrow()
    }

    suspend fun getCalls(baseUrl: String, userId: String): CallsResponseDto {
        val response = createApi(baseUrl).getCalls(userId)
        return response.bodyOrThrow()
    }

    suspend fun cancelCall(baseUrl: String, jobId: String): BasicResponseDto {
        val response = createApi(baseUrl).cancelCall(jobId)
        return response.bodyOrThrow()
    }

    fun toPrettyJson(value: Any): String = gson.toJson(value)

    private fun createApi(baseUrl: String): CallAiApi = NetworkModule.createApi(baseUrl)

    private inline fun <reified T> Response<T>.bodyOrThrow(): T {
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
