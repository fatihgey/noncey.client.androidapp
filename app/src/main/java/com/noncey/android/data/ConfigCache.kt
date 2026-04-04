package com.noncey.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory cache of SMS-channel configurations, synced from the daemon.
 *
 * Used by SmsReceiver (on the main/receiver thread) to gate auto-forward
 * without a network round-trip.  The cache holds only the data needed for
 * matching: config id, activated flag, and the list of sender phone numbers.
 */
class ConfigCache(
    private val api: ApiService,
    private val prefs: Prefs
) {
    data class SmsConfig(
        val id: Int,
        val name: String,
        val activated: Boolean,
        val isOwned: Boolean,
        val senderPhones: List<String>
    )

    @Volatile private var configs: List<SmsConfig> = emptyList()
    @Volatile private var lastRefreshMs: Long = 0L
    private val REFRESH_INTERVAL_MS = 60_000L   // 60 s

    /** Full list of SMS-channel configs (owned + subscribed). Used by the Configs tab. */
    fun allConfigs(): List<SmsConfig> = configs

    /**
     * Returns true if *senderPhone* matches any sender in an active config.
     * Thread-safe; reads from the in-memory snapshot.
     */
    fun matchesSender(senderPhone: String): Boolean =
        configs.any { it.activated && senderPhone in it.senderPhones }

    /**
     * Refresh from the daemon if the cache is stale.  Call from a coroutine.
     * Safe to call redundantly — no-ops if called within the refresh interval.
     */
    suspend fun refreshIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshMs < REFRESH_INTERVAL_MS) return
        refresh()
    }

    /** Force a full refresh from the daemon. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (!prefs.isLoggedIn()) return@withContext
        try {
            val resp = api.getConfigs()
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext
                configs = body
                    .filter { cfg -> cfg.channel_types.contains("sms") }
                    .map { cfg ->
                        SmsConfig(
                            id           = cfg.id,
                            name         = cfg.name,
                            activated    = cfg.activated ?: false,
                            isOwned      = cfg.is_owned,
                            senderPhones = cfg.sms_senders
                        )
                    }
                lastRefreshMs = System.currentTimeMillis()
            }
        } catch (_: Exception) {
            // Network unavailable — keep stale cache
        }
    }

    /** Invalidate the cache (e.g. after activate/deactivate). */
    fun invalidate() { lastRefreshMs = 0L }
}
