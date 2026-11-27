package app.atracker.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atracker.ATrackerApp
import app.atracker.data.DiscoverFilters
import app.atracker.data.SearchOutcome
import app.atracker.data.db.AppSettingEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import app.atracker.data.provider.BrowseKind
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverState(
    val query: String = "",
    val type: MediaType? = MediaType.ANIME,
    val kind: BrowseKind = BrowseKind.POPULAR,
    val loading: Boolean = false,
    val outcome: SearchOutcome? = null,
    val filters: DiscoverFilters = DiscoverFilters()
)

class DiscoverViewModel(private val app: ATrackerApp) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = _state.asStateFlow()
    private var job: Job? = null

    /** Most recent searches first, newest-first, at most [MAX_HISTORY]. Stored in the database so backups keep them. */
    val history: StateFlow<List<String>> = app.database.settingDao().observe(KEY_HISTORY)
        .map { raw -> raw?.split('\n')?.filter { it.isNotBlank() }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun remember(q: String) {
        val clean = q.trim()
        if (clean.length < 2) return
        viewModelScope.launch {
            val dao = app.database.settingDao()
            val old = dao.get(KEY_HISTORY)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
            val next = (listOf(clean) + old.filter { !it.equals(clean, ignoreCase = true) }).take(MAX_HISTORY)
            dao.put(AppSettingEntity(KEY_HISTORY, next.joinToString("\n")))
        }
    }

    fun clearHistory() { viewModelScope.launch { app.database.settingDao().put(AppSettingEntity(KEY_HISTORY, "")) } }
    fun removeFromHistory(q: String) { viewModelScope.launch {
        val dao = app.database.settingDao()
        dao.put(AppSettingEntity(KEY_HISTORY, dao.get(KEY_HISTORY).orEmpty().split('\n').filter { it != q }.joinToString("\n")))
    } }
    fun useHistory(q: String) { _state.update { it.copy(query = q) }; submit() }

    /** Adds a discovered title straight to the library. Returns false when it was already there. */
    suspend fun addToLibrary(meta: MediaMetadata): Boolean {
        val id = open(meta)
        return app.libraryRepository.addToLibrary(id)
    }

    companion object { const val KEY_HISTORY = "search_history"; const val MAX_HISTORY = 15 }

    init { reload() }

    fun setType(t: MediaType?) { _state.update { it.copy(type = t, outcome = null) }; reload() }
    fun setKind(k: BrowseKind) { _state.update { it.copy(kind = k, outcome = null) }; reload() }
    private var debounce: Job? = null

    /** Searches automatically shortly after typing stops (the keyboard's search key still works too). */
    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        debounce?.cancel()
        debounce = viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            reload()
        }
    }
    fun submit() { debounce?.cancel(); reload() }
    fun setFilters(f: DiscoverFilters) { _state.update { it.copy(filters = f) } }

    private fun reload() {
        job?.cancel()
        job = viewModelScope.launch {
            val s = _state.value
            val repo = app.catalogueRepository
            if (s.query.isBlank() && (s.type == null || s.type == MediaType.WEB_NOVEL)) {
                _state.update { it.copy(loading = false, outcome = null) }
                return@launch
            }
            _state.update { it.copy(loading = true) }
            try {
                val out = if (s.query.isBlank()) repo.browse(s.kind, s.type!!) else repo.search(s.query, s.type)
                _state.update { it.copy(loading = false, outcome = out) }
                if (s.query.isNotBlank() && out.remote.isNotEmpty()) remember(s.query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, outcome = SearchOutcome(emptyList(), emptyList(), mapOf("search" to (e.message ?: "failed")), emptySet())) }
            }
        }
    }

    /** Stores the title in the local catalogue (not the library) so its page can open and work offline. */
    suspend fun open(meta: MediaMetadata): Long = app.catalogueRepository.open(meta)
}
