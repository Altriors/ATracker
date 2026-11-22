package app.atracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.atracker.data.db.AppDatabase
import app.atracker.data.db.LibraryEntryEntity
import app.atracker.data.db.MediaEntity
import app.atracker.domain.FranchiseRelation
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.ProviderRelation
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
class FranchiseTest {
    private lateinit var db: AppDatabase
    private lateinit var media: MediaRepository
    private lateinit var library: LibraryRepository
    private lateinit var franchises: FranchiseRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        media = MediaRepository(db) { now++ }
        library = LibraryRepository(db) { now++ }
        franchises = FranchiseRepository(db) { now++ }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun title(id: String, type: MediaType, chapters: Int? = null, episodes: Int? = null, provider: String = "anilist") =
        media.saveMetadata(
            MediaMetadata(provider = provider, externalId = id, mediaType = type, title = "Lord of the Mysteries", totalChapters = chapters, totalEpisodes = episodes)
        )

    @Test
    fun adaptationsWithTheSameNameStaySeparateUntilLinkedByHand() = runBlocking {
        val novel = title("Q1", MediaType.WEB_NOVEL, provider = "wikidata")
        val manhua = title("2", MediaType.MANHUA, 65)
        val anime = title("3", MediaType.ANIME, episodes = 13)
        assertNull(db.franchiseDao().memberFor(novel))
        assertNull(db.franchiseDao().memberFor(manhua))
        assertNull(db.franchiseDao().memberFor(anime))
        assertEquals(0, db.franchiseDao().observeSummariesOnce().size)
    }

    @Test
    fun linkingGivesEachMemberItsOwnEntry() = runBlocking {
        val novel = title("Q1", MediaType.WEB_NOVEL, provider = "wikidata")
        val manhua = title("2", MediaType.MANHUA, 65)
        library.addToLibrary(novel); library.setProgress(novel, 500)
        library.addToLibrary(manhua); library.setProgress(manhua, 12)

        val (fid, result) = franchises.linkPair(novel, manhua, FranchiseRelation.ADAPTATION)
        assertEquals(LinkResult.LINKED, result)
        assertNotNull(fid)
        assertEquals(setOf(novel, manhua), db.franchiseDao().membersOf(fid!!).map { it.mediaId }.toSet())
        assertEquals(500, db.libraryDao().getEntry(novel)!!.progress)
        assertEquals(12, db.libraryDao().getEntry(manhua)!!.progress)
        // the web novel defaults to being the original source
        assertEquals(FranchiseRelation.SOURCE.name, db.franchiseDao().memberFor(novel)!!.relation)
        assertEquals(FranchiseRelation.ADAPTATION.name, db.franchiseDao().memberFor(manhua)!!.relation)
    }

    @Test
    fun aTitleBelongsToOnlyOneFranchiseAndIsNotSilentlyMoved() = runBlocking {
        val a = title("1", MediaType.MANGA); val b = title("2", MediaType.ANIME); val c = title("3", MediaType.MANGA); val d = title("4", MediaType.ANIME)
        val (f1, _) = franchises.linkPair(a, b, FranchiseRelation.ADAPTATION)
        val (f2, _) = franchises.linkPair(c, d, FranchiseRelation.ADAPTATION)

        assertEquals(LinkResult.IN_OTHER_FRANCHISE, franchises.link(f1!!, c, FranchiseRelation.SPIN_OFF))
        assertEquals(f2, db.franchiseDao().memberFor(c)!!.franchiseId)
        // two titles that are already in different franchises are not merged
        assertEquals(null to LinkResult.IN_OTHER_FRANCHISE, franchises.linkPair(b, d, FranchiseRelation.OTHER))
        // moving needs an explicit request
        assertEquals(LinkResult.MOVED, franchises.link(f1, c, FranchiseRelation.SPIN_OFF, allowMove = true))
        assertEquals(f1, db.franchiseDao().memberFor(c)!!.franchiseId)
    }

    @Test
    fun relinkingUpdatesTheRelationInsteadOfDuplicating() = runBlocking {
        val a = title("1", MediaType.MANGA); val b = title("2", MediaType.ANIME)
        val (f, _) = franchises.linkPair(a, b, FranchiseRelation.ADAPTATION)
        assertEquals(LinkResult.UPDATED, franchises.link(f!!, b, FranchiseRelation.SEQUEL))
        assertEquals(2, db.franchiseDao().memberCount(f))
        assertEquals(FranchiseRelation.SEQUEL.name, db.franchiseDao().memberFor(b)!!.relation)
    }

    @Test
    fun unlinkingKeepsAllPersonalDataAndDropsTinyFranchises() = runBlocking {
        val a = title("1", MediaType.MANGA); val b = title("2", MediaType.ANIME); val c = title("3", MediaType.LIGHT_NOVEL)
        library.addToLibrary(b); library.setProgress(b, 5); library.setRating(b, 80)
        val (f, _) = franchises.linkPair(a, b, FranchiseRelation.ADAPTATION)
        franchises.link(f!!, c, FranchiseRelation.SOURCE)

        franchises.unlink(b)
        assertNull(db.franchiseDao().memberFor(b))
        assertEquals(2, db.franchiseDao().memberCount(f))
        val entry = db.libraryDao().getEntry(b)!!
        assertEquals(5, entry.progress); assertEquals(80, entry.rating)

        franchises.unlink(c) // leaves a single member: the franchise is removed
        assertNull(db.franchiseDao().getFranchise(f))
        assertNull(db.franchiseDao().memberFor(a))
    }

