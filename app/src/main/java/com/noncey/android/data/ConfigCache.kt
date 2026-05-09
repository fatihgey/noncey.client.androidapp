package com.noncey.android.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory cache of SMS-channel configurations, synced from the daemon.
 *
 * Used by SmsReceiver (on the main/receiver thread) to gate auto-forward
 * without a network round-trip.  The cache holds only the data needed for
 * matching: config id, activated flag, and the list of SMS matchers.
 *
 * On first use the cache is seeded from Room (survives process kill); on each
 * successful network refresh the Room copy is also updated.
 */
class ConfigCache(
    private val api: ApiService,
    private val prefs: Prefs,
    private val dao: CachedConfigDao
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
    @Volatile private var persistenceLoaded = false
    @Volatile var lastError: Int? = null

    private val REFRESH_INTERVAL_MS = 60_000L
    private val gson = Gson()
    private val matcherListType = object : TypeToken<List<SmsMatcher>>() {}.type

    /** Full list of SMS-channel configs (owned + subscribed). Used by the Configs tab. */
    fun allConfigs(): List<SmsConfig> = configs

    /**
     * Returns the first active config whose matchers fire for [senderPhone] + [body],
     * or null if none match.  Thread-safe; reads from the in-memory snapshot.
     */
    fun matchSmsConfig(senderPhone: String, body: String): SmsConfig? =
        configs.firstOrNull { cfg ->
            cfg.activated && cfg.matchers.any { m ->
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

    fun matchesSms(senderPhone: String, body: String): Boolean =
        matchSmsConfig(senderPhone, body) != null

    /**
     * Refresh from the daemon if the cache is stale.  On first call, seeds the
     * in-memory cache from Room so SMS matching works immediately after a cold start.
     */
    suspend fun refreshIfStale() {
        if (!persistenceLoaded) loadFromPersistence()
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
                val fresh = body
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
                configs = fresh
                lastRefreshMs = System.currentTimeMillis()
                lastError = null
                dao.deleteAll()
                dao.insertAll(fresh.map { it.toCachedEntry() })
            } else {
                lastError = resp.code()
            }
        } catch (_: Exception) {
            // Network unavailable — keep stale cache
        }
    }

    /** Invalidate the cache (e.g. after activate/deactivate). */
    fun invalidate() { lastRefreshMs = 0L }

    private suspend fun loadFromPersistence() = withContext(Dispatchers.IO) {
        val entries = dao.getAll()
        if (entries.isNotEmpty()) {
            configs = entries.map { it.toSmsConfig() }
        }
        persistenceLoaded = true
    }

    private fun SmsConfig.toCachedEntry() = CachedConfigEntry(
        configId     = id,
        name         = name,
        activated    = activated,
        isOwned      = isOwned,
        matchersJson = gson.toJson(matchers)
    )

    private fun CachedConfigEntry.toSmsConfig() = SmsConfig(
        id        = configId,
        name      = name,
        activated = activated,
        isOwned   = isOwned,
        matchers  = gson.fromJson(matchersJson, matcherListType)
    )
}
