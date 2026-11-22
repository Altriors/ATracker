package app.atracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atracker.ATrackerApp
import app.atracker.data.LinkResult
import app.atracker.data.SearchOutcome
import app.atracker.data.Suggestion
import app.atracker.data.db.FranchiseEntity
import app.atracker.data.db.FranchiseMemberRow
import app.atracker.data.db.LibraryEntryEntity
import app.atracker.data.db.MediaCreditEntity
import app.atracker.data.db.MediaEntity
import app.atracker.data.db.MediaGenreEntity
import app.atracker.data.db.MediaSourceEntity
import app.atracker.data.db.MediaTitleEntity
import app.atracker.domain.FranchiseRelation
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(private val app: ATrackerApp, val mediaId: Long) : ViewModel() {
    private val db = app.database
    private val library = app.libraryRepository

    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val media: StateFlow<MediaEntity?> = db.mediaDao().observeById(mediaId).state(null)
    val entry: StateFlow<LibraryEntryEntity?> = db.libraryDao().observeEntry(mediaId).state(null)
    val genres: StateFlow<List<MediaGenreEntity>> = db.mediaDao().observeGenres(mediaId).state(emptyList())
    val credits: StateFlow<List<MediaCreditEntity>> = db.mediaDao().observeCredits(mediaId).state(emptyList())
    val titles: StateFlow<List<MediaTitleEntity>> = db.mediaDao().observeTitles(mediaId).state(emptyList())
    val sources: StateFlow<List<MediaSourceEntity>> = db.mediaDao().observeSources(mediaId).state(emptyList())

    private val franchiseRepo = app.franchiseRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    val franchise: StateFlow<FranchiseEntity?> = db.franchiseDao().observeFranchiseIdOf(mediaId)
        .flatMapLatest { id -> if (id == null) flowOf(null) else db.franchiseDao().observeFranchise(id) }
        .state(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val members: StateFlow<List<FranchiseMemberRow>> = db.franchiseDao().observeFranchiseIdOf(mediaId)
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else db.franchiseDao().observeMembers(id) }
        .state(emptyList())

    /** Provider-reported relations (suggestions only). Recomputed when relations or membership change. */
    val suggestions: StateFlow<List<Suggestion>> =
        combine(db.mediaDao().observeRelations(mediaId), members) { _, _ -> franchiseRepo.suggestionsFor(mediaId) }
            .state(emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun dismissMessage() { _message.value = null }

    fun add(status: LibraryStatus) = launch { library.addToLibrary(mediaId, status) }
    fun remove() = launch { library.removeFromLibrary(mediaId) }
    fun setProgress(value: Int) = launch { library.setProgress(mediaId, value) }
    fun increment(by: Int = 1) = launch { library.incrementProgress(mediaId, by) }
    fun setVolumes(value: Int) = launch { library.setVolumeProgress(mediaId, value) }
    fun setStatus(s: LibraryStatus) = launch { library.setStatus(mediaId, s) }
    fun setRating(r: Int?) = launch { library.setRating(mediaId, r) }
    fun setFavorite(f: Boolean) = launch { library.setFavorite(mediaId, f) }
    fun saveNotes(notes: String, review: String) = launch { library.setNotes(mediaId, notes, review) }
    fun startNewRun() = launch { library.startNewRun(mediaId) }

    /** Re-fetches metadata from the providers. Personal data is never touched by a refresh. */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val r = app.catalogueRepository.refresh(mediaId)
                _message.value = when {
                    r.failed.isNotEmpty() -> "Refresh problem: ${r.failed.first()}"
                    r.updated == 0 -> "Nothing to refresh for this title."
                    else -> "Metadata updated. Your progress was not changed."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Refresh failed: ${e.message}"
            } finally {
                _refreshing.value = false
            }
        }
    }

    // ---- franchise ----

    suspend fun searchLocal(q: String): List<MediaEntity> =
        if (q.isBlank()) emptyList() else db.mediaDao().searchLocal(q.trim(), null, 20).filter { it.id != mediaId }

    suspend fun searchOnline(q: String): SearchOutcome = app.catalogueRepository.search(q.trim(), null)

    /** Links this title with [otherId] (a title already in the local catalogue). */
    fun linkWith(otherId: Long, relation: FranchiseRelation) = launch { report(franchiseRepo.linkPair(mediaId, otherId, relation).second) }

    /** Saves an online result to the local catalogue (not the library), then links it. */
    fun linkWithOnline(meta: MediaMetadata, relation: FranchiseRelation) = launch {
        val id = app.catalogueRepository.open(meta)
        if (id == mediaId) { _message.value = "That is this same title."; return@launch }
        report(franchiseRepo.linkPair(mediaId, id, relation).second)
    }

    fun linkSuggestion(s: Suggestion) = launch {
        val id = s.localMediaId ?: app.catalogueRepository.openRelated(s.relation.provider, s.relation.externalId)
        if (id == null) { _message.value = "That title is not available from the provider right now."; return@launch }
        report(franchiseRepo.linkPair(mediaId, id, runCatching { FranchiseRelation.valueOf(s.relation.relation) }.getOrDefault(FranchiseRelation.OTHER)).second)
    }

    /** Opens a related title's page, fetching it from the provider first if needed. */
    fun openSuggestion(s: Suggestion, onOpen: (Long) -> Unit) = launch {
        val id = s.localMediaId ?: app.catalogueRepository.openRelated(s.relation.provider, s.relation.externalId)
        if (id == null) {
            _message.value = "That title is not available from the provider right now."
        } else {
            withContext(Dispatchers.Main.immediate) { onOpen(id) }
        }
    }

    fun unlinkFromFranchise() = launch { franchiseRepo.unlink(mediaId) }

    private fun report(r: LinkResult) {
        _message.value = when (r) {
            LinkResult.LINKED, LinkResult.UPDATED, LinkResult.MOVED -> "Linked."
            LinkResult.IN_OTHER_FRANCHISE -> "One of these titles is already in a different franchise. Unlink it there first."
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { _message.value = "Could not save: ${e.message}" }
        }
    }
}
