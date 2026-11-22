package app.atracker.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atracker.ATrackerApp
import app.atracker.data.SearchOutcome
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
    val outcome: SearchOutcome? = null
)

class DiscoverViewModel(private val app: ATrackerApp) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = _state.asStateFlow()
    private var job: Job? = null

    init { reload() }

    fun setType(t: MediaType?) { _state.update { it.copy(type = t, outcome = null) }; reload() }
    fun setKind(k: BrowseKind) { _state.update { it.copy(kind = k, outcome = null) }; reload() }
    fun setQuery(q: String) { _state.update { it.copy(query = q) } }
    fun submit() = reload()

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
