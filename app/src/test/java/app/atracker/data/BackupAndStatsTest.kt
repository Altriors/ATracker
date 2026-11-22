package app.atracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.atracker.data.backup.BackupManager
import app.atracker.data.backup.MalImport
import app.atracker.data.backup.RestoreResult
import app.atracker.data.backup.exportCsv
import app.atracker.data.backup.exportJson
import app.atracker.data.db.AppDatabase
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.Providers
import app.atracker.ui.stats.Period
import app.atracker.ui.stats.periodRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupAndStatsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var now = 10_000L

    @After
    fun tearDown() {
        AppDatabase.reset()
        context.deleteDatabase(AppDatabase.FILE_NAME)
    }

    private fun meta(title: String, type: MediaType, eps: Int? = null, ch: Int? = null, minutes: Int? = null) = MediaMetadata(
        provider = Providers.MANUAL, externalId = "", mediaType = type, title = title,
        totalEpisodes = eps, totalChapters = ch, episodeDurationMin = minutes, genres = listOf("Fantasy")
    )

    @Test
    fun statisticsKeepUnitsSeparateAndIgnoreVoidedEvents() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val media = MediaRepository(db) { now++ }
        val lib = LibraryRepository(db) { now++ }
        val anime = media.createManual(meta("Show", MediaType.ANIME, eps = 12, minutes = 24))
        val novel = media.createManual(meta("Novel", MediaType.WEB_NOVEL, ch = 500))
        lib.addToLibrary(anime, LibraryStatus.CURRENT); lib.addToLibrary(novel, LibraryStatus.CURRENT)
        lib.setProgress(anime, 5); lib.setProgress(novel, 100); lib.setRating(anime, 80)

        val stats = computeLibraryStats(
            db.libraryDao().allItems(),
            db.mediaDao().observeAllGenresOnce().groupBy({ it.mediaId }, { it.name }),
            emptyMap()
        )
        val a = stats.perType.first { it.type == MediaType.ANIME }
        val n = stats.perType.first { it.type == MediaType.WEB_NOVEL }
        assertEquals(5, a.progress); assertEquals(100, n.progress)
        assertEquals(120L, a.watchMinutes) // 5 episodes x 24 min
        assertNull(n.watchMinutes)
        assertEquals(80.0, a.averageRating!!, 0.001)
        assertEquals(2, stats.topGenres.first().count)

        val events = db.activityDao().allEvents()
        assertEquals(mapOf("EPISODE" to 5, "CHAPTER" to 100), activityTotals(events))
        val progressEvent = events.first { it.unit == "CHAPTER" && it.type == "PROGRESS" }
        lib.voidEvent(progressEvent.id)
        assertEquals(mapOf("EPISODE" to 5), activityTotals(db.activityDao().allEvents()))
        // Voiding history never changes current progress.
        assertEquals(100, db.libraryDao().getEntry(novel)!!.progress)
        db.close()
    }

    @Test
    fun periodsCoverTheRightRange() {
        val zone = ZoneId.of("UTC")
        val wed = LocalDate.of(2026, 11, 18)
        val (ws, we) = periodRange(Period.WEEK, wed, zone)
        assertEquals(LocalDate.of(2026, 11, 16).atStartOfDay(zone).toInstant().toEpochMilli(), ws)
        assertEquals(LocalDate.of(2026, 11, 23).atStartOfDay(zone).toInstant().toEpochMilli(), we)
        val (ms, me) = periodRange(Period.MONTH, wed, zone)
        assertEquals(LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli(), ms)
        assertEquals(LocalDate.of(2026, 12, 1).atStartOfDay(zone).toInstant().toEpochMilli(), me)
    }

    @Test
    fun backupAndRestoreRoundTrip() = runBlocking {
        var db = AppDatabase.get(context)
        var lib = LibraryRepository(db) { now++ }
        val media = MediaRepository(db) { now++ }
        val first = media.createManual(meta("First", MediaType.MANGA, ch = 10))
        lib.addToLibrary(first, LibraryStatus.CURRENT); lib.setProgress(first, 3)

        val out = ByteArrayOutputStream()
        BackupManager.backupTo(context, db, out)
        val bytes = out.toByteArray()
        assertTrue(bytes.size > 1000)

        // Change data after the backup, then restore.
        val second = media.createManual(meta("Second", MediaType.MANGA, ch = 10))
        lib.addToLibrary(second); lib.setProgress(first, 9)
        assertEquals(2, db.libraryDao().observeCount().first())

        val result = BackupManager.restoreFrom(context, ByteArrayInputStream(bytes), now)
        assertEquals(RestoreResult.Restored, result)

        db = AppDatabase.get(context)
        assertEquals(1, db.libraryDao().observeCount().first())
        assertEquals(3, db.libraryDao().getEntry(first)!!.progress)
        assertNotNull(File(context.filesDir, "pre-restore").listFiles()?.firstOrNull())
    }

    @Test
    fun restoreRejectsGarbageAndKeepsData() = runBlocking {
        val db = AppDatabase.get(context)
        val media = MediaRepository(db) { now++ }
        val id = media.createManual(meta("Keep", MediaType.MANGA))
        LibraryRepository(db) { now++ }.addToLibrary(id)

        val r = BackupManager.restoreFrom(context, ByteArrayInputStream("this is not a database".toByteArray()))
        assertTrue(r is RestoreResult.Rejected)
        assertEquals(1, AppDatabase.get(context).libraryDao().observeCount().first())
    }

    @Test
    fun exportsContainPersonalDataAndEscapeCsv() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val media = MediaRepository(db) { now++ }
        val lib = LibraryRepository(db) { now++ }
        val id = media.createManual(meta("Hello, \"World\"", MediaType.MANHWA, ch = 40))
        lib.addToLibrary(id, LibraryStatus.CURRENT); lib.setProgress(id, 7); lib.setRating(id, 85)
        val json = JSONObject(exportJson(db))
        val row = json.getJSONArray("library").getJSONObject(0)
        assertEquals(7, row.getInt("progress")); assertEquals(8.5, row.getDouble("ratingOutOf10"), 0.001)
        assertTrue(json.getJSONArray("history").length() >= 2)
        val csv = exportCsv(db).lines()
        assertTrue(csv[1].startsWith("\"Hello, \"\"World\"\"\",MANHWA,CURRENT,7"))
        db.close()
    }

    @Test
    fun malImportCreatesEntriesWithoutOverwritingExisting() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val media = MediaRepository(db) { now++ }
        val lib = LibraryRepository(db) { now++ }
        val existing = media.createManual(meta("Already Here", MediaType.ANIME, eps = 12))
        lib.addToLibrary(existing, LibraryStatus.CURRENT); lib.setProgress(existing, 4)

        val xml = """<?xml version="1.0" encoding="UTF-8" ?>
<myanimelist>
<myinfo><user_id>1</user_id></myinfo>
<anime><series_animedb_id>1</series_animedb_id><series_title><![CDATA[Cowboy Bebop]]></series_title><series_episodes>26</series_episodes>
<my_watched_episodes>26</my_watched_episodes><my_start_date>2020-01-02</my_start_date><my_finish_date>0000-00-00</my_finish_date><my_score>9</my_score><my_status>Completed</my_status></anime>
<anime><series_title>Already Here</series_title><series_episodes>12</series_episodes><my_watched_episodes>12</my_watched_episodes><my_score>10</my_score><my_status>Completed</my_status></anime>
<manga><manga_title><![CDATA[Berserk]]></manga_title><manga_chapters>0</manga_chapters><manga_volumes>41</manga_volumes><my_read_chapters>120</my_read_chapters><my_read_volumes>14</my_read_volumes><my_score>0</my_score><my_status>Reading</my_status></manga>
</myanimelist>"""
        val items = MalImport.parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals(3, items.size)
        val summary = MalImport.import(db, media, items, now)
        assertEquals(2, summary.imported); assertEquals(1, summary.skippedExisting)

        // Existing personal data untouched.
        val kept = db.libraryDao().getEntry(existing)!!
        assertEquals(4, kept.progress); assertNull(kept.rating)

        val bebop = db.mediaDao().findIdByTitleType("Cowboy Bebop", "ANIME")!!
        val e = db.libraryDao().getEntry(bebop)!!
        assertEquals(26, e.progress); assertEquals(90, e.rating); assertEquals("COMPLETED", e.status)
        val berserk = db.libraryDao().getEntry(db.mediaDao().findIdByTitleType("Berserk", "MANGA")!!)!!
        assertEquals(120, berserk.progress); assertEquals(14, berserk.progressVolumes); assertNull(berserk.rating)

        // Importing twice is harmless.
        val again = MalImport.import(db, media, items, now)
        assertEquals(0, again.imported); assertEquals(3, again.skippedExisting)
        // No progress events were created, so activity totals are not polluted by the import.
        assertTrue(activityTotals(db.activityDao().allEvents()).let { it["EPISODE"] == 4 && it["CHAPTER"] == null })
        db.close()
    }
}
