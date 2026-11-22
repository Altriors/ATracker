package app.atracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.atracker.data.db.AppDatabase
import app.atracker.data.provider.MetadataProvider
import app.atracker.data.provider.ProviderException
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.Providers
import app.atracker.domain.SourceRef
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

private class FakeProvider(
    override val id: String,
    private val types: Set<MediaType>,
    var results: List<MediaMetadata> = emptyList(),
    var failure: Exception? = null
) : MetadataProvider {
    override val displayName = id
    var searchCalls = 0
    override fun supports(type: MediaType) = type in types
    override suspend fun search(query: String, type: MediaType?, page: Int): List<MediaMetadata> {
        searchCalls++
        failure?.let { throw it }
        return results
    }
    override suspend fun detail(externalId: String) = results.firstOrNull { it.externalId == externalId }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogueRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var media: MediaRepository
    private lateinit var library: LibraryRepository
    private var now = 10_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        media = MediaRepository(db) { now++ }
        library = LibraryRepository(db) { now++ }
    }

    @After
    fun tearDown() = db.close()

    private fun meta(provider: String, id: String, title: String, type: MediaType, chapters: Int? = null) =
        MediaMetadata(provider = provider, externalId = id, mediaType = type, title = title, totalChapters = chapters)

    private fun repo(vararg p: MetadataProvider, ttl: Long = 1_000_000) =
        CatalogueRepository(db, media, p.toList(), clock = { now }, searchTtlMs = ttl)

    @Test
    fun oneFailingProviderDoesNotBreakTheOthers() = runBlocking {
        val good = FakeProvider("anilist", setOf(MediaType.MANHWA), listOf(meta("anilist", "1", "Eleceed", MediaType.MANHWA)))
        val bad = FakeProvider("mangadex", setOf(MediaType.MANHWA), failure = ProviderException("Network error: timeout"))
        val out = repo(good, bad).search("eleceed", MediaType.MANHWA)
        assertEquals(1, out.remote.size)
        assertEquals("Network error: timeout", out.errors["mangadex"])
    }

    @Test
    fun providersThatDoNotSupportTheTypeAreNotQueried() = runBlocking {
        val anilist = FakeProvider("anilist", setOf(MediaType.MANHWA))
        val wikidata = FakeProvider("wikidata", setOf(MediaType.WEB_NOVEL), listOf(meta("wikidata", "Q1", "Reverend Insanity", MediaType.WEB_NOVEL)))
        val out = repo(anilist, wikidata).search("reverend insanity", MediaType.WEB_NOVEL)
        assertEquals(0, anilist.searchCalls)
        assertEquals("Q1", out.remote.single().externalId)
    }

    @Test
    fun searchingDoesNotAddAnythingToLibraryOrCatalogue() = runBlocking {
        val p = FakeProvider("anilist", setOf(MediaType.MANHWA), listOf(meta("anilist", "1", "Eleceed", MediaType.MANHWA)))
        repo(p).search("eleceed", null)
        assertEquals(0, db.mediaDao().count())
        assertEquals(0, db.libraryDao().getEntry(1)?.let { 1 } ?: 0)
    }

    @Test
    fun offlineSearchFallsBackToStaleCache() = runBlocking {
        val p = FakeProvider("anilist", setOf(MediaType.MANHWA), listOf(meta("anilist", "1", "Eleceed", MediaType.MANHWA)))
        val r = repo(p, ttl = 100)
        assertEquals(1, r.search("eleceed", MediaType.MANHWA).remote.size)

        now += 10_000 // cache expired
        p.failure = ProviderException("Network error: offline")
        val offline = r.search("eleceed", MediaType.MANHWA)
        assertEquals(1, offline.remote.size)
        assertTrue(offline.errors.isEmpty())
        assertEquals(setOf("anilist"), offline.staleProviders)
    }

    @Test
    fun freshCacheAvoidsASecondNetworkCall() = runBlocking {
        val p = FakeProvider("anilist", setOf(MediaType.MANHWA), listOf(meta("anilist", "1", "Eleceed", MediaType.MANHWA)))
        val r = repo(p)
        r.search("eleceed", MediaType.MANHWA)
        r.search("Eleceed", MediaType.MANHWA)
        assertEquals(1, p.searchCalls)
    }

    @Test
    fun localCatalogueIsSearchedFirstAndWorksOffline() = runBlocking {
        media.saveMetadata(meta("anilist", "1", "Eleceed", MediaType.MANHWA).copy(alternativeTitles = listOf("Ilrisid")))
        val offline = FakeProvider("anilist", setOf(MediaType.MANHWA), failure = ProviderException("offline"))
        val out = repo(offline).search("ilrisid", MediaType.MANHWA)
        assertEquals("Eleceed", out.local.single().title)
        assertTrue(out.errors.containsKey("anilist"))
    }

    @Test
    fun differentAdaptationsWithTheSameNameStaySeparate() = runBlocking {
        val manga = media.saveMetadata(meta("anilist", "125291", "Lord of the Mysteries", MediaType.MANHUA))
        val anime = media.saveMetadata(meta("anilist", "137667", "Lord of the Mysteries", MediaType.ANIME))
        val novel = media.saveMetadata(meta("wikidata", "Q109342457", "Lord of the Mysteries", MediaType.WEB_NOVEL))
        assertEquals(3, setOf(manga, anime, novel).size)
    }

    @Test
    fun crossReferenceAttachesSecondProviderToTheSameEntry() = runBlocking {
        val aniId = media.saveMetadata(meta("anilist", "108050", "Gu Zhenren", MediaType.MANHUA, chapters = 96))
        library.addToLibrary(aniId)
        library.setProgress(aniId, 40)

        val dexId = media.saveMetadata(
            meta("mangadex", "bb71e1b9", "Reverend Insanity", MediaType.MANHUA, chapters = 99)
                .copy(crossRefs = listOf(SourceRef(Providers.ANILIST, "108050")))
        )
        assertEquals("hard id match must reuse the AniList entry", aniId, dexId)
        assertEquals(setOf("anilist", "mangadex"), db.mediaDao().sourcesFor(aniId).map { it.provider }.toSet())
        val row = db.mediaDao().getById(aniId)!!
        assertEquals("primary provider's value is not overwritten by the secondary", 96, row.totalChapters)
        assertEquals("Gu Zhenren", row.title)
        assertEquals(40, db.libraryDao().getEntry(aniId)!!.progress)
    }

    @Test
    fun secondaryProviderFillsMissingValues() = runBlocking {
        val id = media.saveMetadata(meta("anilist", "1", "A", MediaType.MANHWA, chapters = null))
        media.saveMetadata(
            meta("mangadex", "m1", "A", MediaType.MANHWA, chapters = 70).copy(crossRefs = listOf(SourceRef("anilist", "1")))
        )
        assertEquals(70, db.mediaDao().getById(id)!!.totalChapters)
    }

    @Test
    fun refreshKeepsProgressAndSurvivesProviderFailure() = runBlocking {
        val p = FakeProvider("anilist", setOf(MediaType.WEB_NOVEL), listOf(meta("anilist", "9", "Novel", MediaType.WEB_NOVEL, chapters = 500)))
        val r = repo(p)
        val id = r.open(p.results.first())
        library.addToLibrary(id)
        library.setProgress(id, 321)

        p.results = listOf(meta("anilist", "9", "Novel", MediaType.WEB_NOVEL, chapters = 700))
        assertEquals(1, r.refresh(id).updated)
        assertEquals(700, db.mediaDao().getById(id)!!.totalChapters)
        assertEquals(321, db.libraryDao().getEntry(id)!!.progress)

        p.results = emptyList() // title vanished from the provider
        assertEquals(0, r.refresh(id).updated)
        assertNotNull(db.mediaDao().getById(id))
        assertEquals(321, db.libraryDao().getEntry(id)!!.progress)
    }

    @Test
    fun manualEntryWorksAndIsNotOverwrittenByRefresh() = runBlocking {
        val id = media.createManual(
            meta("manual", "ignored", "My Obscure Web Novel", MediaType.WEB_NOVEL).copy(synopsis = "typed by hand")
        )
        library.addToLibrary(id)
        library.setProgress(id, 12)
        val row = db.mediaDao().getById(id)!!
        assertTrue(row.isManual)
        assertEquals(12, db.libraryDao().getEntry(id)!!.progress)

        assertTrue(media.updateManual(id, meta("manual", "x", "My Obscure Web Novel", MediaType.WEB_NOVEL, chapters = 300)))
        assertEquals(300, db.mediaDao().getById(id)!!.totalChapters)
        assertEquals(12, db.libraryDao().getEntry(id)!!.progress)
        assertEquals("manual", db.mediaDao().sourcesFor(id).single().provider)
    }

    @Test
    fun providerBackedEntriesCannotBeEditedByHand() = runBlocking {
        val id = media.saveMetadata(meta("anilist", "1", "A", MediaType.MANGA))
        assertTrue(!media.updateManual(id, meta("manual", "x", "Changed", MediaType.MANGA)))
        assertEquals("A", db.mediaDao().getById(id)!!.title)
    }

    @Test
    fun nextAiringReleasesAreReplacedNotAccumulated() = runBlocking {
        fun withRelease(ep: Int, at: Long) = meta("anilist", "5", "Show", MediaType.ANIME)
            .copy(releases = listOf(app.atracker.domain.ReleaseEvent("EPISODE", ep, at)))
        val id = media.saveMetadata(withRelease(8, 1000))
        media.saveMetadata(withRelease(9, 2000))
        val rel = db.mediaDao().releasesFor(id)
        assertEquals(1, rel.size)
        assertEquals(9, rel.single().number)
        assertNull(rel.firstOrNull { it.number == 8 })
    }
}
