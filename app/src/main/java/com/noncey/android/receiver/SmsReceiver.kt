package com.noncey.android.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.TelephonyManager
import com.noncey.android.NonceyApp
import com.noncey.android.data.SpoolEntry
import com.noncey.android.service.ForwardService
import com.noncey.android.util.PhoneNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val app = context.applicationContext as NonceyApp
        if (!app.prefs.autoForwardEnabled) return
        if (!app.prefs.isLoggedIn()) return

        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
        val format = intent.getStringExtra("format") ?: "3gpp"

        // Reconstruct (potentially multi-part) messages grouped by sender
        val messages = pdus.mapNotNull { pdu ->
            SmsMessage.createFromPdu(pdu as ByteArray, format)
        }
        if (messages.isEmpty()) return

        val rawSender  = messages.first().originatingAddress ?: return
        val body       = messages.joinToString("") { it.messageBody ?: "" }
        val receivedAt = Instant.now()
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        // Normalise sender phone number
        val tm          = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simCountry  = tm.simCountryIso?.takeIf { it.isNotEmpty() }
        val senderPhone = PhoneNormalizer.normalize(
            rawSender,
            simCountry,
            app.prefs.countryCallingCode
        )

        // Ensure the cache is fresh before matching (handles cold-start after process kill)
        runBlocking { app.cache.refreshIfStale() }

        // Check against cached matchers; log result and drop if no active config matches
        val matchedConfig = app.cache.matchSmsConfig(senderPhone, body)
        if (matchedConfig == null) {
            val preview = body.take(40).replace('\n', ' ')
            app.traceLog.add("No match: from=$senderPhone  body=\"$preview\"")
            return
        }
        app.traceLog.add("Forwarded: from=$senderPhone → '${matchedConfig.name}'")

        // Mark the SMS as Read in the system inbox
        markAsRead(context, rawSender, body)

        // Enqueue to spool and start ForwardService
        CoroutineScope(Dispatchers.IO).launch {
            app.db.spoolDao().insert(
                SpoolEntry(
                    sender     = senderPhone,
                    body       = body,
                    receivedAt = receivedAt,
                    enqueuedAt = System.currentTimeMillis()
                )
            )
            ForwardService.start(context)
        }
    }

    private fun markAsRead(context: Context, sender: String, body: String) {
        try {
            val uri    = Uri.parse("content://sms/inbox")
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("_id"),
                "address=? AND body=? AND read=0",
                arrayOf(sender, body),
                "date DESC"
            ) ?: return
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val values = ContentValues().apply { put("read", 1) }
                    context.contentResolver.update(
                        Uri.parse("content://sms/$id"), values, null, null
                    )
                }
            }
        } catch (_: Exception) {
            // Permission denied or ContentResolver unavailable — non-fatal
        }
    }
}
