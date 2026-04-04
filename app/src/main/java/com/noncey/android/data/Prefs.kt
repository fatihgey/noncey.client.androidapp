package com.noncey.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Prefs(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "noncey_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to plain prefs if encryption unavailable (emulator/test)
        context.getSharedPreferences("noncey_prefs_plain", Context.MODE_PRIVATE)
    }

    var daemonUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_URL, v).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TOKEN, v).apply()

    var tokenExpiresAt: String
        get() = prefs.getString(KEY_EXPIRES, "") ?: ""
        set(v) = prefs.edit().putString(KEY_EXPIRES, v).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(v) = prefs.edit().putString(KEY_USERNAME, v).apply()

    /** Spool retention in minutes (default 10). */
    var spoolRetentionMinutes: Int
        get() = prefs.getInt(KEY_SPOOL_RETENTION, 10)
        set(v) = prefs.edit().putInt(KEY_SPOOL_RETENTION, v).apply()

    /** Spool retry interval in seconds (default 5). */
    var spoolRetrySeconds: Int
        get() = prefs.getInt(KEY_SPOOL_RETRY, 5)
        set(v) = prefs.edit().putInt(KEY_SPOOL_RETRY, v).apply()

    /** Country calling code to prepend when SIM country code is unavailable (e.g. "49"). */
    var countryCallingCode: String
        get() = prefs.getString(KEY_COUNTRY_CODE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_COUNTRY_CODE, v).apply()

    /** Master switch: auto-forward SMS matching active configs. */
    var autoForwardEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FWD, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_FWD, v).apply()

    fun isLoggedIn() = token.isNotEmpty()

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_URL            = "daemon_url"
        private const val KEY_TOKEN          = "token"
        private const val KEY_EXPIRES        = "token_expires_at"
        private const val KEY_USERNAME       = "username"
        private const val KEY_SPOOL_RETENTION = "spool_retention_min"
        private const val KEY_SPOOL_RETRY    = "spool_retry_sec"
        private const val KEY_COUNTRY_CODE   = "country_calling_code"
        private const val KEY_AUTO_FWD       = "auto_forward_enabled"
    }
}
