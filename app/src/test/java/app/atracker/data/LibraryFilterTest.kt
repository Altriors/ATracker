package app.atracker.data

import app.atracker.data.db.LibraryEntryEntity
import app.atracker.data.db.LibraryItem
import app.atracker.data.db.MediaEntity
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaType
import app.atracker.domain.PublicationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
    private fun item(
        id: Long, title: String, type: MediaType = MediaType.MANGA, status: LibraryStatus = LibraryStatus.CURRENT,
        rating: Int? = null, fav: Boolean = false, year: Int? = null, pub: PublicationStatus = PublicationStatus.ONGOING,
        updated: Long = id, added: Long = id, native: String? = null
    ) = LibraryItem(
        LibraryEntryEntity(mediaId = id, status = status.name, rating = rating, favorite = fav, addedAt = added, updatedAt = updated),
        MediaEntity(id = id, mediaType = type.name, title = title, titleNative = native, startYear = year, pubStatus = pub.name, createdAt = 0, metadataUpdatedAt = 0)
    )

    private val items = listOf(
        item(1, "Eleceed", MediaType.MANHWA, rating = 90, fav = true, year = 2018),
        item(2, "Frieren", MediaType.ANIME, LibraryStatus.COMPLETED, rating = 95, year = 2023, pub = PublicationStatus.COMPLETED),
        item(3, "Boogiepop", MediaType.LIGHT_NOVEL, LibraryStatus.DROPPED, year = 2000),
        item(4, "Reverend Insanity", MediaType.WEB_NOVEL, LibraryStatus.PLANNED, native = "蛊真人"),
        item(5, "Alpha", MediaType.MANHWA, rating = 60, year = 2018)
    )
    private val extras = mapOf(
        1L to ItemExtras(setOf("Action", "Fantasy"), listOf("Ilrisid")),
        2L to ItemExtras(setOf("Fantasy"), listOf("Sousou no Frieren")),
        4L to ItemExtras(setOf("Fantasy"), listOf("Gu Zhenren"))
    )

    private fun ids(f: LibraryFilters) = applyLibraryFilters(items, extras, f).map { it.media.id }

    @Test fun noFilterReturnsEverythingNewestFirst() = assertEquals(listOf(5L, 4L, 3L, 2L, 1L), ids(LibraryFilters()))
    @Test fun filtersByType() = assertEquals(setOf(1L, 5L), ids(LibraryFilters(type = MediaType.MANHWA)).toSet())
    @Test fun filtersByStatus() = assertEquals(listOf(2L), ids(LibraryFilters(status = LibraryStatus.COMPLETED)))
    @Test fun filtersByGenre() = assertEquals(setOf(1L, 2L, 4L), ids(LibraryFilters(genre = "Fantasy")).toSet())
    @Test fun filtersByGenreWithNoMatches() = assertEquals(emptyList<Long>(), ids(LibraryFilters(genre = "Romance")))
    @Test fun filtersByYear() = assertEquals(setOf(1L, 5L), ids(LibraryFilters(year = 2018)).toSet())
    @Test fun filtersByPublicationStatus() = assertEquals(listOf(2L), ids(LibraryFilters(pubStatus = PublicationStatus.COMPLETED)))
    @Test fun filtersFavourites() = assertEquals(listOf(1L), ids(LibraryFilters(favoritesOnly = true)))
    @Test fun minimumRatingExcludesUnrated() = assertEquals(setOf(1L, 2L), ids(LibraryFilters(minRating = 90)).toSet())

    @Test fun searchMatchesTitleNativeAndAlternativeTitles() {
        assertEquals(listOf(1L), ids(LibraryFilters(query = "eleceed")))
        assertEquals("alt title", listOf(1L), ids(LibraryFilters(query = "ilrisid")))
        assertEquals("native title", listOf(4L), ids(LibraryFilters(query = "蛊真人")))
        assertEquals("romaji alias", listOf(2L), ids(LibraryFilters(query = "sousou")))
    }

    @Test fun combinedFiltersAreAnded() =
        assertEquals(listOf(1L), ids(LibraryFilters(type = MediaType.MANHWA, genre = "Action", year = 2018)))

    @Test fun sortsAlphabetically() = assertEquals(listOf(5L, 3L, 1L, 2L, 4L), ids(LibraryFilters(sort = LibrarySort.TITLE)))

    @Test fun sortsByRatingWithUnratedLast() =
        assertEquals(listOf(2L, 1L, 5L, 3L, 4L), ids(LibraryFilters(sort = LibrarySort.RATING)))

    @Test fun sortsByRecentlyAdded() {
        val custom = listOf(item(1, "A", added = 50, updated = 1), item(2, "B", added = 10, updated = 99))
        assertEquals(listOf(1L, 2L), applyLibraryFilters(custom, emptyMap(), LibraryFilters(sort = LibrarySort.RECENT_ADDED)).map { it.media.id })
        assertEquals(listOf(2L, 1L), applyLibraryFilters(custom, emptyMap(), LibraryFilters(sort = LibrarySort.RECENT_UPDATE)).map { it.media.id })
    }
}

class DiscoverFilterTest {
    private fun m(t: String, y: Int?, s: Int?, g: List<String>, st: app.atracker.domain.PublicationStatus = app.atracker.domain.PublicationStatus.FINISHED) =
        app.atracker.domain.MediaMetadata(
            provider = "anilist", externalId = t, mediaType = app.atracker.domain.MediaType.MANGA, title = t,
            startYear = y, averageScore = s, genres = g, pubStatus = st
        )

    private val data = listOf(
        m("B", 2010, 70, listOf("Action")), m("A", 2022, 85, listOf("Fantasy", "Action")), m("C", null, null, listOf("Fantasy"))
    )

    @org.junit.Test
    fun filtersByGenreYearScore() {
        assertEquals(listOf("B", "A"), data.applyFilters(DiscoverFilters(genre = "action")).map { it.title })
        assertEquals(listOf("A"), data.applyFilters(DiscoverFilters(yearFrom = 2020)).map { it.title })
        assertEquals(listOf("A"), data.applyFilters(DiscoverFilters(minScore = 80)).map { it.title })
    }

    @org.junit.Test
    fun sorts() {
        assertEquals(listOf("A", "B", "C"), data.applyFilters(DiscoverFilters(sort = DiscoverSort.SCORE)).map { it.title })
        assertEquals(listOf("A", "B", "C"), data.applyFilters(DiscoverFilters(sort = DiscoverSort.TITLE)).map { it.title })
        assertEquals(listOf("A", "B", "C"), data.applyFilters(DiscoverFilters(sort = DiscoverSort.NEWEST)).map { it.title })
    }
}
