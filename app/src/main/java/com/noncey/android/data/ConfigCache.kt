package com.noncey.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory cache of SMS-channel configurations, synced from the daemon.
 *
 * Used by SmsReceiver (on the main/receiver thread) to gate auto-forward
 * without a network round-trip.  The cache holds only the data needed for
 * matching: config id, activated flag, and the list of SMS matchers.
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
        val matchers: List<SmsMatcher>
    )

    @Volatile private var configs: List<SmsConfig> = emptyList()
    @Volatile private var lastRefreshMs: Long = 0L
    private val REFRESH_INTERVAL_MS = 60_000L   // 60 s

    /** Full list of SMS-channel configs (owned + subscribed). Used by the Configs tab. */
    fun allConfigs(): List<SmsConfig> = configs

    /**
     * Returns true if *senderPhone* + *body* matches any active config matcher.
     * A matcher fires when:
     *   - sender_phone matches (if set), AND
     *   - body_pattern matches (if set) according to body_match_type.
     * Thread-safe; reads from the in-memory snapshot.
     */
    fun matchesSms(senderPhone: String, body: String): Boolean =
        configs.any { cfg ->
            if (!cfg.activated) return@any false
            cfg.matchers.any { m ->
                val senderOk = m.sender_phone == null || m.sender_phone == senderPhone
                val bodyOk = when {
                    m.body_pattern == null -> true
                    m.body_match_type == "starts_with" -> body.startsWith(m.body_pattern)
                    m.body_match_type == "regex" -> Regex(m.body_pattern).containsMatchIn(body)
                    else -> true
                }
                senderOk && bodyOk
            }
        }

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
                            id        = cfg.id,
                            name      = cfg.name,
                            activated = cfg.activated ?: false,
                            isOwned   = cfg.is_owned,
                            matchers  = cfg.sms_matchers
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