    @Test
    fun linkingToYourselfIsRejected() = runBlocking {
        val a = title("1", MediaType.MANGA)
        val e = runCatching { franchises.linkPair(a, a, FranchiseRelation.OTHER) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test
    fun renameAndBlankNames() = runBlocking {
        val a = title("1", MediaType.MANGA); val b = title("2", MediaType.ANIME)
        val (f, _) = franchises.linkPair(a, b, FranchiseRelation.ADAPTATION)
        franchises.rename(f!!, "  Mysteries  ")
        assertEquals("Mysteries", db.franchiseDao().getFranchise(f)!!.name)
        franchises.rename(f, "   ")
        assertEquals("Mysteries", db.franchiseDao().getFranchise(f)!!.name)
    }

    @Test
    fun providerRelationsArePersistedAndOnlySuggested() = runBlocking {
        val anime = title("3", MediaType.ANIME)
        val manga = media.saveMetadata(
            MediaMetadata(
                provider = "anilist", externalId = "2", mediaType = MediaType.MANHUA, title = "Source",
                relations = listOf(
                    ProviderRelation("anilist", "3", MediaType.ANIME, "Anime", FranchiseRelation.ADAPTATION),
                    ProviderRelation("anilist", "999", MediaType.ANIME, "Not saved yet", FranchiseRelation.SEQUEL)
                )
            )
        )
        val s = franchises.suggestionsFor(manga)
        assertEquals(2, s.size)
        assertEquals(anime, s.first { it.relation.externalId == "3" }.localMediaId)
        assertNull(s.first { it.relation.externalId == "999" }.localMediaId)
        // suggestions never create links by themselves
        assertNull(db.franchiseDao().memberFor(manga))
        assertNull(db.franchiseDao().memberFor(anime))

        // a refresh replaces relations instead of piling them up
        media.saveMetadata(
            MediaMetadata(provider = "anilist", externalId = "2", mediaType = MediaType.MANHUA, title = "Source",
                relations = listOf(ProviderRelation("anilist", "3", MediaType.ANIME, "Anime", FranchiseRelation.ADAPTATION)))
        )
        assertEquals(1, franchises.suggestionsFor(manga).size)
    }

    @Test
    fun suggestionKnowsWhenAlreadyInTheSameFranchise() = runBlocking {
        val anime = title("3", MediaType.ANIME)
        val manga = media.saveMetadata(
            MediaMetadata(provider = "anilist", externalId = "2", mediaType = MediaType.MANHUA, title = "Source",
                relations = listOf(ProviderRelation("anilist", "3", MediaType.ANIME, "Anime", FranchiseRelation.ADAPTATION)))
        )
        assertTrue(!franchises.suggestionsFor(manga).single().alreadyInFranchise)
        franchises.linkPair(manga, anime, FranchiseRelation.ADAPTATION)
        assertTrue(franchises.suggestionsFor(manga).single().alreadyInFranchise)
    }

    // ---------------- statistics ----------------

    private fun media(id: Long, type: MediaType) =
        MediaEntity(id = id, mediaType = type.name, title = "T$id", createdAt = 0, metadataUpdatedAt = 0)

    private fun entry(id: Long, progress: Int, status: LibraryStatus = LibraryStatus.CURRENT, rating: Int? = null, volumes: Int = 0) =
        LibraryEntryEntity(mediaId = id, status = status.name, progress = progress, progressVolumes = volumes, rating = rating, addedAt = 0, updatedAt = 0)

    @Test
    fun statsKeepUnitsSeparateAndNeverMixEpisodesWithChapters() {
        val stats = computeFranchiseStats(
            listOf(
                media(1, MediaType.WEB_NOVEL) to entry(1, 500),
                media(2, MediaType.MANHUA) to entry(2, 65, LibraryStatus.COMPLETED, rating = 80),
                media(3, MediaType.ANIME) to entry(3, 13, LibraryStatus.COMPLETED, rating = 90),
                media(4, MediaType.ANIME) to entry(4, 6)
            )
        )
        val byType = stats.formats.associateBy { it.type }
        assertEquals(19, byType[MediaType.ANIME]!!.progress)            // episodes only
        assertEquals("episode", byType[MediaType.ANIME]!!.unit.singular)
        assertEquals(65, byType[MediaType.MANHUA]!!.progress)           // chapters only
        assertEquals(500, byType[MediaType.WEB_NOVEL]!!.progress)       // separate from manhua chapters
        assertEquals(4, stats.titles)
        assertEquals(2, stats.completed)
        assertEquals(85.0, stats.averageRating!!, 0.001)
    }

    @Test
    fun membersOutsideTheLibraryContributeNothingAndAreNotDoubleCounted() {
        val stats = computeFranchiseStats(
            listOf(media(1, MediaType.MANGA) to null, media(2, MediaType.MANGA) to entry(2, 30, volumes = 3))
        )
        val manga = stats.formats.single()
        assertEquals(2, manga.titles)
        assertEquals(1, manga.inLibrary)
        assertEquals(30, manga.progress)
        assertEquals(3, manga.volumes)
        assertNull(manga.averageRating)
    }
}
