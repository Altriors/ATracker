package app.atracker.data

import androidx.room.withTransaction
import app.atracker.data.db.AppDatabase
import app.atracker.data.db.MediaCreditEntity
import app.atracker.data.db.MediaEntity
import app.atracker.data.db.MediaGenreEntity
import app.atracker.data.db.MediaSourceEntity
import app.atracker.data.db.MediaTitleEntity
import app.atracker.data.db.ReleaseInfoEntity
import app.atracker.domain.MediaMetadata
import app.atracker.domain.Providers

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
     * A title is also recognised through hard cross-references (MangaDex lists the AniList id);
     * names are never used to merge entries. Returns the stable internal media id.
     */
    suspend fun saveMetadata(meta: MediaMetadata): Long = db.withTransaction {
        val now = clock()
        val existingId = media.findIdBySource(meta.provider, meta.externalId)
            ?: meta.crossRefs.firstNotNullOfOrNull { media.findIdBySource(it.provider, it.externalId) }
        val id: Long
        if (existingId == null) {
            id = media.insert(meta.toEntity(id = 0, createdAt = now, now = now))
            media.insertSource(
                MediaSourceEntity(
                    mediaId = id, provider = meta.provider, externalId = meta.externalId,
                    url = meta.sourceUrl, fetchedAt = now
                )
            )
            meta.crossRefs.forEach {
                media.insertSource(MediaSourceEntity(mediaId = id, provider = it.provider, externalId = it.externalId, fetchedAt = 0))
            }
        } else {
            id = existingId
            val sources = media.sourcesFor(id)
            media.insertSource(
                MediaSourceEntity(
                    mediaId = id, provider = meta.provider, externalId = meta.externalId,
                    url = meta.sourceUrl, fetchedAt = now
                )
            )
            val old = media.getById(id)
            if (old != null && !old.isManual) {
                val primary = sources.minByOrNull { it.id }?.provider
                val fresh = meta.toEntity(id = id, createdAt = old.createdAt, now = now)
                // The first provider that created the entry stays authoritative; a secondary
                // provider only fills in values that are still missing.
                media.update(if (primary == null || primary == meta.provider) fresh.mergeKnown(old) else old.fillFrom(fresh, now))
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
            media.insertCredits(meta.credits.distinct().map {
                MediaCreditEntity(mediaId = id, role = it.role.name, name = it.name)
            })
        }
        // Dated releases are replaced per provider so a past "next episode" never lingers.
        media.clearReleases(id, meta.provider)
        if (meta.releases.isNotEmpty()) {
            media.insertReleases(meta.releases.map {
                ReleaseInfoEntity(mediaId = id, kind = it.kind, number = it.number, releaseAt = it.atMillis, source = meta.provider)
            })
        }
    }

    /** Creates a manual entry with no provider. Used for web novels and anything no API covers. */
    suspend fun createManual(meta: MediaMetadata): Long = db.withTransaction {
        val now = clock()
        val id = media.insert(meta.toEntity(id = 0, createdAt = now, now = now).copy(isManual = true))
        media.insertSource(
            MediaSourceEntity(mediaId = id, provider = Providers.MANUAL, externalId = "manual-$id", fetchedAt = now)
        )
        replaceDetails(id, meta.copy(provider = Providers.MANUAL))
        id
    }

    /**
     * Edits a manual entry in place. Entries that came from a provider cannot be edited here
     * (a refresh would overwrite the edit); returns false for them.
     */
    suspend fun updateManual(id: Long, meta: MediaMetadata): Boolean = db.withTransaction {
        val old = media.getById(id) ?: return@withTransaction false
        if (!old.isManual) return@withTransaction false
        val now = clock()
        media.update(meta.toEntity(id = id, createdAt = old.createdAt, now = now).copy(isManual = true))
        // Manual edits replace the lists wholesale, including making them empty.
        media.clearTitles(id); media.clearGenres(id); media.clearCredits(id)
        replaceDetails(id, meta.copy(provider = Providers.MANUAL))
        true
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

    /** Secondary provider: only fills values the entry does not have yet. */
    private fun MediaEntity.fillFrom(new: MediaEntity, now: Long) = copy(
        format = format ?: new.format,
        titleNative = titleNative ?: new.titleNative,
        synopsis = synopsis ?: new.synopsis,
        coverUrl = coverUrl ?: new.coverUrl,
        totalEpisodes = totalEpisodes ?: new.totalEpisodes,
        totalChapters = totalChapters ?: new.totalChapters,
        totalVolumes = totalVolumes ?: new.totalVolumes,
        episodeDurationMin = episodeDurationMin ?: new.episodeDurationMin,
        startDate = startDate ?: new.startDate,
        endDate = endDate ?: new.endDate,
        startYear = startYear ?: new.startYear,
        averageScore = averageScore ?: new.averageScore,
        countryOfOrigin = countryOfOrigin ?: new.countryOfOrigin,
        pubStatus = if (pubStatus == "UNKNOWN") new.pubStatus else pubStatus,
        metadataUpdatedAt = now
    )
}
