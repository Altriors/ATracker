package app.atracker.data

import app.atracker.data.db.LibraryItem
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaType
import app.atracker.domain.PublicationStatus

enum class LibrarySort(val label: String) {
    RECENT_UPDATE("Recently updated"),
    RECENT_ADDED("Recently added"),
    TITLE("Alphabetical"),
    RATING("Rating")
}

data class LibraryFilters(
    val query: String = "",
    val type: MediaType? = null,
    val status: LibraryStatus? = null,
    val genre: String? = null,
    val year: Int? = null,
    val pubStatus: PublicationStatus? = null,
    val favoritesOnly: Boolean = false,
    /** Minimum personal rating on the 0..100 scale. */
    val minRating: Int? = null,
    val sort: LibrarySort = LibrarySort.RECENT_UPDATE
) {
    val activeCount: Int
        get() = listOfNotNull(
            type, status, genre, year, pubStatus, minRating, if (favoritesOnly) true else null,
            query.takeIf { it.isNotBlank() }
        ).size
}

/** Extra searchable data for each media id, loaded from other tables. */
data class ItemExtras(val genres: Set<String> = emptySet(), val altTitles: List<String> = emptyList())

fun applyLibraryFilters(
    items: List<LibraryItem>,
    extras: Map<Long, ItemExtras>,
    f: LibraryFilters
): List<LibraryItem> {
    val q = f.query.trim().lowercase()
    val filtered = items.filter { item ->
        val m = item.media
        val e = item.entry
        val x = extras[m.id]
        (f.type == null || m.mediaType == f.type.name) &&
            (f.status == null || e.status == f.status.name) &&
            (f.genre == null || x?.genres?.contains(f.genre) == true) &&
            (f.year == null || m.startYear == f.year) &&
            (f.pubStatus == null || m.pubStatus == f.pubStatus.name) &&
            (!f.favoritesOnly || e.favorite) &&
            (f.minRating == null || (e.rating ?: -1) >= f.minRating) &&
            (q.isEmpty() ||
                m.title.lowercase().contains(q) ||
                m.titleNative?.lowercase()?.contains(q) == true ||
                x?.altTitles?.any { it.lowercase().contains(q) } == true)
    }
    return when (f.sort) {
        LibrarySort.RECENT_UPDATE -> filtered.sortedByDescending { it.entry.updatedAt }
        LibrarySort.RECENT_ADDED -> filtered.sortedByDescending { it.entry.addedAt }
        LibrarySort.TITLE -> filtered.sortedBy { it.media.title.lowercase() }
        // Unrated titles always sort last.
        LibrarySort.RATING -> filtered.sortedWith(
            compareByDescending<LibraryItem> { it.entry.rating != null }.thenByDescending { it.entry.rating ?: 0 }
                .thenBy { it.media.title.lowercase() }
        )
    }
}
