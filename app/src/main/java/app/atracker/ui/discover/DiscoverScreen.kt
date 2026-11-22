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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.atracker.ATrackerApp
import app.atracker.data.provider.BrowseKind
import app.atracker.domain.MediaType
import app.atracker.ui.common.EmptyState
import app.atracker.ui.common.MediaRow
import app.atracker.ui.common.subtitle
import app.atracker.ui.common.vm
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(app: ATrackerApp, onOpen: (Long) -> Unit, onManual: (String) -> Unit) {
    val model = vm { DiscoverViewModel(app) }
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var openError by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
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
        if (state.query.isBlank() && state.type != null) {
            item {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.kind == BrowseKind.POPULAR, onClick = { model.setKind(BrowseKind.POPULAR) }, label = { Text("Popular") })
                    FilterChip(selected = state.kind == BrowseKind.RECENT, onClick = { model.setKind(BrowseKind.RECENT) }, label = { Text("Recently released") })
                }
            }
        }
        if (state.loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            }
        }
        openError?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }

        val outcome = state.outcome
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
                    MediaRow(m.title, m.subtitle(), m.coverUrl, badge = "via ${providerName(m.provider)}") {
                        scope.launch {
                            openError = null
                            try { onOpen(model.open(m)) } catch (e: Exception) { openError = "Could not open title: ${e.message}" }
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
                        "Not every title exists in the online catalogues, especially web novels. You can add it yourself and track it like any other title."
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
