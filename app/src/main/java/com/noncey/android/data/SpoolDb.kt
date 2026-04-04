package com.noncey.android.data

import android.content.Context
import androidx.room.*

// ── Entity ────────────────────────────────────────────────────────────────────

@Entity(tableName = "spool")
data class SpoolEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: String,         // ISO-8601 from the device clock at reception
    val enqueuedAt: Long,           // System.currentTimeMillis()
    val configId: Int? = null       // non-null when manually funnelled into a config
)

// ── DAO ───────────────────────────────────────────────────────────────────────

@Dao
interface SpoolDao {
    @Insert
    suspend fun insert(entry: SpoolEntry): Long

    @Query("SELECT * FROM spool ORDER BY enqueuedAt ASC")
    suspend fun getAll(): List<SpoolEntry>

    @Delete
    suspend fun delete(entry: SpoolEntry)

    @Query("DELETE FROM spool WHERE enqueuedAt < :cutoffMs")
    suspend fun deleteExpiredBefore(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM spool")
    suspend fun count(): Int
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(entities = [SpoolEntry::class], version = 1, exportSchema = false)
abstract class SpoolDb : RoomDatabase() {
    abstract fun spoolDao(): SpoolDao

    companion object {
        @Volatile private var INSTANCE: SpoolDb? = null

        fun build(context: Context): SpoolDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpoolDb::class.java,
                    "noncey_spool"
                ).build().also { INSTANCE = it }
            }
    }
}
