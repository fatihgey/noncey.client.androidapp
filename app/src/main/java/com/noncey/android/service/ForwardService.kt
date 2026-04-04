package com.noncey.android.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.noncey.android.NonceyApp
import com.noncey.android.R
import com.noncey.android.data.SmsIngestRequest
import com.noncey.android.data.SpoolEntry
import com.noncey.android.ui.MainActivity
import kotlinx.coroutines.*

/**
 * Foreground service that drains the spool by forwarding each entry to the daemon.
 *
 * Lifecycle:
 *  - Started by SmsReceiver (auto-forward) or ForwardDialog (manual forward).
 *  - Runs until the spool is empty, then stops itself.
 *  - On network failure: waits [retrySeconds] and retries.
 *  - Entries older than [retentionMs] are purged silently before each send cycle.
 */
class ForwardService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        scope.launch { drainSpool() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun drainSpool() {
        val app           = applicationContext as NonceyApp
        val dao           = app.db.spoolDao()
        val retryMs       = app.prefs.spoolRetrySeconds * 1_000L
        val retentionMs   = app.prefs.spoolRetentionMinutes * 60_000L

        while (true) {
            // Purge expired entries
            dao.deleteExpiredBefore(System.currentTimeMillis() - retentionMs)

            val entries = dao.getAll()
            if (entries.isEmpty()) break

            if (!isNetworkAvailable()) {
                delay(retryMs)
                continue
            }

            var allOk = true
            for (entry in entries) {
                try {
                    val resp = app.api.ingestSms(
                        SmsIngestRequest(
                            sender      = entry.sender,
                            body        = entry.body,
                            received_at = entry.receivedAt,
                            config_id   = entry.configId
                        )
                    )
                    if (resp.isSuccessful || resp.code() == 400) {
                        // 204 = success; 400 = bad request (won't succeed on retry) — both discard
                        dao.delete(entry)
                    } else {
                        allOk = false
                    }
                } catch (_: Exception) {
                    allOk = false
                }
            }

            if (!allOk) delay(retryMs)
        }

        stopSelf()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(nw) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NonceyApp.CHANNEL_FORWARD)
            .setContentTitle("noncey")
            .setContentText("Forwarding SMS…")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ForwardService::class.java))
        }

        /** Enqueue a manual forward (with optional config override) and start the service. */
        fun enqueueAndStart(
            context: Context,
            sender: String,
            body: String,
            receivedAt: String,
            configId: Int? = null
        ) {
            val app = context.applicationContext as NonceyApp
            CoroutineScope(Dispatchers.IO).launch {
                app.db.spoolDao().insert(
                    SpoolEntry(
                        sender     = sender,
                        body       = body,
                        receivedAt = receivedAt,
                        enqueuedAt = System.currentTimeMillis(),
                        configId   = configId
                    )
                )
                start(context)
            }
        }
    }
}
