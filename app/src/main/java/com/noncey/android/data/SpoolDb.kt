package com.noncey.android.data

import android.content.Context
import androidx.room.*

// ── Spool entity ──────────────────────────────────────────────────────────────

@Entity(tableName = "spool")
data class SpoolEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: String,         // ISO-8601 from the device clock at reception
    val enqueuedAt: Long,           // System.currentTimeMillis()
    val configId: Int? = null       // non-null when manually funnelled into a config
)

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

// ── Cached config entity ──────────────────────────────────────────────────────

@Entity(tableName = "cached_configs")
data class CachedConfigEntry(
    @PrimaryKey val configId: Int,
    val name: String,
    val activated: Boolean,
    val isOwned: Boolean,
    val matchersJson: String        // Gson JSON: List<SmsMatcher>
)

@Dao
interface CachedConfigDao {
    @Query("SELECT * FROM cached_configs")
    suspend fun getAll(): List<CachedConfigEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CachedConfigEntry>)

    @Query("DELETE FROM cached_configs")
    suspend fun deleteAll()
}

// ── Database ──────────────────────────────────────────────────────────────────

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cached_configs (" +
            "configId INTEGER NOT NULL PRIMARY KEY, " +
            "name TEXT NOT NULL, " +
            "activated INTEGER NOT NULL, " +
            "isOwned INTEGER NOT NULL, " +
            "matchersJson TEXT NOT NULL)"
        )
    }
}

@Database(
    entities = [SpoolEntry::class, CachedConfigEntry::class],
    version  = 2,
    exportSchema = false
)
abstract class SpoolDb : RoomDatabase() {
    abstract fun spoolDao(): SpoolDao
    abstract fun cachedConfigDao(): CachedConfigDao

    companion object {
        @Volatile private var INSTANCE: SpoolDb? = null

        fun build(context: Context): SpoolDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpoolDb::class.java,
                    "noncey_spool"
                )
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
            }
    }
}
