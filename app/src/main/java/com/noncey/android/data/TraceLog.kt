package com.noncey.android.data

import android.content.Context
import java.io.File

/**
 * Simple persistent trace log.  Entries older than 24 h are purged on each
 * read or write.  Thread-safe via a plain synchronized block — reads happen
 * on the UI thread, writes from the IO thread (SmsReceiver via runBlocking).
 */
class TraceLog(context: Context) {

    data class Entry(val epochMs: Long, val message: String)

    private val file  = File(context.filesDir, "trace.log")
    private val lock  = Any()
    private val list  = mutableListOf<Entry>()

    companion object {
        private const val RETENTION_MS = 24 * 60 * 60 * 1000L
        private const val SEP = '\t'
    }

    init { load() }

    fun add(message: String) {
        val entry = Entry(System.currentTimeMillis(), message)
        synchronized(lock) {
            list.add(entry)
            persist()
        }
    }

    /** Returns entries newest-first, already purged of anything older than 24 h. */
    fun entries(): List<Entry> {
        synchronized(lock) {
            purgeOld()
            return list.toList().sortedByDescending { it.epochMs }
        }
    }

    private fun load() {
        synchronized(lock) {
            if (!file.exists()) return
            val cutoff = System.currentTimeMillis() - RETENTION_MS
            file.readLines().forEach { line ->
                val tab = line.indexOf(SEP)
                if (tab == -1) return@forEach
                val ms = line.substring(0, tab).toLongOrNull() ?: return@forEach
                if (ms >= cutoff) list.add(Entry(ms, line.substring(tab + 1)))
            }
        }
    }

    private fun purgeOld() {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        list.removeAll { it.epochMs < cutoff }
    }

    private fun persist() {
        purgeOld()
        file.writeText(list.joinToString("\n") { "${it.epochMs}$SEP${it.message}" })
    }
}
