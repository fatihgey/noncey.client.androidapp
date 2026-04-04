package com.noncey.android.data

import retrofit2.Response
import retrofit2.http.*

// ── Request / response models ─────────────────────────────────────────────────

data class LoginRequest(val username: String, val password: String, val client_type: String = "android")
data class LoginResponse(val token: String, val expires_at: String)

data class ConfigResponse(
    val id: Int,
    val name: String,
    val version: String,
    val status: String,
    val visibility: String,
    val activated: Boolean?,
    val is_owned: Boolean,
    val channel_types: List<String>,
    val sms_senders: List<String>,
    val prompt: PromptData?
)

data class PromptData(val url: String, val selector: String, val url_match: String)

data class SmsIngestRequest(
    val sender: String,
    val body: String,
    val received_at: String,
    val config_id: Int? = null
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<LoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/configs")
    suspend fun getConfigs(): Response<List<ConfigResponse>>

    @POST("api/configs/{id}/activate")
    suspend fun activate(@Path("id") id: Int): Response<Unit>

    @POST("api/configs/{id}/deactivate")
    suspend fun deactivate(@Path("id") id: Int): Response<Unit>

    @DELETE("api/subscriptions/{configId}")
    suspend fun unsubscribe(@Path("configId") configId: Int): Response<Unit>

    @POST("api/sms/ingest")
    suspend fun ingestSms(@Body req: SmsIngestRequest): Response<Unit>
}
