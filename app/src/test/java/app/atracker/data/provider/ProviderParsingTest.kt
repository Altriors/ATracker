package app.atracker.data.provider

import app.atracker.domain.CreditRole
import app.atracker.domain.MediaType
import app.atracker.domain.Providers
import app.atracker.domain.PublicationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderParsingTest {

    // ---------------- AniList ----------------

    @Test
    fun anilistChineseMangaBecomesManhuaWithUnitsAndCredits() = runBlocking {
        val p = AniListProvider(Fixtures.http { 200 to Fixtures.ANILIST_LOTM_MANGA })
        val m = p.search("Lord of the Mysteries", MediaType.MANHUA).single()
        assertEquals(MediaType.MANHUA, m.mediaType)
        assertEquals("Lord of the Mysteries", m.title)
        assertEquals("诡秘之主", m.titleNative)
        assertEquals(65, m.totalChapters)
        assertNull("AniList gave no volume count, so none may be invented", m.totalVolumes)
        assertEquals(PublicationStatus.COMPLETED, m.pubStatus)
        assertEquals("2020-01-21", m.startDate)
        assertEquals("2020", m.endDate) // partial date stays partial
        assertEquals("c-xl.png", m.coverUrl!!.substringAfterLast('/'))
        assertFalse("HTML must be stripped", m.synopsis!!.contains("<br"))
        assertTrue(m.credits.contains(app.atracker.domain.Credit(CreditRole.AUTHOR, "Ai Qianshui de Wuzei")))
        assertTrue(m.credits.contains(app.atracker.domain.Credit(CreditRole.ARTIST, "V")))
        assertEquals("137667", m.relations.single().externalId)
        assertEquals(MediaType.ANIME, m.relations.single().mediaType)
    }

    @Test
    fun anilistAnimeKeepsEpisodesStudioAndNextAiring() = runBlocking {
        val p = AniListProvider(Fixtures.http { 200 to Fixtures.ANILIST_LOTM_ANIME })
        val m = p.detail("137667")!!
        assertEquals(MediaType.ANIME, m.mediaType)
        assertEquals(13, m.totalEpisodes)
        assertEquals(35, m.episodeDurationMin)
        assertNull(m.totalChapters)
        assertTrue(m.credits.any { it.role == CreditRole.STUDIO && it.name == "B.CMAY PICTURES" })
        assertFalse("director is not an author", m.credits.any { it.name == "Ke Xiong" })
        val next = m.releases.single()
        assertEquals("EPISODE", next.kind)
        assertEquals(9, next.number)
        assertEquals(1795788000L * 1000, next.atMillis)
        assertFalse("trailing zero-width characters must be removed", m.titleNative!!.endsWith("​"))
    }

    @Test
    fun anilistOngoingManhwaKeepsUnknownTotalsAndIgnoresTranslators() = runBlocking {
        val p = AniListProvider(Fixtures.http { 200 to Fixtures.ANILIST_MANHWA_UNKNOWN_TOTAL })
        val m = p.detail("119257")!!
        assertEquals(MediaType.MANHWA, m.mediaType)
        assertNull(m.totalChapters)
        assertEquals(PublicationStatus.ONGOING, m.pubStatus)
        assertNull(m.synopsis)
        assertEquals(setOf("UMI", "Singsyong"), m.credits.filter { it.role == CreditRole.AUTHOR }.map { it.name }.toSet())
        assertEquals(listOf("Sleepy-C"), m.credits.filter { it.role == CreditRole.ARTIST }.map { it.name })
    }

    @Test
    fun anilistTypeMapping() {
        assertEquals(MediaType.LIGHT_NOVEL, AniListProvider.mapType("MANGA", "NOVEL", "JP"))
        assertEquals(MediaType.WEB_NOVEL, AniListProvider.mapType("MANGA", "NOVEL", "KR"))
        assertEquals(MediaType.MANHWA, AniListProvider.mapType("MANGA", "MANGA", "KR"))
        assertEquals(MediaType.MANHUA, AniListProvider.mapType("MANGA", "MANGA", "CN"))
        assertEquals(MediaType.MANGA, AniListProvider.mapType("MANGA", "ONE_SHOT", "JP"))
        assertEquals(MediaType.ANIME, AniListProvider.mapType("ANIME", "MOVIE", "JP"))
    }

    @Test(expected = ProviderException::class)
    fun anilistApiErrorSurfacesAsProviderException(): Unit = runBlocking {
        AniListProvider(Fixtures.http { 200 to Fixtures.ANILIST_ERROR }).search("x", null)
        Unit
    }

    @Test
    fun anilistDoesNotCoverWebNovels() = runBlocking {
        var called = false
        val p = AniListProvider(Fixtures.http { called = true; 200 to "{}" })
        assertTrue(p.search("Reverend Insanity", MediaType.WEB_NOVEL).isEmpty())
        assertFalse("no request may be made for an unsupported type", called)
    }

    // ---------------- MangaDex ----------------

    @Test
    fun mangadexTrustsLastChapterOnlyForFinishedSeries() = runBlocking {
        val p = MangaDexProvider(Fixtures.http { 200 to Fixtures.MANGADEX_REVEREND })
        val list = p.search("Reverend Insanity", MediaType.MANHUA)
        val remake = list.first { it.title == "Reverend Insanity Remake" }
        val original = list.first { it.title == "Reverend Insanity" }

        assertEquals(PublicationStatus.ONGOING, remake.pubStatus)
        assertNull(remake.totalChapters)
        assertEquals(PublicationStatus.CANCELLED, original.pubStatus)
        assertEquals(96, original.totalChapters)
        assertEquals(MediaType.MANHUA, original.mediaType)
        assertEquals("蛊真人", original.titleNative)
    }

    @Test
    fun mangadexCrossReferencesAnilistAndBuildsCoverUrl() = runBlocking {
        val p = MangaDexProvider(Fixtures.http { 200 to Fixtures.MANGADEX_REVEREND })
        val list = p.search("Reverend Insanity", null)
        val original = list.first { it.title == "Reverend Insanity" }
        assertEquals(Providers.ANILIST, original.crossRefs.single().provider)
        assertEquals("108050", original.crossRefs.single().externalId)
        val remake = list.first { it.title.endsWith("Remake") }
        assertEquals("https://uploads.mangadex.org/covers/e1809b04-f7dd-4327-9669-ca83884a1403/a635e356.png.512.jpg", remake.coverUrl)
        assertEquals(listOf("Action"), remake.genres)
        assertEquals(listOf("Reincarnation"), remake.tags)
        assertTrue(remake.credits.any { it.role == CreditRole.ARTIST && it.name == "LenHC" })
    }

    @Test
    fun mangadexSkipsAnimeAndNovelRequests() = runBlocking {
        val p = MangaDexProvider(Fixtures.http { error("must not be called") })
        assertTrue(p.search("x", MediaType.ANIME).isEmpty())
        assertTrue(p.search("x", MediaType.LIGHT_NOVEL).isEmpty())
    }

    // ---------------- Wikidata ----------------

    private fun wikidata(): WikidataProvider = WikidataProvider(Fixtures.http { req ->
        val url = req.url.toString()
        when {
            "wbsearchentities" in url -> 200 to Fixtures.WIKIDATA_SEARCH_LOTM
            "Q110107880" in url && "wbgetentities" in url && "claims" !in url -> 200 to Fixtures.WIKIDATA_AUTHOR
            "Q109342457" in url -> 200 to Fixtures.WIKIDATA_ENTITY_LOTM
            "Q137441819" in url -> 200 to Fixtures.WIKIDATA_ENTITY_BARE
            else -> 404 to "{}"
        }
    })

    @Test
    fun wikidataFindsWebNovelAndRejectsItsAnimeAdaptations() = runBlocking {
        val list = wikidata().search("Lord of the Mysteries", MediaType.WEB_NOVEL)
        val m = list.single()
        assertEquals("Q109342457", m.externalId)
        assertEquals(MediaType.WEB_NOVEL, m.mediaType)
        assertEquals("Lord of Mysteries", m.title)
        assertEquals("诡秘之主", m.titleNative)
        assertTrue(m.alternativeTitles.contains("Lord of the Mysteries"))
        assertEquals(listOf("Cuttlefish That Loves Diving"), m.credits.map { it.name })
    }

    @Test
    fun wikidataNeverInventsChapterCountsStatusOrMonth() = runBlocking {
        val m = wikidata().search("Lord of the Mysteries", MediaType.WEB_NOVEL).single()
        assertNull(m.totalChapters)
        assertEquals(PublicationStatus.UNKNOWN, m.pubStatus)
        assertEquals("year precision only: no month/day may appear", "2020", m.startDate)
        assertEquals(2020, m.startYear)
    }

    @Test
    fun wikidataEntityWithoutOptionalFieldsStillParses() = runBlocking {
        val m = wikidata().detail("Q137441819")!!
        assertEquals("Reverend Insanity", m.title)
        assertNull(m.startDate)
        assertTrue(m.credits.isEmpty())
        assertNotNull(m.synopsis)
    }

    // ---------------- HTTP layer ----------------

    @Test
    fun httpRetriesServerErrorsThenSucceeds() = runBlocking {
        var calls = 0
        val http = Fixtures.http { calls++; if (calls < 3) 503 to "" else 200 to "ok" }
        assertEquals("ok", http.get("https://example.test/x"))
        assertEquals(3, calls)
    }

    @Test
    fun httpRetriesRateLimitThenGivesUpWithClearError() = runBlocking {
        var calls = 0
        val http = Fixtures.http { calls++; 429 to "" }
        val e = runCatching { http.get("https://example.test/x") }.exceptionOrNull()
        assertTrue(e is ProviderException)
        assertTrue(e!!.message!!.contains("429"))
        assertEquals(3, calls)
    }

    @Test
    fun httpDoesNotRetryClientErrors() = runBlocking {
        var calls = 0
        val http = Fixtures.http { calls++; 404 to "" }
        assertTrue(runCatching { http.get("https://example.test/x") }.isFailure)
        assertEquals(1, calls)
    }

    @Test
    fun rateLimiterSpacesRequests() = runBlocking {
        var t = 1_000L
        val limiter = RateLimiter(500, now = { t })
        limiter.acquire()
        val before = t
        // simulate the clock advancing only 100ms: the limiter must wait the remaining 400ms (real delay)
        t += 100
        val started = System.nanoTime()
        limiter.acquire()
        val waitedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("limiter should have delayed, waited ${waitedMs}ms", waitedMs >= 300)
        assertTrue(before < t)
    }
}
