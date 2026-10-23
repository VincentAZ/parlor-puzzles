package com.parlor.core.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "attempts", indices = [Index(value = ["levelId", "mode", "completed"])])
data class AttemptRow(
    @PrimaryKey val id: String,
    val levelId: String,
    val mode: String,
    val completed: Boolean,
    val updatedAt: Long,
    val json: String,
    @ColumnInfo(defaultValue = "'TRAFFIC_JAM'") val puzzle: String = "TRAFFIC_JAM",
    val challengeDate: String? = null,
)

@Entity(tableName = "bests")
data class BestRow(@PrimaryKey val levelId: String, val json: String,
                   @ColumnInfo(defaultValue = "'TRAFFIC_JAM'") val puzzle: String = "TRAFFIC_JAM")

@Entity(tableName = "player")
data class PlayerRow(@PrimaryKey val id: Int = 1, val ledgerJson: String, val totalPlayTimeMs: Long = 0,
                     @ColumnInfo(defaultValue = "'{}'") val profileJson: String = "{}")

@Entity(tableName = "daily")
data class DailyRow(@PrimaryKey val date: String, val puzzle: String, val json: String)

@Entity(tableName = "completions")
data class CompletionRow(@PrimaryKey val attemptId: String, val completionDate: String)

data class CompletedAttemptRow(
    @Embedded val attempt: AttemptRow,
    val completionDate: String?,
    val dailyJson: String?,
)

/** The recovery snapshot and pending settings commit travel atomically with imported progress. */
@Entity(tableName = "backup_state")
data class BackupStateRow(@PrimaryKey val id: Int = 1, val recoveryJson: String?, val pendingSettingsJson: String?, val lastImportId: String?)

/** Every image row is at most 512 KiB, including on devices with small CursorWindows. */
@Entity(tableName = "photos")
data class PhotoRow(@PrimaryKey val id: String, val jpeg: ByteArray)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM photos WHERE id = :id") suspend fun photo(id: String): PhotoRow?
    @Query("SELECT id FROM photos") suspend fun photoIds(): List<String>
    @Query("SELECT COALESCE(SUM(length(jpeg)), 0) FROM photos") suspend fun photoBytes(): Long
    @Upsert suspend fun savePhoto(row: PhotoRow)
    @Query("DELETE FROM photos WHERE id NOT IN (:keep)") suspend fun prunePhotos(keep: List<String>)
    @Query("UPDATE backup_state SET recoveryJson = :json WHERE id = 1") suspend fun replaceRecovery(json: String)
    @Query("SELECT * FROM attempts ORDER BY updatedAt, id") suspend fun allAttempts(): List<AttemptRow>
    @Query("SELECT * FROM completions") suspend fun allCompletions(): List<CompletionRow>
    @Query("DELETE FROM daily") suspend fun clearDailies()
    @Query("SELECT pendingSettingsJson FROM backup_state WHERE id = 1") suspend fun pendingSettings(): String?
    @Query("SELECT EXISTS(SELECT 1 FROM backup_state WHERE id = 1 AND recoveryJson IS NOT NULL)") suspend fun hasRecovery(): Boolean
    @Query("SELECT length(recoveryJson) FROM backup_state WHERE id = 1") suspend fun recoveryLength(): Int?
    @Query("SELECT substr(recoveryJson, :start, :count) FROM backup_state WHERE id = 1") suspend fun recoveryPart(start: Int, count: Int): String?
    @Query("UPDATE backup_state SET pendingSettingsJson = NULL WHERE id = 1 AND pendingSettingsJson = :expected")
    suspend fun finishBackupSettings(expected: String): Int
    @Query("UPDATE backup_state SET pendingSettingsJson = :settings, lastImportId = :snapshotId WHERE id = 1")
    suspend fun restoreBackupSettings(settings: String, snapshotId: String): Int
    @Upsert suspend fun saveBackupState(row: BackupStateRow)
    @Query("SELECT * FROM attempts WHERE id = :id") suspend fun attempt(id: String): AttemptRow?
    @Query("SELECT * FROM attempts WHERE levelId = :levelId AND mode = :mode AND completed = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun unfinished(levelId: String, mode: String): AttemptRow?
    @Query("SELECT * FROM attempts WHERE completed = 0 AND mode IN ('CAMPAIGN','DAILY') ORDER BY updatedAt DESC LIMIT 1")
    fun observeResume(): Flow<AttemptRow?>
    @Query("SELECT * FROM attempts WHERE id = :id") fun observeAttempt(id: String): Flow<AttemptRow?>
    @Upsert suspend fun saveAttempt(row: AttemptRow)
    @Query("DELETE FROM attempts WHERE id = :id") suspend fun deleteAttempt(id: String)
    @Query("SELECT * FROM bests") fun observeBests(): Flow<List<BestRow>>
    @Query("SELECT * FROM bests") suspend fun allBests(): List<BestRow>
    @Query("SELECT * FROM bests WHERE levelId = :id") suspend fun best(id: String): BestRow?
    @Upsert suspend fun saveBest(row: BestRow)
    @Query("SELECT * FROM player WHERE id = 1") suspend fun player(): PlayerRow?
    @Query("SELECT * FROM player WHERE id = 1") fun observePlayer(): Flow<PlayerRow?>
    @Upsert suspend fun savePlayer(row: PlayerRow)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun recordCompletion(row: CompletionRow): Long
    @Query("SELECT COUNT(*) FROM completions") fun observeCompletionCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM completions") suspend fun completionCount(): Int
    @Query("SELECT EXISTS(SELECT 1 FROM completions WHERE attemptId = :id)") suspend fun wasCompleted(id: String): Boolean
    @Query("SELECT * FROM daily ORDER BY date DESC") fun observeDailies(): Flow<List<DailyRow>>
    @Query("SELECT * FROM daily WHERE date = :date") suspend fun daily(date: String): DailyRow?
    @Query("SELECT * FROM daily") suspend fun allDailies(): List<DailyRow>
    @Upsert suspend fun saveDaily(row: DailyRow)
    @Query("DELETE FROM daily WHERE date < :cutoff AND date NOT IN (:protectedDates)")
    suspend fun pruneDaily(cutoff: String, protectedDates: List<String>)
    @Query("DELETE FROM attempts WHERE mode = 'DAILY' AND challengeDate < :cutoff AND challengeDate NOT IN (:protectedDates)")
    suspend fun pruneDailyAttempts(cutoff: String, protectedDates: List<String>)
    @Query("DELETE FROM attempts WHERE (:puzzle IS NULL OR puzzle = :puzzle)") suspend fun resetAttempts(puzzle: String?)
    @Query("DELETE FROM bests WHERE (:puzzle IS NULL OR puzzle = :puzzle)") suspend fun resetBests(puzzle: String?)
    @Query("SELECT * FROM attempts WHERE completed = 1 AND mode != 'DEMONSTRATION' ORDER BY updatedAt DESC")
    fun observeHistory(): Flow<List<AttemptRow>>
    @Query("""SELECT a.*, c.completionDate, d.json AS dailyJson FROM attempts a
        LEFT JOIN completions c ON c.attemptId = a.id
        LEFT JOIN daily d ON a.mode = 'DAILY' AND d.date = a.challengeDate
        WHERE a.completed = 1 AND a.mode != 'DEMONSTRATION'
        AND (:puzzle IS NULL OR a.puzzle = :puzzle)
        AND (:mode IS NULL OR a.mode = :mode)
        AND (:beforeId IS NULL OR a.updatedAt < :beforeTime OR (a.updatedAt = :beforeTime AND a.id < :beforeId))
        ORDER BY a.updatedAt DESC, a.id DESC LIMIT :limit""")
    fun observeHistoryPage(puzzle: String?, mode: String?, beforeTime: Long, beforeId: String?, limit: Int): Flow<List<CompletedAttemptRow>>
}

