package app.atracker.data

import app.atracker.data.db.MediaEntity
import app.atracker.domain.MediaMetadata
import app.atracker.domain.PublicationStatus

/** Client-side refinement of Discover results. A result that lacks the filtered field is hidden (we cannot confirm it matches). */
data class DiscoverFilters(
    val genre: String? = null,
    val yearFrom: Int? = null,
    val status: PublicationStatus? = null,
    /** Minimum provider score on the 0..100 scale. */
    val minScore: Int? = null,
    val sort: DiscoverSort = DiscoverSort.RELEVANCE
) {
    val active: Boolean get() = genre != null || yearFrom != null || status != null || minScore != null || sort != DiscoverSort.RELEVANCE
}

enum class DiscoverSort(val label: String) { RELEVANCE("Provider order"), SCORE("Score"), NEWEST("Newest"), TITLE("Title") }

fun List<MediaMetadata>.applyFilters(f: DiscoverFilters): List<MediaMetadata> {
    val filtered = filter { m ->
        (f.genre == null || m.genres.any { it.equals(f.genre, true) }) &&
            (f.yearFrom == null || (m.startYear ?: Int.MIN_VALUE) >= f.yearFrom) &&
            (f.status == null || m.pubStatus == f.status) &&
            (f.minScore == null || (m.averageScore ?: -1) >= f.minScore)
    }
    return when (f.sort) {
        DiscoverSort.RELEVANCE -> filtered
        DiscoverSort.SCORE -> filtered.sortedByDescending { it.averageScore ?: -1 }
        DiscoverSort.NEWEST -> filtered.sortedByDescending { it.startYear ?: -1 }
        DiscoverSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
    }
}

fun List<MediaEntity>.applyLocalFilters(f: DiscoverFilters): List<MediaEntity> = filter { m ->
    (f.yearFrom == null || (m.startYear ?: Int.MIN_VALUE) >= f.yearFrom) &&
        (f.status == null || m.pubStatus == f.status.name) &&
        (f.minScore == null || (m.averageScore ?: -1) >= f.minScore)
}
