package com.ayush.madv2.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH

data class AccountUser(val id: String, val email: String, @SerializedName("full_name") val fullName: String)
data class AuthCredentials(val email: String, val password: String, @SerializedName("full_name") val fullName: String? = null)
data class RefreshCredentials(@SerializedName("refresh_token") val refreshToken: String)
data class ProfileResponse(val status: String, val user: AccountUser,
                           val organization_name: String?, val timezone: String?)
data class AuthResponse(
    val status: String, val message: String? = null, val user: AccountUser? = null,
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 3600,
)

interface AuthApi {
    @POST("auth/signup") suspend fun signup(@Body body: AuthCredentials): Response<AuthResponse>
    @POST("auth/login") suspend fun login(@Body body: AuthCredentials): Response<AuthResponse>
    @POST("auth/refresh") suspend fun refresh(@Body body: RefreshCredentials): Response<AuthResponse>
    @POST("auth/logout") suspend fun logout(): Response<BasicResponseDto>
    @GET("auth/me") suspend fun me(): Response<AuthResponse>
    @PATCH("auth/profile") suspend fun profile(@Body body: Map<String, String>): Response<ProfileResponse>
}