@Database(entities = [AttemptRow::class, BestRow::class, PlayerRow::class, CompletionRow::class, DailyRow::class, BackupStateRow::class, PhotoRow::class], version = 4, exportSchema = true)
abstract class ParlorDatabase : RoomDatabase() {
    abstract fun progress(): ProgressDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attempts ADD COLUMN puzzle TEXT NOT NULL DEFAULT 'TRAFFIC_JAM'")
                db.execSQL("ALTER TABLE attempts ADD COLUMN challengeDate TEXT")
                db.execSQL("ALTER TABLE bests ADD COLUMN puzzle TEXT NOT NULL DEFAULT 'TRAFFIC_JAM'")
                db.execSQL("ALTER TABLE player ADD COLUMN profileJson TEXT NOT NULL DEFAULT '{}'")
                // Version 1 contained only Traffic Jam; attribute its saved play time correctly.
                db.execSQL("UPDATE player SET profileJson = '{\"playTimeByPuzzle\":{\"TRAFFIC_JAM\":' || totalPlayTimeMs || '}}'")
                db.execSQL("CREATE TABLE IF NOT EXISTS daily (date TEXT NOT NULL, puzzle TEXT NOT NULL, json TEXT NOT NULL, PRIMARY KEY(date))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS backup_state (id INTEGER NOT NULL, recoveryJson TEXT, pendingSettingsJson TEXT, lastImportId TEXT, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS photos (id TEXT NOT NULL, jpeg BLOB NOT NULL, PRIMARY KEY(id))")
            }
        }
        fun open(context: Context, name: String = "parlor.db", journalMode: JournalMode = JournalMode.AUTOMATIC) = Room.databaseBuilder(context, ParlorDatabase::class.java, name)
            .setJournalMode(journalMode)
            .openHelperFactory(PreservingOpenHelperFactory())
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
