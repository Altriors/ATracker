package app.atracker.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.atracker.ATrackerApp
import app.atracker.data.db.LibraryItem
import app.atracker.data.trackedTotal
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaType
import app.atracker.ui.common.Cover
import app.atracker.ui.common.EmptyState
import app.atracker.ui.common.MediaRow
import app.atracker.ui.common.progressText
import app.atracker.ui.common.subtitle
import app.atracker.ui.common.vm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class HomeViewModel(private val app: ATrackerApp) : ViewModel() {
    val items: StateFlow<List<LibraryItem>> = app.database.libraryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun plusOne(mediaId: Long) { viewModelScope.launch { app.libraryRepository.incrementProgress(mediaId) } }
}

@Composable
fun HomeScreen(app: ATrackerApp, onOpen: (Long) -> Unit, onDiscover: () -> Unit) {
    val model = vm { HomeViewModel(app) }
    val items by model.items.collectAsState()

    if (items.isEmpty()) {
        EmptyState(
            "Welcome to ATracker",
            "Your private anime, manga, manhwa, manhua and novel library. Everything you track stays on this device.",
            action = { androidx.compose.material3.Button(onClick = onDiscover) { Text("Find something to track") } }
        )
        return
    }

    val current = items.filter { it.entry.status == LibraryStatus.CURRENT.name }.sortedByDescending { it.entry.lastActivityAt ?: it.entry.updatedAt }
    val watching = current.filter { MediaType.from(it.media.mediaType).isWatching }
    val reading = current.filter { !MediaType.from(it.media.mediaType).isWatching }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (watching.isNotEmpty()) {
            item { Header("Continue watching") }
            items(watching.take(8), key = { "w${it.media.id}" }) { QuickRow(it, onOpen, model::plusOne) }
        }
        if (reading.isNotEmpty()) {
            item { Header("Continue reading") }
            items(reading.take(8), key = { "r${it.media.id}" }) { QuickRow(it, onOpen, model::plusOne) }
        }
        item { Header("Recently updated") }
        item { Shelf(items.sortedByDescending { it.entry.updatedAt }.take(12), onOpen) }
        item { Header("Recently added") }
        item { Shelf(items.sortedByDescending { it.entry.addedAt }.take(12), onOpen) }
        val favourites = items.filter { it.entry.favorite }
        if (favourites.isNotEmpty()) {
            item { Header("Favourites") }
            item { Shelf(favourites.take(12), onOpen) }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 6.dp))
}

@Composable
private fun QuickRow(item: LibraryItem, onOpen: (Long) -> Unit, plusOne: (Long) -> Unit) {
    val type = MediaType.from(item.media.mediaType)
    MediaRow(
        title = item.media.title, subtitle = item.media.subtitle(), coverUrl = item.media.coverUrl,
        extra = progressText(item.entry.progress, item.media.trackedTotal(), type.progressUnit),
        trailing = {
            FilledTonalIconButton(onClick = { plusOne(item.media.id) }) { Icon(Icons.Filled.Add, "Add one ${type.progressUnit.singular}") }
        }
    ) { onOpen(item.media.id) }
}

@Composable
private fun Shelf(items: List<LibraryItem>, onOpen: (Long) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.media.id }) { item ->
            Column(Modifier.width(104.dp).clickable { onOpen(item.media.id) }) {
                Cover(item.media.coverUrl, Modifier.size(width = 104.dp, height = 148.dp), corner = 10.dp)
                Text(item.media.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
