package com.noncey.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.noncey.android.data.ApiClient
import com.noncey.android.data.ConfigCache
import com.noncey.android.data.Prefs
import com.noncey.android.data.SpoolDb
import com.noncey.android.data.TraceLog

class NonceyApp : Application() {

    companion object {
        const val CHANNEL_FORWARD = "noncey_forward"
        const val CHANNEL_AUTH    = "noncey_auth"
        const val NOTIF_RELOGIN   = 1002
        lateinit var instance: NonceyApp
            private set
    }

    val prefs    by lazy { Prefs(this) }
    val db       by lazy { SpoolDb.build(this) }
    val api      by lazy { ApiClient.build(this, prefs) }
    val cache    by lazy { ConfigCache(api, prefs, db.cachedConfigDao()) }
    val traceLog by lazy { TraceLog(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i("NonceyBuild", "Build time: ${java.util.Date(BuildConfig.BUILD_TIME)}")
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FORWARD,
                "SMS forwarding",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Background SMS forwarding to noncey daemon" }
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AUTH,
                "Authentication",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Re-login required when session expires" }
        )
    }
}
