package com.ayush.madv2.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CallAiApi {
    @POST("schedule-call")
    suspend fun scheduleCall(@Body request: ScheduleCallRequestDto): Response<ScheduleCallResponseDto>

    @GET("calls")
    suspend fun getCalls(@Query("user_id") userId: String): Response<CallsResponseDto>

    @DELETE("calls/{jobId}")
    suspend fun cancelCall(@Path("jobId") jobId: String): Response<BasicResponseDto>
}
