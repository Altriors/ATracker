package app.atracker

import android.app.Application
import app.atracker.data.LibraryRepository
import app.atracker.data.MediaRepository
import app.atracker.data.db.AppDatabase

class ATrackerApp : Application() {
    val database by lazy { AppDatabase.get(this) }
    val mediaRepository by lazy { MediaRepository(database) }
    val libraryRepository by lazy { LibraryRepository(database) }
}
