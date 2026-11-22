package app.atracker.data

import app.atracker.data.db.ActivityEventEntity
import app.atracker.data.db.LibraryItem
import app.atracker.data.db.MediaCreditEntity
import app.atracker.domain.ActivityType
import app.atracker.domain.CreditRole
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaType
import app.atracker.domain.ProgressUnit

/** Statistics for one format. Units are never mixed: each format reports only its own unit. */
data class TypeStats(
    val type: MediaType,
    val titles: Int,
    val byStatus: Map<LibraryStatus, Int>,
    val completed: Int,
    val progress: Int,
    val volumes: Int,
    val unit: ProgressUnit,
    /** Mean personal rating on the 0..100 scale, null when nothing is rated. */
    val averageRating: Double?,
    val ratedCount: Int,
    /** Anime only, and only counting titles whose episode length is known. */
    val watchMinutes: Long?,
    val watchMinutesCoverage: Pair<Int, Int>?
)

data class Ranked(val name: String, val count: Int)

data class LibraryStats(
    val totalTitles: Int,
    val perType: List<TypeStats>,
    val topGenres: List<Ranked>,
    val topAuthors: List<Ranked>,
    val topStudios: List<Ranked>,
    val favorites: Int,
    val averageRating: Double?
)

fun computeLibraryStats(
    items: List<LibraryItem>,
    genres: Map<Long, List<String>>,
    credits: Map<Long, List<MediaCreditEntity>>
): LibraryStats {
    val perType = MediaType.entries.mapNotNull { t ->
        val xs = items.filter { it.media.mediaType == t.name }
        if (xs.isEmpty()) return@mapNotNull null
        val rated = xs.mapNotNull { it.entry.rating }.filter { it > 0 }
        val minutesKnown = if (t == MediaType.ANIME) xs.filter { (it.media.episodeDurationMin ?: 0) > 0 } else emptyList()
        TypeStats(
            type = t,
            titles = xs.size,
            byStatus = LibraryStatus.entries.associateWith { s -> xs.count { it.entry.status == s.name } }.filterValues { it > 0 },
            completed = xs.count { it.entry.status == LibraryStatus.COMPLETED.name },
            progress = xs.sumOf { it.entry.progress },
            volumes = xs.sumOf { it.entry.progressVolumes },
            unit = t.progressUnit,
            averageRating = rated.takeIf { it.isNotEmpty() }?.average(),
            ratedCount = rated.size,
            watchMinutes = if (t == MediaType.ANIME) minutesKnown.sumOf { it.entry.progress.toLong() * (it.media.episodeDurationMin ?: 0) } else null,
            watchMinutesCoverage = if (t == MediaType.ANIME) minutesKnown.size to xs.size else null
        )
    }
    fun rank(names: List<String>) = names.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(10).map { Ranked(it.key, it.value) }
    val ids = items.map { it.media.id }
    val allRated = items.mapNotNull { it.entry.rating }.filter { it > 0 }
    return LibraryStats(
        totalTitles = items.size,
        perType = perType,
        topGenres = rank(ids.flatMap { genres[it].orEmpty().distinct() }),
        topAuthors = rank(ids.flatMap { id -> credits[id].orEmpty().filter { it.role == CreditRole.AUTHOR.name }.map { it.name }.distinct() }),
        topStudios = rank(ids.flatMap { id -> credits[id].orEmpty().filter { it.role == CreditRole.STUDIO.name }.map { it.name }.distinct() }),
        favorites = items.count { it.entry.favorite },
        averageRating = allRated.takeIf { it.isNotEmpty() }?.average()
    )
}

/** Units consumed in a period, per unit (EPISODE / CHAPTER / VOLUME). Voided events and corrections' sign are respected. */
fun activityTotals(events: List<ActivityEventEntity>): Map<String, Int> =
    events.filter { it.voidedAt == null && (it.type == ActivityType.PROGRESS.name || it.type == ActivityType.VOLUME_PROGRESS.name) }
        .groupBy { it.unit }
        .mapValues { (_, v) -> v.sumOf { it.delta }.coerceAtLeast(0) }
        .filterValues { it > 0 }
