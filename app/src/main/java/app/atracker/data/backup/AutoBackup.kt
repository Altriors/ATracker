package app.atracker.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.atracker.ATrackerApp
import app.atracker.data.db.AppSettingEntity
import java.util.concurrent.TimeUnit

object AutoBackup {
    const val KEY_FOLDER = "autobackup.folder"
    const val KEY_LAST = "autobackup.last"
    const val KEY_LAST_ERROR = "autobackup.error"
    private const val WORK = "atracker-auto-backup"
    private const val KEEP = 7

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS).build())
    }

    /** Writes one backup into the folder the user picked (Storage Access Framework) and prunes old ones. */
    suspend fun run(app: ATrackerApp): Result<String> = runCatching {
        val folder = app.database.settingDao().get(KEY_FOLDER) ?: error("No backup folder chosen")
        val tree = Uri.parse(folder)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = BackupManager.suggestedName()
        val doc = DocumentsContract.createDocument(app.contentResolver, parent, "application/octet-stream", name)
            ?: error("Could not create a file in the backup folder")
        app.contentResolver.openOutputStream(doc)!!.use { BackupManager.backupTo(app, app.database, it) }
        prune(app, tree)
        name
    }

    private fun prune(app: ATrackerApp, tree: Uri) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = ArrayList<Pair<String, String>>()
        app.contentResolver.query(
            children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(1)
                if (n.startsWith("atracker-backup-") && n.endsWith(".db")) found += c.getString(0) to n
            }
        }
        found.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(app.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val app = applicationContext as ATrackerApp
            val r = AutoBackup.run(app)
            val dao = app.database.settingDao()
            r.onSuccess {
                dao.put(AppSettingEntity(KEY_LAST, System.currentTimeMillis().toString()))
                dao.put(AppSettingEntity(KEY_LAST_ERROR, ""))
            }.onFailure { dao.put(AppSettingEntity(KEY_LAST_ERROR, it.message ?: "failed")) }
            return if (r.isSuccess) Result.success() else Result.retry()
        }
    }
}
