package app.atracker.data

import androidx.room.withTransaction
import app.atracker.data.db.ActivityEventEntity
import app.atracker.data.db.AppDatabase
import app.atracker.data.db.LibraryEntryEntity
import app.atracker.data.db.MediaEntity
import app.atracker.domain.ActivityType
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaType
import app.atracker.domain.ProgressUnit

/** The tracked total for the unit this media type is measured in; null when unknown. */
fun MediaEntity.trackedTotal(): Int? = when (MediaType.from(mediaType).progressUnit) {
    ProgressUnit.EPISODE -> totalEpisodes
    ProgressUnit.VOLUME -> totalVolumes
    else -> totalChapters
}

/**
 * All personal-data writes go through here. Every state change appends an event to the activity
 * log in the same transaction, so the log and the current state can never disagree.
 */
class LibraryRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val library get() = db.libraryDao()
    private val activity get() = db.activityDao()
    private val mediaDao get() = db.mediaDao()

    suspend fun addToLibrary(mediaId: Long, status: LibraryStatus = LibraryStatus.PLANNED): Boolean =
        db.withTransaction {
            if (library.getEntry(mediaId) != null) return@withTransaction false
            val media = mediaDao.getById(mediaId) ?: return@withTransaction false
            val now = clock()
            library.upsert(
                LibraryEntryEntity(
                    mediaId = mediaId, status = status.name, addedAt = now, updatedAt = now,
                    startedAt = if (status == LibraryStatus.CURRENT) now else null
                )
            )
            log(media, ActivityType.ADDED, now, note = status.name)
            true
        }

    suspend fun removeFromLibrary(mediaId: Long) = db.withTransaction {
        // Activity history is deliberately kept; only the library entry goes.
        library.delete(mediaId)
    }

    /** Sets absolute progress (episodes/chapters, or volumes for light novels). */
    suspend fun setProgress(mediaId: Long, newProgress: Int) = db.withTransaction {
        val media = mediaDao.getById(mediaId) ?: return@withTransaction
        val entry = library.getEntry(mediaId) ?: return@withTransaction
        val now = clock()
        val total = media.trackedTotal()
        val target = newProgress.coerceAtLeast(0).let { if (total != null) it.coerceAtMost(total) else it }
        if (target == entry.progress) return@withTransaction

        val unit = MediaType.from(media.mediaType).progressUnit
        val run = entry.repeatCount + 1
        var status = LibraryStatus.from(entry.status)
        var startedAt = entry.startedAt
        var completedAt = entry.completedAt

        if (target > 0 && startedAt == null) startedAt = now
        if (target > 0 && (status == LibraryStatus.PLANNED || status == LibraryStatus.NOT_STARTED)) {
            status = LibraryStatus.CURRENT
            log(media, ActivityType.STARTED, now, run = run)
        }

        log(
            media, ActivityType.PROGRESS, now, from = entry.progress, to = target,
            delta = target - entry.progress, run = run
        )

        if (total != null && target >= total && status != LibraryStatus.COMPLETED) {
            status = LibraryStatus.COMPLETED
            completedAt = now
            log(media, ActivityType.COMPLETED, now, run = run)
        } else if (status == LibraryStatus.COMPLETED && (total == null || target < total)) {
            status = LibraryStatus.CURRENT
            completedAt = null
        }

        library.upsert(
            entry.copy(
                progress = target, status = status.name, startedAt = startedAt, completedAt = completedAt,
                lastActivityAt = now, updatedAt = now
            )
        )
    }

    suspend fun incrementProgress(mediaId: Long, by: Int = 1) {
        val entry = library.getEntry(mediaId) ?: return
        setProgress(mediaId, entry.progress + by)
    }

    /** Volumes read alongside chapters (manga). Light novels use [setProgress] for volumes. */
    suspend fun setVolumeProgress(mediaId: Long, volumes: Int) = db.withTransaction {
        val media = mediaDao.getById(mediaId) ?: return@withTransaction
        val entry = library.getEntry(mediaId) ?: return@withTransaction
        val now = clock()
        val cap = media.totalVolumes
        val target = volumes.coerceAtLeast(0).let { if (cap != null) it.coerceAtMost(cap) else it }
        if (target == entry.progressVolumes) return@withTransaction
        log(
            media, ActivityType.VOLUME_PROGRESS, now, from = entry.progressVolumes, to = target,
            delta = target - entry.progressVolumes, run = entry.repeatCount + 1, unit = ProgressUnit.VOLUME
        )
        library.upsert(entry.copy(progressVolumes = target, lastActivityAt = now, updatedAt = now))
    }

    suspend fun setStatus(mediaId: Long, status: LibraryStatus) = db.withTransaction {
        val media = mediaDao.getById(mediaId) ?: return@withTransaction
        val entry = library.getEntry(mediaId) ?: return@withTransaction
        if (entry.status == status.name) return@withTransaction
        val now = clock()
        val run = entry.repeatCount + 1
        val type = when (status) {
            LibraryStatus.COMPLETED -> ActivityType.COMPLETED
            LibraryStatus.DROPPED -> ActivityType.DROPPED
            LibraryStatus.ON_HOLD -> ActivityType.ON_HOLD
            else -> ActivityType.STATUS_CHANGED
        }
        log(media, type, now, run = run, note = "${entry.status} -> ${status.name}")
        library.upsert(
            entry.copy(
                status = status.name,
                startedAt = entry.startedAt ?: if (status == LibraryStatus.CURRENT) now else null,
                completedAt = if (status == LibraryStatus.COMPLETED) now else entry.completedAt,
                lastActivityAt = now, updatedAt = now
            )
        )
    }

    /** Rating 0..100 in steps of 5, or null to clear. */
    suspend fun setRating(mediaId: Long, rating: Int?) = db.withTransaction {
        val media = mediaDao.getById(mediaId) ?: return@withTransaction
        val entry = library.getEntry(mediaId) ?: return@withTransaction
        val clean = rating?.coerceIn(0, 100)
        if (clean == entry.rating) return@withTransaction
        val now = clock()
        log(media, ActivityType.RATING_CHANGED, now, from = entry.rating, to = clean, run = entry.repeatCount + 1)
        library.upsert(entry.copy(rating = clean, updatedAt = now))
    }

    suspend fun setFavorite(mediaId: Long, favorite: Boolean) = db.withTransaction {
        val entry = library.getEntry(mediaId) ?: return@withTransaction
        library.upsert(entry.copy(favorite = favorite, updatedAt = clock()))
    }

    suspend fun setNotes(mediaId: Long, notes: String?, review: String?) = db.withTransaction {
        val entry = library.getEntry(mediaId) ?: return@withTransaction
        library.upsert(entry.copy(notes = notes?.ifBlank { null }, review = review?.ifBlank { null }, updatedAt = clock()))
    }

    /** Starts a rewatch/reread: a fresh run with its own progress, while keeping all history. */
    suspend fun startNewRun(mediaId: Long) = db.withTransaction {
        val media = mediaDao.getById(mediaId) ?: return@withTransaction
        val entry = library.getEntry(mediaId) ?: return@withTransaction
        val now = clock()
        val newRun = entry.repeatCount + 2 // repeatCount counts finished repeats; new run follows current
        log(media, ActivityType.REREAD_STARTED, now, run = newRun)
        library.upsert(
            entry.copy(
                repeatCount = entry.repeatCount + 1, progress = 0, progressVolumes = 0,
                status = LibraryStatus.CURRENT.name, startedAt = now, completedAt = null,
                lastActivityAt = now, updatedAt = now
            )
        )
    }

    /** Voids an event so statistics ignore it. Current progress is never changed by this. */
    suspend fun voidEvent(eventId: Long, voided: Boolean = true) {
        activity.void(eventId, if (voided) clock() else null)
    }

    private suspend fun log(
        media: MediaEntity, type: ActivityType, now: Long,
        from: Int? = null, to: Int? = null, delta: Int = 0, run: Int = 1,
        note: String? = null, unit: ProgressUnit? = null
    ) {
        val u = unit ?: when (type) {
            ActivityType.PROGRESS, ActivityType.VOLUME_PROGRESS -> MediaType.from(media.mediaType).progressUnit
            else -> ProgressUnit.NONE
        }
        activity.insert(
            ActivityEventEntity(
                mediaId = media.id, type = type.name, unit = u.name, fromValue = from, toValue = to,
                delta = delta, runNumber = run, timestamp = now, note = note
            )
        )
    }
}
