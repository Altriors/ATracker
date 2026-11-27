package app.atracker

import android.app.Application
import app.atracker.data.CatalogueRepository
import app.atracker.data.FranchiseRepository
import app.atracker.data.LibraryRepository
import app.atracker.data.MediaRepository
import app.atracker.data.db.AppDatabase
import app.atracker.data.mal.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow

class ATrackerApp : Application() {
    val tokenStore by lazy { TokenStore(this) }
    /** Set by MainActivity when MyAnimeList redirects back to atracker://mal-auth. */
    val pendingMalRedirect = MutableStateFlow<android.net.Uri?>(null)
    val database by lazy { AppDatabase.get(this) }
    val mediaRepository by lazy { MediaRepository(database) }
    val libraryRepository by lazy { LibraryRepository(database) }
    val franchiseRepository by lazy { FranchiseRepository(database) }
    val catalogueRepository by lazy {
        CatalogueRepository(database, mediaRepository, CatalogueRepository.defaultProviders())
    }
}
