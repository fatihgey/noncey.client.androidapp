package com.noncey.android.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    fun build(prefs: Prefs): ApiService {
        val authInterceptor = Interceptor { chain ->
            val token = prefs.token
            val req = if (token.isNotEmpty()) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(req)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        // Base URL is set at login time; here we use a placeholder that is
        // replaced dynamically via RebuildableApiService wrapper when the URL changes.
        val baseUrl = prefs.daemonUrl.trimEnd('/').ifEmpty { "https://localhost" } + "/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /** Build a fresh ApiService after login (URL or token may have changed). */
    fun rebuild(prefs: Prefs): ApiService = build(prefs)
}
