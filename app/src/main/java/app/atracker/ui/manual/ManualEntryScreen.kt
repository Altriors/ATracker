package app.atracker.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.atracker.ATrackerApp
import app.atracker.domain.Credit
import app.atracker.domain.CreditRole
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.ProgressUnit
import app.atracker.domain.Providers
import app.atracker.domain.PublicationStatus
import app.atracker.ui.common.Picker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Creates or edits a hand-entered title. Every field except the title is optional and left
 * unknown when blank, so nothing is ever invented. Used when no online provider has the title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(app: ATrackerApp, editId: Long?, prefillTitle: String, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(editId == null) }
    var title by remember { mutableStateOf(prefillTitle) }
    var native by remember { mutableStateOf("") }
    var alts by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MediaType.WEB_NOVEL) }
    var status by remember { mutableStateOf(PublicationStatus.UNKNOWN) }
    var total by remember { mutableStateOf("") }
    var volumes by remember { mutableStateOf("") }
    var authors by remember { mutableStateOf("") }
    var genres by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }
    var synopsis by remember { mutableStateOf("") }
    var addToLibrary by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(editId) {
        if (editId != null) {
            val dao = app.database.mediaDao()
            dao.getById(editId)?.let { m ->
                title = m.title; native = m.titleNative.orEmpty(); type = MediaType.from(m.mediaType)
                status = PublicationStatus.from(m.pubStatus); year = m.startYear?.toString().orEmpty()
                cover = m.coverUrl.orEmpty(); synopsis = m.synopsis.orEmpty()
                total = when (type.progressUnit) {
                    ProgressUnit.EPISODE -> m.totalEpisodes
                    ProgressUnit.VOLUME -> m.totalVolumes
                    else -> m.totalChapters
                }?.toString().orEmpty()
                volumes = if (type.progressUnit == ProgressUnit.CHAPTER) m.totalVolumes?.toString().orEmpty() else ""
                alts = dao.titlesFor(editId).joinToString(", ") { it.title }
            }
            genres = app.database.mediaDao().observeGenresOnce(editId)
            authors = app.database.mediaDao().observeAuthorsOnce(editId)
            loaded = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (editId == null) "Add title manually" else "Edit title") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Use this when no online catalogue has the title. Leave anything you don't know blank; unknown values stay unknown.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(title, { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = error != null && title.isBlank())

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Type", Modifier.weight(1f))
                Picker("", type, MediaType.entries.toList(), { it.label }, { it?.let { t -> type = t } }, anyLabel = null)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Publication status", Modifier.weight(1f))
                Picker("", status, PublicationStatus.entries.toList(), { it.label }, { it?.let { s -> status = s } }, anyLabel = null)
            }

            val unit = type.progressUnit
            OutlinedTextField(
                total, { total = it.filter(Char::isDigit).take(6) },
                label = { Text("Total ${unit.plural} (blank = unknown)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (unit == ProgressUnit.CHAPTER && type.hasVolumes) {
                OutlinedTextField(
                    volumes, { volumes = it.filter(Char::isDigit).take(4) },
                    label = { Text("Total volumes (blank = unknown)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            OutlinedTextField(native, { native = it }, label = { Text("Original-language title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(alts, { alts = it }, label = { Text("Alternative titles (comma separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(authors, { authors = it }, label = { Text("Author(s) (comma separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(genres, { genres = it }, label = { Text("Genres (comma separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Start year") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(cover, { cover = it }, label = { Text("Cover image URL (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(synopsis, { synopsis = it }, label = { Text("Synopsis") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

            if (editId == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(addToLibrary, { addToLibrary = it })
                    Text("Add to my library right away")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (title.isBlank()) { error = "A title is required."; return@Button }
                    val totalNum = total.toIntOrNull()?.takeIf { it > 0 }
                    val volNum = volumes.toIntOrNull()?.takeIf { it > 0 }
                    fun split(s: String) = s.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                    val yearNum = year.toIntOrNull()?.takeIf { it in 1000..2999 }
                    val meta = MediaMetadata(
                        provider = Providers.MANUAL, externalId = "manual", mediaType = type, title = title.trim(),
                        titleNative = native.trim().ifBlank { null },
                        alternativeTitles = split(alts), synopsis = synopsis.trim().ifBlank { null },
                        coverUrl = cover.trim().ifBlank { null }, pubStatus = status,
                        totalEpisodes = if (unit == ProgressUnit.EPISODE) totalNum else null,
                        totalChapters = if (unit == ProgressUnit.CHAPTER) totalNum else null,
                        totalVolumes = if (unit == ProgressUnit.VOLUME) totalNum else if (unit == ProgressUnit.CHAPTER) volNum else null,
                        startYear = yearNum, startDate = yearNum?.toString(),
                        genres = split(genres), credits = split(authors).map { Credit(CreditRole.AUTHOR, it) }
                    )
                    scope.launch {
                        try {
                            val id = if (editId == null) app.mediaRepository.createManual(meta).also {
                                if (addToLibrary) app.libraryRepository.addToLibrary(it, LibraryStatus.PLANNED)
                            } else editId.also { app.mediaRepository.updateManual(it, meta) }
                            withContext(Dispatchers.Main.immediate) { onSaved(id) }
                        } catch (e: Exception) {
                            error = "Could not save: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (editId == null) "Save title" else "Save changes") }
        }
    }
}
