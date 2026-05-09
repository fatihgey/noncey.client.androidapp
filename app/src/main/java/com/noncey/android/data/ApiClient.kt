package com.noncey.android.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.noncey.android.NonceyApp
import com.noncey.android.R
import com.noncey.android.ui.MainActivity
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val lock = Any()
    private val gson = Gson()

    private class TokenRefresher(
        private val context: Context,
        private val prefs: Prefs
    ) : Authenticator {

        override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
            synchronized(lock) {
                // If another thread already rotated the token, just retry with the new one
                val current = prefs.token
                val used    = response.request.header("Authorization")?.removePrefix("Bearer ")
                if (current.isNotEmpty() && current != used) {
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $current")
                        .build()
                }

                val rt = prefs.refreshToken
                if (rt.isEmpty()) { clearAndNotify(); return null }

                val fresh = tryRefresh(prefs.daemonUrl, rt)
                if (fresh == null) { clearAndNotify(); return null }

                prefs.token          = fresh.token
                prefs.tokenExpiresAt = fresh.expires_at
                prefs.refreshToken   = fresh.refresh_token

                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${fresh.token}")
                    .build()
            }
        }

        private fun tryRefresh(baseUrl: String, refreshToken: String): RefreshResponse? {
            val rawClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val url  = baseUrl.trimEnd('/') + "/api/auth/refresh"
            val body = gson.toJson(RefreshRequest(refreshToken))
                .toRequestBody("application/json".toMediaType())
            val req  = Request.Builder().url(url).post(body).build()
            return try {
                val resp = rawClient.newCall(req).execute()
                if (!resp.isSuccessful) return null
                val text = resp.body?.string() ?: return null
                gson.fromJson(text, RefreshResponse::class.java)
            } catch (_: Exception) {
                null
            }
        }

        private fun clearAndNotify() {
            prefs.clear()
            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
                PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, NonceyApp.CHANNEL_AUTH)
                .setContentTitle("noncey — re-login required")
                .setContentText("Your session has expired. Tap to log in again.")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(NonceyApp.NOTIF_RELOGIN, notification)
        }
    }

    fun build(context: Context, prefs: Prefs): ApiService {
        val baseUrl = prefs.daemonUrl.trimEnd('/').ifEmpty { "https://localhost" } + "/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(buildClient(context, prefs))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun rebuild(context: Context, prefs: Prefs): ApiService = build(context, prefs)

    private fun buildClient(context: Context, prefs: Prefs): OkHttpClient {
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
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(TokenRefresher(context, prefs))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
