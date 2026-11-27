package app.atracker.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.atracker.ATrackerApp
import app.atracker.data.DiscoverFilters
import app.atracker.data.DiscoverSort
import app.atracker.data.applyFilters
import app.atracker.data.applyLocalFilters
import app.atracker.domain.PublicationStatus
import app.atracker.ui.common.Picker
import app.atracker.data.provider.BrowseKind
import app.atracker.domain.MediaType
import app.atracker.ui.common.EmptyState
import app.atracker.ui.common.MediaRow
import app.atracker.ui.common.subtitle
import app.atracker.ui.common.vm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(app: ATrackerApp, onOpen: (Long) -> Unit, onManual: (String) -> Unit) {
    val model = vm { DiscoverViewModel(app) }
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var openError by remember { mutableStateOf<String?>(null) }
    var addedMessage by remember { mutableStateOf<String?>(null) }
    val history by model.history.collectAsState()

    LazyColumn(Modifier.fillMaxSize().testTag("discover-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = model::setQuery,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
                singleLine = true,
                placeholder = { Text("Search anime, manga, manhwa, novels…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { model.submit() })
            )
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = state.type == null, onClick = { model.setType(null) }, label = { Text("All") }) }
                items(MediaType.entries.toList()) { t ->
                    FilterChip(selected = state.type == t, onClick = { model.setType(t) }, label = { Text(t.label) })
                }
            }
        }
        if (state.query.isBlank() && history.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent searches", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = model::clearHistory) { Text("Clear") }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history) { h -> androidx.compose.material3.AssistChip(onClick = { model.useHistory(h) }, label = { Text(h) }) }
                }
            }
        }
        if (state.query.isBlank() && state.type != null) {
            item {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.kind == BrowseKind.POPULAR, onClick = { model.setKind(BrowseKind.POPULAR) }, label = { Text("Popular") })
                    FilterChip(selected = state.kind == BrowseKind.RECENT, onClick = { model.setKind(BrowseKind.RECENT) }, label = { Text("Recently released") })
                }
            }
        }
        item {
            val f = state.filters
            val all = state.outcome?.remote.orEmpty()
            val genres = all.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(40)
            val years = (java.time.Year.now().value downTo 1960 step 5).toList()
            Row(Modifier.padding(horizontal = 4.dp)) {
                Picker("Genre", f.genre, genres, { it }, { model.setFilters(f.copy(genre = it)) })
                Picker("Since", f.yearFrom, years, { it.toString() }, { model.setFilters(f.copy(yearFrom = it)) })
            }
            Row(Modifier.padding(horizontal = 4.dp)) {
                Picker("Status", f.status, PublicationStatus.entries.toList(), { it.label }, { model.setFilters(f.copy(status = it)) })
                Picker("Min score", f.minScore, listOf(60, 70, 75, 80, 85), { (it / 10.0).toString() + "+" }, { model.setFilters(f.copy(minScore = it)) })
                Picker("Sort", f.sort, DiscoverSort.entries.toList(), { it.label }, { it?.let { s -> model.setFilters(f.copy(sort = s)) } }, anyLabel = null)
            }
            if (f.active) Text("Filters apply to the results shown here (they do not change what is in your library).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (state.loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            }
        }
        addedMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) } }
        openError?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }

        val raw = state.outcome
        val outcome = raw?.copy(local = raw.local.applyLocalFilters(state.filters), remote = raw.remote.applyFilters(state.filters))
        if (outcome != null) {
            if (outcome.local.isNotEmpty()) {
                item { SectionTitle("Already in your catalogue") }
                items(outcome.local, key = { "l${it.id}" }) { m ->
                    MediaRow(m.title, m.subtitle(), m.coverUrl, badge = "Saved on this device") { onOpen(m.id) }
                }
            }
            if (outcome.remote.isNotEmpty()) {
                item { SectionTitle(if (state.query.isBlank()) "Online" else "Online results") }
                items(outcome.remote, key = { "r${it.provider}${it.externalId}" }) { m ->
                    MediaRow(m.title, m.subtitle(), m.coverUrl, badge = "via ${providerName(m.provider)}", trailing = {
                        androidx.compose.material3.IconButton(onClick = {
                            scope.launch {
                                try {
                                    val added = model.addToLibrary(m)
                                    addedMessage = if (added) "Added “${m.title}” to your library." else "“${m.title}” is already in your library."
                                } catch (e: Exception) { openError = "Could not add title: ${e.message}" }
                            }
                        }) { Icon(Icons.Filled.Add, contentDescription = "Add ${m.title} to library") }
                    }) {
                        scope.launch {
                            openError = null
                            try {
                                val id = model.open(m)
                                withContext(Dispatchers.Main.immediate) { onOpen(id) }
                            } catch (e: Exception) { openError = "Could not open title: ${e.message}" }
                        }
                    }
                }
            }
            if (outcome.staleProviders.isNotEmpty()) {
                item {
                    Text(
                        "Showing saved results from ${outcome.staleProviders.joinToString { providerName(it) }} (offline or unavailable).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp, 8.dp)
                    )
                }
            }
            if (outcome.errors.isNotEmpty()) {
                item {
                    Column(Modifier.padding(16.dp, 8.dp)) {
                        outcome.errors.forEach { (p, msg) ->
                            Text("${providerName(p)}: $msg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        OutlinedButton(onClick = { model.submit() }) { Text("Retry") }
                    }
                }
            }
            val nothing = outcome.local.isEmpty() && outcome.remote.isEmpty()
            if (nothing && !state.loading && state.query.isNotBlank()) {
                item {
                    EmptyState(
                        "No results for “${state.query.trim()}”",
                        "Not every title exists in the online catalogues, especially web novels. You can add it yourself and track it like any other title." +
                            (if (outcome.counts.isNotEmpty()) "\n\nAsked: " + outcome.counts.entries.joinToString { (p, n) -> "${providerName(p)} returned $n" } +
                                ". If a well-known title returns 0 everywhere, open Settings → Test connections." else "") +
                            (if (state.filters.active) "\n\nFilters are active and may be hiding results." else "")
                    )
                }
            }
        } else if (!state.loading && state.query.isBlank() && state.type == null) {
            item { EmptyState("Search or pick a category", "Type a title above, or choose a category to browse popular titles.") }
        }

        if (state.type == MediaType.WEB_NOVEL && state.query.isBlank()) {
            item { EmptyState("Web novels can't be browsed", "There is no catalogue to browse for web novels. Search by name, or add one manually.") }
        }

        if (state.query.isNotBlank() && !state.loading) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { onManual(state.query.trim()) }) { Text("Can't find it? Add “${state.query.trim().take(30)}” manually") }
                }
            }
        } else if (!state.loading) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedButton(onClick = { onManual("") }) { Text("Add a title manually") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
}

private fun providerName(id: String) = when (id) {
    "anilist" -> "AniList"
    "mangadex" -> "MangaDex"
    "wikidata" -> "Wikidata"
    "jikan" -> "Jikan"
    else -> id
}
