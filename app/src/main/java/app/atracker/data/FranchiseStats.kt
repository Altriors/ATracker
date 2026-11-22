package app.atracker.data

import app.atracker.data.db.LibraryEntryEntity
import app.atracker.data.db.MediaEntity
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaType
import app.atracker.domain.ProgressUnit

/**
 * Statistics for ONE format inside a franchise. Units are never mixed: anime counts episodes,
 * manga/manhwa/manhua/web novels count chapters, light novels count volumes.
 */
data class FormatStats(
    val type: MediaType,
    val unit: ProgressUnit,
    val titles: Int,
    val inLibrary: Int,
    val completed: Int,
    /** Sum of current progress of the entries in this format (each title counted once). */
    val progress: Int,
    /** Volumes read for formats that track volumes alongside chapters (manga). */
    val volumes: Int,
    /** Average personal rating on the 0..100 scale of rated titles, or null when none is rated. */
    val averageRating: Double?
)

data class FranchiseStats(
    val formats: List<FormatStats>,
    val titles: Int,
    val inLibrary: Int,
    val completed: Int,
    val averageRating: Double?
)

/**
 * Combined franchise statistics from the members' own library entries. A member that is not in
 * the library contributes no progress. Nothing is double counted: each title appears once and
 * progress is only summed within a format.
 */
fun computeFranchiseStats(members: List<Pair<MediaEntity, LibraryEntryEntity?>>): FranchiseStats {
    val formats = members.groupBy { MediaType.from(it.first.mediaType) }.map { (type, list) ->
        val entries = list.mapNotNull { it.second }
        val rated = entries.mapNotNull { it.rating }
        FormatStats(
            type = type,
            unit = type.progressUnit,
            titles = list.size,
            inLibrary = entries.size,
            completed = entries.count { it.status == LibraryStatus.COMPLETED.name },
            progress = entries.sumOf { it.progress },
            volumes = if (type.hasVolumes && type.progressUnit != ProgressUnit.VOLUME) entries.sumOf { it.progressVolumes } else 0,
            averageRating = if (rated.isEmpty()) null else rated.average()
        )
    }.sortedBy { it.type.ordinal }

    val allEntries = members.mapNotNull { it.second }
    val allRated = allEntries.mapNotNull { it.rating }
    return FranchiseStats(
        formats = formats,
        titles = members.size,
        inLibrary = allEntries.size,
        completed = allEntries.count { it.status == LibraryStatus.COMPLETED.name },
        averageRating = if (allRated.isEmpty()) null else allRated.average()
    )
}
