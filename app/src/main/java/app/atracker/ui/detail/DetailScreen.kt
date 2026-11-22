package app.atracker.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.atracker.ATrackerApp
import app.atracker.data.db.LibraryEntryEntity
import app.atracker.data.db.MediaEntity
import app.atracker.data.trackedTotal
import app.atracker.domain.CreditRole
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaType
import app.atracker.domain.ProgressUnit
import app.atracker.ui.common.Cover
import app.atracker.ui.common.Picker
import app.atracker.ui.common.formatDate
import app.atracker.ui.common.progressText
import app.atracker.ui.common.ratingText
import app.atracker.ui.common.subtitle
import app.atracker.ui.common.totalLabel
import app.atracker.ui.common.vm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(app: ATrackerApp, mediaId: Long, onBack: () -> Unit, onEditManual: (Long) -> Unit) {
    val model = vm(key = "detail-$mediaId") { DetailViewModel(app, mediaId) }
    val media by model.media.collectAsState()
    val entry by model.entry.collectAsState()
    val message by model.message.collectAsState()
    val refreshing by model.refreshing.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); model.dismissMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(media?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    val m = media
                    if (m != null && m.isManual) {
                        IconButton(onClick = { onEditManual(mediaId) }) { Icon(Icons.Filled.Edit, "Edit details") }
                    }
                    if (m != null && !m.isManual) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp))
                        else IconButton(onClick = model::refresh) { Icon(Icons.Filled.Refresh, "Refresh metadata") }
                    }
                    if (entry != null) {
                        IconButton(onClick = { model.setFavorite(!(entry?.favorite ?: false)) }) {
                            Icon(if (entry?.favorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favourite")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val m = media
        if (m == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp)); CircularProgressIndicator()
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                Header(model, m)
                TrackingCard(model, m, entry)
                InfoSection(model, m)
            }
        }
    }
}

