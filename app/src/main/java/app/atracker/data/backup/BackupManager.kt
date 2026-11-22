package app.atracker.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.atracker.data.db.AppDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream

sealed interface RestoreResult {
    data object Restored : RestoreResult
    data class Rejected(val reason: String) : RestoreResult
}

/**
 * Full backups are a consistent copy of the SQLite database file, so they contain everything
 * (library, history, franchises, lists, settings and the cached catalogue).
 */
object BackupManager {
    private val HEADER = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

    fun dbFile(context: Context): File = context.getDatabasePath(AppDatabase.FILE_NAME)

    /** Flushes the write-ahead log into the main file, then streams the database out. */
    fun backupTo(context: Context, db: AppDatabase, out: OutputStream) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        dbFile(context).inputStream().use { it.copyTo(out) }
        out.flush()
    }

    fun suggestedName(now: Long = System.currentTimeMillis()): String {
        val t = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
        return "atracker-backup-%04d%02d%02d-%02d%02d.db".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute)
    }

    /** Checks that [file] is a usable ATracker database without touching the live one. */
    fun validate(file: File): String? {
        if (!file.exists() || file.length() < 100) return "That file is empty or too small to be a backup."
        val head = ByteArray(HEADER.size)
        file.inputStream().use { if (it.read(head) != head.size) return "Not an ATracker backup." }
        if (!head.contentEquals(HEADER)) return "Not an ATracker backup (it is not a database file)."
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
                val version = d.rawQuery("PRAGMA user_version", null).use { c -> c.moveToFirst(); c.getInt(0) }
                val current = AppDatabase.VERSION
                if (version > current) return "This backup was made by a newer version of ATracker (schema $version). Update the app first."
                val tables = d.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(0)) }
                }
                if (!tables.containsAll(listOf("media", "library_entry", "activity_event"))) return "Not an ATracker backup (tables are missing)."
                val ok = d.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" }
                if (!ok) return "The backup file is damaged."
            }
            null
        } catch (e: Exception) {
            "Could not read the backup: ${e.message}"
        }
    }

    /**
     * Replaces the live database with the backup. A copy of the current database is kept in
     * `files/pre-restore/` first. The app must be restarted afterwards.
     */
    fun restoreFrom(context: Context, input: InputStream, now: Long = System.currentTimeMillis()): RestoreResult {
        val temp = File(context.cacheDir, "restore-incoming.db")
        input.use { src -> temp.outputStream().use { src.copyTo(it) } }
        validate(temp)?.let { temp.delete(); return RestoreResult.Rejected(it) }

        val live = dbFile(context)
        if (live.exists()) {
            AppDatabase.get(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            val safety = File(context.filesDir, "pre-restore").apply { mkdirs() }
            live.copyTo(File(safety, "before-restore-$now.db"), overwrite = true)
            safety.listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
        }
        AppDatabase.reset()
        File(live.path + "-wal").delete()
        File(live.path + "-shm").delete()
        File(live.path + "-journal").delete()
        temp.copyTo(live, overwrite = true)
        temp.delete()
        return RestoreResult.Restored
    }
}
