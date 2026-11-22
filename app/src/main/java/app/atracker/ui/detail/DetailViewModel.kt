package app.atracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atracker.ATrackerApp
import app.atracker.data.db.LibraryEntryEntity
import app.atracker.data.db.MediaCreditEntity
import app.atracker.data.db.MediaEntity
import app.atracker.data.db.MediaGenreEntity
import app.atracker.data.db.MediaSourceEntity
import app.atracker.data.db.MediaTitleEntity
import app.atracker.domain.LibraryStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
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

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { _message.value = "Could not save: ${e.message}" }
        }
    }
}
