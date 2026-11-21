package app.atracker.data

import androidx.room.withTransaction
import app.atracker.data.db.AppDatabase
import app.atracker.data.db.MediaCreditEntity
import app.atracker.data.db.MediaEntity
import app.atracker.data.db.MediaGenreEntity
import app.atracker.data.db.MediaSourceEntity
import app.atracker.data.db.MediaTitleEntity
import app.atracker.domain.MediaMetadata

/**
 * Writes catalogue metadata. This class only ever touches catalogue tables, which is what
 * guarantees that a refresh can never overwrite personal progress, ratings or notes.
 */
class MediaRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val media get() = db.mediaDao()

    /**
     * Inserts the title, or refreshes the existing row for the same (provider, externalId).
     * Returns the stable internal media id.
     */
    suspend fun saveMetadata(meta: MediaMetadata): Long = db.withTransaction {
        val now = clock()
        val existingId = media.findIdBySource(meta.provider, meta.externalId)
        val id: Long
        if (existingId == null) {
            id = media.insert(meta.toEntity(id = 0, createdAt = now, now = now))
            media.insertSource(
                MediaSourceEntity(
                    mediaId = id, provider = meta.provider, externalId = meta.externalId,
                    url = meta.sourceUrl, fetchedAt = now
                )
            )
        } else {
            id = existingId
            val old = media.getById(id)
            if (old != null && !old.isManual) {
                // Keep known values when a provider response is missing a field (partial update).
                media.update(meta.toEntity(id = id, createdAt = old.createdAt, now = now).mergeKnown(old))
            }
        }
        replaceDetails(id, meta)
        id
    }

    private suspend fun replaceDetails(id: Long, meta: MediaMetadata) {
        if (meta.alternativeTitles.isNotEmpty()) {
            media.clearTitles(id)
            media.insertTitles(meta.alternativeTitles.distinct().map { MediaTitleEntity(mediaId = id, title = it) })
        }
        if (meta.genres.isNotEmpty() || meta.tags.isNotEmpty()) {
            media.clearGenres(id)
            media.insertGenres(
                meta.genres.distinct().map { MediaGenreEntity(id, it, "GENRE") } +
                    meta.tags.distinct().map { MediaGenreEntity(id, it, "TAG") }
            )
        }
        if (meta.credits.isNotEmpty()) {
            media.clearCredits(id)
            media.insertCredits(meta.credits.distinct().map { (role, name) ->
                MediaCreditEntity(mediaId = id, role = role.name, name = name)
            })
        }
    }

    /** Creates a manual entry with no provider. Used for web novels and anything no API covers. */
    suspend fun createManual(meta: MediaMetadata): Long = db.withTransaction {
        val now = clock()
        val id = media.insert(meta.toEntity(id = 0, createdAt = now, now = now).copy(isManual = true))
        media.insertSource(
            MediaSourceEntity(mediaId = id, provider = "manual", externalId = "manual-$id", fetchedAt = now)
        )
        replaceDetails(id, meta)
        id
    }

    private fun MediaMetadata.toEntity(id: Long, createdAt: Long, now: Long) = MediaEntity(
        id = id,
        mediaType = mediaType.name,
        format = format,
        title = title,
        titleNative = titleNative,
        synopsis = synopsis,
        coverUrl = coverUrl,
        pubStatus = pubStatus.name,
        totalEpisodes = totalEpisodes,
        totalChapters = totalChapters,
        totalVolumes = totalVolumes,
        episodeDurationMin = episodeDurationMin,
        startDate = startDate,
        endDate = endDate,
        startYear = startYear,
        averageScore = averageScore,
        countryOfOrigin = countryOfOrigin,
        isManual = false,
        createdAt = createdAt,
        metadataUpdatedAt = now
    )

    /** A refresh that lacks a value must not erase a value we already know. */
    private fun MediaEntity.mergeKnown(old: MediaEntity) = copy(
        format = format ?: old.format,
        titleNative = titleNative ?: old.titleNative,
        synopsis = synopsis ?: old.synopsis,
        coverUrl = coverUrl ?: old.coverUrl,
        totalEpisodes = totalEpisodes ?: old.totalEpisodes,
        totalChapters = totalChapters ?: old.totalChapters,
        totalVolumes = totalVolumes ?: old.totalVolumes,
        episodeDurationMin = episodeDurationMin ?: old.episodeDurationMin,
        startDate = startDate ?: old.startDate,
        endDate = endDate ?: old.endDate,
        startYear = startYear ?: old.startYear,
        averageScore = averageScore ?: old.averageScore,
        countryOfOrigin = countryOfOrigin ?: old.countryOfOrigin,
        pubStatus = if (pubStatus == "UNKNOWN") old.pubStatus else pubStatus
    )
}
