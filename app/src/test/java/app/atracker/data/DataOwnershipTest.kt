package app.atracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.atracker.data.db.AppDatabase
import app.atracker.domain.ActivityType
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.Providers
import app.atracker.domain.PublicationStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataOwnershipTest {
    private lateinit var db: AppDatabase
    private lateinit var media: MediaRepository
    private lateinit var library: LibraryRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        media = MediaRepository(db) { now++ }
        library = LibraryRepository(db) { now++ }
    }

    @After
    fun tearDown() = db.close()

    private fun webNovel(chapters: Int?) = MediaMetadata(
        provider = Providers.ANILIST, externalId = "42", mediaType = MediaType.WEB_NOVEL,
        title = "Test Novel", totalChapters = chapters, pubStatus = PublicationStatus.ONGOING,
        genres = listOf("Fantasy")
    )

    @Test
    fun metadataRefreshNeverOverwritesProgress() = runBlocking {
        val id = media.saveMetadata(webNovel(chapters = 1000))
        library.addToLibrary(id)
        library.setProgress(id, 500)
        library.setRating(id, 85)
        library.setNotes(id, "great arc", null)

        // Catalogue refresh: the provider now reports a different total.
        val sameId = media.saveMetadata(webNovel(chapters = 1334))

        assertEquals("refresh must reuse the same internal id", id, sameId)
        val entry = db.libraryDao().getEntry(id)!!
        assertEquals(500, entry.progress)
        assertEquals(85, entry.rating)
        assertEquals("great arc", entry.notes)
        assertEquals(1334, db.mediaDao().getById(id)!!.totalChapters)
    }

    @Test
    fun refreshWithMissingValueKeepsKnownValue() = runBlocking {
        val id = media.saveMetadata(webNovel(chapters = 1000))
        media.saveMetadata(webNovel(chapters = null))
        assertEquals(1000, db.mediaDao().getById(id)!!.totalChapters)
    }

    @Test
    fun unknownTotalDoesNotAutoComplete() = runBlocking {
        val id = media.saveMetadata(webNovel(chapters = null))
        library.addToLibrary(id)
        library.setProgress(id, 2000)
        val entry = db.libraryDao().getEntry(id)!!
        assertEquals(2000, entry.progress)
        assertEquals(LibraryStatus.CURRENT.name, entry.status)
        assertNull(entry.completedAt)
    }

    @Test
    fun reachingKnownTotalCompletesAndLogsOnce() = runBlocking {
        val id = media.saveMetadata(webNovel(chapters = 10))
        library.addToLibrary(id)
        library.setProgress(id, 10)
        library.setProgress(id, 10) // repeated identical update must not duplicate events

        val entry = db.libraryDao().getEntry(id)!!
        assertEquals(LibraryStatus.COMPLETED.name, entry.status)
        assertNotNull(entry.completedAt)
        val events = db.activityDao().allForMedia(id)
        assertEquals(1, events.count { it.type == ActivityType.COMPLETED.name })
        assertEquals(1, events.count { it.type == ActivityType.PROGRESS.name })
    }

    @Test
    fun progressIsCappedAtKnownTotal() = runBlocking {
        val id = media.saveMetadata(webNovel(chapters = 10))
        library.addToLibrary(id)
        library.setProgress(id, 99)
        assertEquals(10, db.libraryDao().getEntry(id)!!.progress)
    }

    @Test
    fun voidingAnEventDoesNotChangeCurrentProgress() = runBlocking {
        val id = media.saveMetadata(webNovel(chapters = 100))
        library.addToLibrary(id)
        library.setProgress(id, 30)
        val progressEvent = db.activityDao().allForMedia(id).first { it.type == ActivityType.PROGRESS.name }
        library.voidEvent(progressEvent.id)

        assertEquals(30, db.libraryDao().getEntry(id)!!.progress)
        assertTrue(db.activityDao().allForMedia(id).first { it.id == progressEvent.id }.voidedAt != null)
    }

    @Test
    fun rewatchStartsFreshRunAndKeepsHistory() = runBlocking {
        val id = media.saveMetadata(webNovel(chapters = 5))
        library.addToLibrary(id)
        library.setProgress(id, 5)
        library.startNewRun(id)

        val entry = db.libraryDao().getEntry(id)!!
        assertEquals(0, entry.progress)
        assertEquals(1, entry.repeatCount)
        assertEquals(LibraryStatus.CURRENT.name, entry.status)
        assertTrue(db.activityDao().allForMedia(id).any { it.type == ActivityType.COMPLETED.name })
    }

    @Test
    fun cacheCleanupNeverDeletesLibraryTitles() = runBlocking {
        val kept = media.saveMetadata(webNovel(chapters = 10))
        library.addToLibrary(kept)
        val other = media.saveMetadata(webNovel(chapters = 10).copy(externalId = "43", title = "Other"))

        db.mediaDao().deleteUnusedCache(olderThan = Long.MAX_VALUE)

        assertNotNull(db.mediaDao().getById(kept))
        assertNull(db.mediaDao().getById(other))
    }
}
