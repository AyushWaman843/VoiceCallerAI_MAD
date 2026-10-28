package com.ayush.madv2.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    // Reuse sockets and worker threads; credentials still belong to each service instance.
    private val sharedClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    fun createApi(baseUrl: String, accessToken: String? = null): CallAiApi =
        createService(baseUrl, CallAiApi::class.java, accessToken)

    fun <T> createService(baseUrl: String, service: Class<T>, accessToken: String? = null): T {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Auth requests contain passwords and tokens; never log headers or bodies.
            level = HttpLoggingInterceptor.Level.NONE
        }
        val client = sharedClient.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                if (accessToken != null) request.header("Authorization", "Bearer $accessToken")
                chain.proceed(request.build())
            }
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(service)
    }
}
