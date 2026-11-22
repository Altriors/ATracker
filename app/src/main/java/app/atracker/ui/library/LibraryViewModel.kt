package app.atracker.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atracker.ATrackerApp
import app.atracker.data.ItemExtras
import app.atracker.data.LibraryFilters
import app.atracker.data.applyLibraryFilters
import app.atracker.data.db.LibraryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class LibraryViewModel(app: ATrackerApp) : ViewModel() {
    private val db = app.database
    private val _filters = MutableStateFlow(LibraryFilters())
    val filters: StateFlow<LibraryFilters> = _filters

    fun update(block: (LibraryFilters) -> LibraryFilters) { _filters.update(block) }

    private val all = db.libraryDao().observeAll()

    private val extras = combine(db.mediaDao().observeAllGenres(), db.mediaDao().observeAllTitles()) { genres, titles ->
        val g = genres.filter { it.kind == "GENRE" }.groupBy({ it.mediaId }, { it.name })
        val t = titles.groupBy({ it.mediaId }, { it.title })
        (g.keys + t.keys).associateWith { id -> ItemExtras(g[id].orEmpty().toSet(), t[id].orEmpty()) }
    }

    val items: StateFlow<List<LibraryItem>> =
        combine(all, extras, _filters) { items, ex, f -> applyLibraryFilters(items, ex, f) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> = all.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Genres and years that exist in the user's own library, for the filter menus. */
    val allGenres: StateFlow<List<String>> = combine(all, extras) { items, ex ->
        items.flatMap { ex[it.media.id]?.genres.orEmpty() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allYears: StateFlow<List<Int>> = all.map { items ->
        items.mapNotNull { it.media.startYear }.distinct().sortedDescending()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
