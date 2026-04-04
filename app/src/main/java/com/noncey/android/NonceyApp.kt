package com.noncey.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.noncey.android.data.ApiClient
import com.noncey.android.data.ConfigCache
import com.noncey.android.data.Prefs
import com.noncey.android.data.SpoolDb

class NonceyApp : Application() {

    companion object {
        const val CHANNEL_FORWARD = "noncey_forward"
        lateinit var instance: NonceyApp
            private set
    }

    val prefs by lazy { Prefs(this) }
    val db     by lazy { SpoolDb.build(this) }
    val api    by lazy { ApiClient.build(prefs) }
    val cache  by lazy { ConfigCache(api, prefs) }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
    }
}
