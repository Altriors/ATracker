package app.atracker.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.atracker.ATrackerApp
import app.atracker.data.LibrarySort
import app.atracker.data.db.LibraryItem
import app.atracker.data.trackedTotal
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaType
import app.atracker.domain.PublicationStatus
import app.atracker.ui.common.Cover
import app.atracker.ui.common.EmptyState
import app.atracker.ui.common.MediaRow
import app.atracker.ui.common.Picker
import app.atracker.ui.common.chipLabel
import app.atracker.ui.common.progressText
import app.atracker.ui.common.ratingText
import app.atracker.ui.common.subtitle
import app.atracker.ui.common.vm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(app: ATrackerApp, onOpen: (Long) -> Unit, onDiscover: () -> Unit) {
    val model = vm { LibraryViewModel(app) }
    val items by model.items.collectAsState()
    val filters by model.filters.collectAsState()
    val total by model.totalCount.collectAsState()
    val genres by model.allGenres.collectAsState()
    val years by model.allYears.collectAsState()
    var grid by rememberSaveable { mutableStateOf(true) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp, 8.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filters.query, onValueChange = { model.update { f -> f.copy(query = it) } },
                modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Search your library") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }
            )
            IconButton(onClick = { grid = !grid }) {
                Icon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, if (grid) "List view" else "Grid view")
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(filters.type == null, { model.update { f -> f.copy(type = null) } }, { Text("All") }) }
            items(MediaType.entries.toList()) { t ->
                FilterChip(filters.type == t, { model.update { f -> f.copy(type = t) } }, { Text(t.label) })
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(filters.status == null, { model.update { f -> f.copy(status = null) } }, { Text("Any status") }) }
            items(LibraryStatus.entries.toList()) { s ->
                FilterChip(filters.status == s, { model.update { f -> f.copy(status = s) } }, { Text(s.chipLabel(filters.type)) })
            }
        }
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Picker("Sort", filters.sort, LibrarySort.entries.toList(), { it.label }, { it?.let { s -> model.update { f -> f.copy(sort = s) } } }, anyLabel = null)
            Picker("More filters${if (filters.genre != null || filters.year != null || filters.pubStatus != null || filters.minRating != null) " •" else ""}", null as String?, emptyList(), { it }, { }, anyLabel = null,
                modifier = Modifier.clickable { showFilters = !showFilters })
            Text("${items.size} of $total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton(onClick = { model.update { f -> f.copy(favoritesOnly = !f.favoritesOnly) } }) {
                Icon(Icons.Filled.Favorite, "Favourites only", tint = if (filters.favoritesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showFilters) {
            Row(Modifier.padding(horizontal = 4.dp)) {
                Picker("Genre", filters.genre, genres, { it }, { g -> model.update { f -> f.copy(genre = g) } })
                Picker("Year", filters.year, years, { it.toString() }, { y -> model.update { f -> f.copy(year = y) } })
            }
            Row(Modifier.padding(horizontal = 4.dp)) {
                Picker("Publication", filters.pubStatus, PublicationStatus.entries.toList(), { it.label }, { p -> model.update { f -> f.copy(pubStatus = p) } })
                Picker("Min rating", filters.minRating, listOf(50, 60, 70, 80, 90), { ratingText(it) + "+" }, { r -> model.update { f -> f.copy(minRating = r) } })
            }
        }

        when {
            total == 0 -> EmptyState(
                "Your library is empty",
                "Find something in Discover and add it. Nothing from the online catalogue is added unless you choose it.",
                action = { androidx.compose.material3.Button(onClick = onDiscover) { Text("Discover titles") } }
            )
            items.isEmpty() -> EmptyState("Nothing matches", "Try removing a filter or changing the search.")
            grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                gridItems(items, key = { it.media.id }) { GridCard(it, onOpen) }
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items, key = { it.media.id }) { item ->
                    val type = MediaType.from(item.media.mediaType)
                    MediaRow(
                        title = item.media.title, subtitle = item.media.subtitle(), coverUrl = item.media.coverUrl,
                        extra = LibraryStatus.from(item.entry.status).label(type) + " · " +
                            progressText(item.entry.progress, item.media.trackedTotal(), type.progressUnit),
                        badge = item.entry.rating?.let { "Your rating ${ratingText(it)}/10" }
                    ) { onOpen(item.media.id) }
                }
            }
        }
    }
}

@Composable
private fun GridCard(item: LibraryItem, onOpen: (Long) -> Unit) {
    val type = MediaType.from(item.media.mediaType)
    val total = item.media.trackedTotal()
    Column(Modifier.clickable { onOpen(item.media.id) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f)) {
            Cover(item.media.coverUrl, Modifier.fillMaxSize(), corner = 10.dp)
            if (item.entry.favorite) {
                Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Text(item.media.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(
            progressText(item.entry.progress, total, type.progressUnit), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
