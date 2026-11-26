package app.atracker.data.mal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.atracker.data.MediaRepository
import app.atracker.data.db.AppDatabase
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.Providers
import app.atracker.domain.SourceRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MalImporterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val xml = """<?xml version="1.0" encoding="UTF-8" ?>
<myanimelist>
 <myinfo><user_export_type>1</user_export_type></myinfo>
 <anime><series_animedb_id>52991</series_animedb_id><series_title>Sousou no Frieren</series_title><series_episodes>28</series_episodes>
  <my_watched_episodes>19</my_watched_episodes><my_start_date>2023-10-01</my_start_date><my_finish_date>0000-00-00</my_finish_date>
  <my_score>9</my_score><my_status>Watching</my_status><my_comments>Great</my_comments><my_times_watched>1</my_times_watched><my_tags>fantasy, slow</my_tags></anime>
 <manga><manga_mangadb_id>2</manga_mangadb_id><manga_title>Berserk</manga_title><manga_volumes>41</manga_volumes><manga_chapters>0</manga_chapters>
  <my_read_volumes>36</my_read_volumes><my_read_chapters>350</my_read_chapters><my_score>0</my_score><my_status>Reading</my_status></manga>
</myanimelist>"""

    private fun frieren() = MediaMetadata(
        provider = Providers.ANILIST, externalId = "154587", mediaType = MediaType.ANIME, title = "Frieren", totalEpisodes = 28,
        crossRefs = listOf(SourceRef(Providers.MAL, "anime:52991"))
    )

    @Test fun xmlKeepsEveryFieldMalProvides() {
        val r = MalXml.parse(xml.byteInputStream())
        assertEquals(2, r.size)
        val a = r.first { it.kind == MalKind.ANIME }
        assertEquals(52991, a.malId); assertEquals(19, a.progress); assertEquals(9, a.score); assertEquals(1, a.repeatCount)
        assertEquals("2023-10-01", a.startDate); assertNull(a.finishDate)
        assertEquals(listOf("fantasy", "slow"), a.tags); assertEquals("Great", a.comments)
        val m = r.first { it.kind == MalKind.MANGA }
        assertNull(m.score); assertNull(m.total); assertEquals(36, m.volumes); assertEquals(LibraryStatus.CURRENT, m.status)
    }

    @Test fun rejectsNonMalFile() {
        try { MalXml.parse("<html/>".byteInputStream()); throw AssertionError("should fail") } catch (e: MalXml.ParseException) { assertTrue(true) }
    }

    @Test fun importIsIdempotentAndNeverOverwritesByDefault() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val importer = MalImporter(db, MediaRepository(db), null)
        val records = MalXml.parse(xml.byteInputStream())
        // Plan with a pre-matched AniList entry by injecting it as the matched meta.
        val plan = importer.plan(records).let { p ->
            p.copy(items = p.items.map { if (it.record.malId == 52991) it.copy(action = PlanAction.ADD, meta = frieren()) else it })
        }
        val first = importer.commit(plan, "MAL_XML", ConflictPolicy.KEEP_MINE)
        assertEquals(2, first.added); assertEquals(1, first.unmatched)
        val id = db.mediaDao().findIdBySource(Providers.MAL, "anime:52991")!!
        assertEquals(id, db.mediaDao().findIdBySource(Providers.ANILIST, "154587"))
        assertEquals(90, db.libraryDao().getEntry(id)!!.rating)

        // The user changes their own data, then re-imports: nothing is duplicated or replaced.
        db.libraryDao().upsert(db.libraryDao().getEntry(id)!!.copy(progress = 25, rating = 60))
        val plan2 = importer.plan(records)
        assertEquals(0, plan2.count(PlanAction.ADD)); assertEquals(2, plan2.items.count { it.existingMediaId != null })
        val second = importer.commit(plan2, "MAL_XML", ConflictPolicy.KEEP_MINE)
        assertEquals(0, second.added); assertEquals(0, second.updated)
        assertEquals(25, db.libraryDao().getEntry(id)!!.progress); assertEquals(60, db.libraryDao().getEntry(id)!!.rating)
        assertEquals(2, db.libraryDao().allItems().size)

        // Only an explicit choice replaces values, and the change is written to history.
        val third = importer.commit(plan2, "MAL_XML", ConflictPolicy.USE_MAL)
        assertEquals(1, third.updated)
        assertEquals(19, db.libraryDao().getEntry(id)!!.progress)
        assertNotNull(db.activityDao().allForMedia(id).firstOrNull { it.type == "CORRECTION" })
        assertEquals(3, db.importDao().observeRunsOnceForTest().size)
    }
}