@Composable
private fun Header(model: DetailViewModel, m: MediaEntity) {
    val titles by model.titles.collectAsState()
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        Cover(m.coverUrl, Modifier.size(width = 120.dp, height = 172.dp), corner = 10.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.titleLarge)
            if (m.titleNative != null && m.titleNative != m.title) {
                Text(m.titleNative, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(m.subtitle(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(m.totalLabel(), style = MaterialTheme.typography.bodySmall)
            if (m.startDate != null) {
                Text("Started ${m.startDate}${m.endDate?.let { " · ended $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
            }
            m.averageScore?.let {
                Text("Community score ${it / 10.0}/10", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (m.isManual) Text("Added manually", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (titles.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Also known as: " + titles.take(6).joinToString(" · ") { it.title },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TrackingCard(model: DetailViewModel, m: MediaEntity, entry: LibraryEntryEntity?) {
    val type = MediaType.from(m.mediaType)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (entry == null) {
                Text("Not in your library", style = MaterialTheme.typography.titleMedium)
                Text("Nothing is saved to your personal collection until you add it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.add(LibraryStatus.PLANNED) }) { Text(LibraryStatus.PLANNED.label(type)) }
                    OutlinedButton(onClick = { model.add(LibraryStatus.CURRENT) }) { Text(LibraryStatus.CURRENT.label(type)) }
                }
                return@Column
            }

            val status = LibraryStatus.from(entry.status)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Picker("", status, LibraryStatus.entries.toList(), { it.label(type) }, { it?.let(model::setStatus) }, anyLabel = null)
            }

            // Progress in this type's own unit (episodes, chapters or volumes).
            val unit = type.progressUnit
            val total = m.trackedTotal()
            Spacer(Modifier.height(4.dp))
            Text(progressText(entry.progress, total, unit), style = MaterialTheme.typography.headlineSmall)
            if (total != null && total > 0) {
                LinearProgressIndicator(progress = { (entry.progress.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
            Stepper(entry.progress, onSet = model::setProgress, step = 1, label = unit.plural)
            if (unit == ProgressUnit.CHAPTER && type.hasVolumes) {
                Spacer(Modifier.height(8.dp))
                Text("Volumes read: ${entry.progressVolumes}" + (m.totalVolumes?.let { " / $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                Stepper(entry.progressVolumes, onSet = model::setVolumes, step = 1, label = "volumes")
            }
            if (entry.repeatCount > 0) {
                Text("Run ${entry.repeatCount + 1} · ${entry.repeatCount} earlier ${if (type.isWatching) "rewatch" else "reread"}${if (entry.repeatCount > 1) "es" else ""} completed",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (status == LibraryStatus.COMPLETED) {
                TextButton(onClick = model::startNewRun) { Text(if (type.isWatching) "Start a rewatch" else "Start a reread") }
            }

            Spacer(Modifier.height(12.dp))
            Text("Rating: ${ratingText(entry.rating)} / 10", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = ((entry.rating ?: 0) / 5).toFloat(),
                    onValueChange = { model.setRating(it.toInt() * 5) },
                    valueRange = 0f..20f, steps = 19, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { model.setRating(null) }, enabled = entry.rating != null) { Text("Clear") }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Started ${formatDate(entry.startedAt)} · Completed ${formatDate(entry.completedAt)} · Last activity ${formatDate(entry.lastActivityAt)}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            NotesEditor(model, entry)

            var confirm by remember { mutableStateOf(false) }
            TextButton(onClick = { confirm = true }) { Text("Remove from library", color = MaterialTheme.colorScheme.error) }
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("Remove from library?") },
                    text = { Text("This deletes your progress, rating and notes for this title. Your activity history is kept.") },
                    confirmButton = { TextButton(onClick = { confirm = false; model.remove() }) { Text("Remove") } },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
                )
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, onSet: (Int) -> Unit, step: Int, label: String) {
    var dialog by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onSet(value - step) }, enabled = value > 0) { Icon(Icons.Filled.Remove, "Decrease") }
        Button(onClick = { onSet(value + step) }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("+$step") }
        OutlinedButton(onClick = { onSet(value + 10) }) { Text("+10") }
        TextButton(onClick = { dialog = true }) { Text("Set…") }
    }
    if (dialog) {
        var text by remember { mutableStateOf(value.toString()) }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Set $label") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
            },
            confirmButton = { TextButton(onClick = { dialog = false; text.toIntOrNull()?.let(onSet) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NotesEditor(model: DetailViewModel, entry: LibraryEntryEntity) {
    var notes by remember(entry.notes) { mutableStateOf(entry.notes.orEmpty()) }
    var review by remember(entry.review) { mutableStateOf(entry.review.orEmpty()) }
    val dirty = notes != entry.notes.orEmpty() || review != entry.review.orEmpty()
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(notes, { notes = it }, label = { Text("Personal notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(review, { review = it }, label = { Text("Review") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    if (dirty) TextButton(onClick = { model.saveNotes(notes, review) }) { Text("Save notes") }
}

@Composable
private fun InfoSection(model: DetailViewModel, m: MediaEntity) {
    val genres by model.genres.collectAsState()
    val credits by model.credits.collectAsState()
    val sources by model.sources.collectAsState()
    val uri = LocalUriHandler.current
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.padding(16.dp)) {
        Text("Synopsis", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        if (m.synopsis == null) {
            Text("No synopsis available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(m.synopsis, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Read more") }
        }

        val g = genres.filter { it.kind == "GENRE" }.map { it.name }
        val t = genres.filter { it.kind == "TAG" }.map { it.name }
        if (g.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text("Genres: ${g.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium) }
        if (t.isNotEmpty()) Text("Tags: ${t.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        fun names(r: CreditRole) = credits.filter { it.role == r.name }.map { it.name }.distinct()
        listOf(CreditRole.AUTHOR to "Author", CreditRole.ARTIST to "Artist", CreditRole.STUDIO to "Studio", CreditRole.PUBLISHER to "Publisher").forEach { (role, label) ->
            val n = names(role)
            if (n.isNotEmpty()) Text("$label: ${n.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
        }

        val external = sources.filter { it.provider != "manual" }
        if (external.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Data from", style = MaterialTheme.typography.titleSmall)
            external.forEach { s ->
                val name = when (s.provider) { "anilist" -> "AniList"; "mangadex" -> "MangaDex"; "wikidata" -> "Wikidata"; else -> s.provider }
                if (s.url != null) TextButton(onClick = { runCatching { uri.openUri(s.url) } }) { Text("$name (open page)") }
                else Text(name, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
