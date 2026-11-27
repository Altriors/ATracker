package app.atracker.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atracker.ATrackerApp
import app.atracker.notify.Notifications
import app.atracker.data.backup.AutoBackup
import app.atracker.data.backup.BackupManager
import app.atracker.data.backup.MalImport
import app.atracker.data.backup.RestoreResult
import app.atracker.data.backup.exportCsv
import app.atracker.data.backup.exportJson
import app.atracker.data.db.AppSettingEntity
import app.atracker.ui.common.formatDate
import app.atracker.ui.common.vm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val KEY_THEME = "theme"

class SettingsViewModel(private val app: ATrackerApp) : ViewModel() {
    private val settings = app.database.settingDao()
    val theme: StateFlow<String?> = settings.observe(KEY_THEME).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val autoFolder: StateFlow<String?> = settings.observe(AutoBackup.KEY_FOLDER).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastAuto: StateFlow<String?> = settings.observe(AutoBackup.KEY_LAST).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val autoError: StateFlow<String?> = settings.observe(AutoBackup.KEY_LAST_ERROR).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val libraryCount: StateFlow<Int> = app.database.libraryDao().observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    val restartNeeded = MutableStateFlow(false)
    fun dismiss() { _message.value = null }

    val diagnostics = MutableStateFlow<List<String>?>(null)
    fun testConnections() {
        diagnostics.value = listOf("Testing…")
        viewModelScope.launch { diagnostics.value = app.catalogueRepository.diagnose() }
    }

    fun setTheme(v: String) { viewModelScope.launch { settings.put(AppSettingEntity(KEY_THEME, v)) } }

    fun backupTo(uri: Uri) = io("Backup saved.") {
        app.contentResolver.openOutputStream(uri)!!.use { BackupManager.backupTo(app, app.database, it) }
    }

    fun writeText(uri: Uri, csv: Boolean) = io(if (csv) "CSV exported." else "JSON exported.") {
        val text = if (csv) exportCsv(app.database) else exportJson(app.database)
        app.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { BackupManager.restoreFrom(app, app.contentResolver.openInputStream(uri)!!) }
                when (r) {
                    RestoreResult.Restored -> restartNeeded.value = true
                    is RestoreResult.Rejected -> _message.value = "Restore cancelled: ${r.reason} Your data was not changed."
                }
            } catch (e: Exception) {
                _message.value = "Restore failed: ${e.message}. Your data was not changed."
            }
        }
    }

    fun importMal(uri: Uri) {
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)!!.use { MalImport.parse(it) } }
                if (items.isEmpty()) { _message.value = "No anime or manga entries were found in that file."; return@launch }
                val s = MalImport.import(app.database, app.mediaRepository, items)
                _message.value = "Imported ${s.imported} titles. ${s.skippedExisting} were already in your library and were left untouched." +
                    if (s.failed > 0) " ${s.failed} could not be imported." else ""
            } catch (e: Exception) {
                _message.value = "Import failed: ${e.message}. Is this a MyAnimeList XML export?"
            }
        }
    }

    fun setAutoFolder(uri: Uri) {
        viewModelScope.launch {
            settings.put(AppSettingEntity(AutoBackup.KEY_FOLDER, uri.toString()))
            AutoBackup.schedule(app, true)
        }
    }

    fun setAutoEnabled(on: Boolean) { AutoBackup.schedule(app, on); _message.value = if (on) "Automatic daily backup is on." else "Automatic backup is off." }

    fun backupNow() {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { AutoBackup.run(app) }
            r.onSuccess { settings.put(AppSettingEntity(AutoBackup.KEY_LAST, System.currentTimeMillis().toString())); _message.value = "Backup written: $it" }
                .onFailure { _message.value = "Backup failed: ${it.message}" }
        }
    }

    private fun io(ok: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() }; _message.value = ok } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }
}

@Composable
fun SettingsScreen(app: ATrackerApp, onRestart: () -> Unit, onOpenMal: () -> Unit) {
    val model = vm { SettingsViewModel(app) }
    val theme by model.theme.collectAsState()
    val folder by model.autoFolder.collectAsState()
    val last by model.lastAuto.collectAsState()
    val autoError by model.autoError.collectAsState()
    val count by model.libraryCount.collectAsState()
    val message by model.message.collectAsState()
    val restartNeeded by model.restartNeeded.collectAsState()
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    var autoOn by remember { mutableStateOf(true) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(model::backupTo) }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> model.writeText(u, false) } }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> model.writeText(u, true) } }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirmRestore = it }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            model.setAutoFolder(uri)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Section("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (v, label) ->
                    FilterChip((theme ?: "system") == v, { model.setTheme(v) }, { Text(label) })
                }
            }
        }

        Section("Release calendar") {
            val cal by remember { app.database.settingDao().observe("calendar.enabled") }.collectAsState(initial = null)
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show the release calendar in My Library", Modifier.weight(1f))
                Switch(cal != "off", { on -> scope.launch { app.database.settingDao().put(AppSettingEntity("calendar.enabled", if (on) "on" else "off")) } })
            }
        }

        Section("Notifications") {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val rel by remember { app.database.settingDao().observe(Notifications.KEY_RELEASES) }.collectAsState(initial = null)
            val rem by remember { app.database.settingDao().observe(Notifications.KEY_REMINDER) }.collectAsState(initial = null)
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            fun set(key: String, on: Boolean, other: Boolean) {
                scope.launch {
                    app.database.settingDao().put(AppSettingEntity(key, if (on) "on" else "off"))
                    Notifications.schedule(ctx, on || other)
                }
                if (on && android.os.Build.VERSION.SDK_INT >= 33 && Notifications.needsPermission(ctx)) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Release alerts"); Text("When an episode or chapter in your library is due within 24 hours. Only dates a source reported.", style = MaterialTheme.typography.labelSmall) }
                Switch(rel == "on", { on -> set(Notifications.KEY_RELEASES, on, rem == "on") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Progress reminder"); Text("If you follow titles but haven't updated any for 3 days.", style = MaterialTheme.typography.labelSmall) }
                Switch(rem == "on", { on -> set(Notifications.KEY_REMINDER, on, rel == "on") })
            }
        }

        Section("Online catalogues") {
            val diag by model.diagnostics.collectAsState()
            OutlinedButton(onClick = { model.testConnections() }, Modifier.fillMaxWidth()) { Text("Test connections") }
            diag?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Searches every source (AniList, MangaDex, Kitsu, MangaUpdates, Wikidata) with a known title and shows exactly what came back on this phone.", style = MaterialTheme.typography.labelSmall)
        }

        Section("Your data") {
            Text("$count titles in your library. Everything is stored only on this phone.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Important: uninstalling ATracker deletes this data. Make a backup first, and keep it outside the app (Drive, a computer, etc.). Updating by installing a newer APK over the old one keeps your data.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
            )
        }

        Section("Backup and restore") {
            OutlinedButton(onClick = { backupLauncher.launch(BackupManager.suggestedName()) }, Modifier.fillMaxWidth()) { Text("Save a full backup…") }
            OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Restore from a backup…") }
            Text("A full backup contains your library, history, franchises and settings. Restoring replaces the current data; a safety copy is kept.", style = MaterialTheme.typography.bodySmall)
        }

        Section("Automatic backup") {
            Text(if (folder == null) "No folder chosen." else "Folder chosen. A backup is written once a day and the newest 7 are kept.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { folderLauncher.launch(null) }, Modifier.fillMaxWidth()) { Text(if (folder == null) "Choose backup folder…" else "Change backup folder…") }
            if (folder != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily automatic backup", Modifier.weight(1f))
                    Switch(autoOn, { autoOn = it; model.setAutoEnabled(it) })
                }
                OutlinedButton(onClick = { model.backupNow() }, Modifier.fillMaxWidth()) { Text("Back up now") }
                last?.toLongOrNull()?.let { Text("Last backup: ${formatDate(it)}", style = MaterialTheme.typography.bodySmall) }
                if (!autoError.isNullOrBlank()) Text("Last automatic backup failed: $autoError", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text("Tip: pick a folder that syncs (for example in Google Drive) so a backup survives losing the phone.", style = MaterialTheme.typography.bodySmall)
        }

        Section("Export and import") {
            OutlinedButton(onClick = { jsonLauncher.launch("atracker-export.json") }, Modifier.fillMaxWidth()) { Text("Export as JSON (library + history)") }
            OutlinedButton(onClick = { csvLauncher.launch("atracker-library.csv") }, Modifier.fillMaxWidth()) { Text("Export library as CSV") }
            OutlinedButton(onClick = onOpenMal, modifier = Modifier.fillMaxWidth()) { Text("MyAnimeList: connect, import, history…") }
            Text(
                "Import from your MyAnimeList account or an export file. You see a preview first, and nothing you already track is overwritten unless you choose it.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Section("About") {
            Text("ATracker is private and offline-first. Metadata comes from AniList, MangaDex and Wikidata and never overwrites your progress, ratings or notes. Anything the providers lack can be added manually.", style = MaterialTheme.typography.bodySmall)
        }
    }

    message?.let {
        AlertDialog(onDismissRequest = model::dismiss, text = { Text(it) }, confirmButton = { TextButton(onClick = model::dismiss) { Text("OK") } })
    }
    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("This replaces everything currently in ATracker with the contents of the backup. A safety copy of the current data is kept. The app will restart.") },
            confirmButton = { TextButton(onClick = { confirmRestore = null; model.restore(uri) }) { Text("Restore", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } }
        )
    }
    if (restartNeeded) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Backup restored") },
            text = { Text("ATracker needs to restart to load the restored data.") },
            confirmButton = { TextButton(onClick = onRestart) { Text("Restart now") } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
