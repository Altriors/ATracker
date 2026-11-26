package app.atracker.data.mal

import androidx.room.withTransaction
import app.atracker.data.MediaRepository
import app.atracker.data.db.ActivityEventEntity
import app.atracker.data.db.AppDatabase
import app.atracker.data.db.ImportRecordEntity
import app.atracker.data.db.ImportRunEntity
import app.atracker.data.db.LibraryEntryEntity
import app.atracker.data.db.LibraryTagEntity
import app.atracker.data.db.MediaSourceEntity
import app.atracker.data.db.UserTagEntity
import app.atracker.data.provider.AniListProvider
import app.atracker.domain.ActivityType
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.Providers
import kotlinx.coroutines.CancellationException

/**
 * Two-phase MyAnimeList import. [plan] only reads (network + database) and returns a preview; nothing is
 * written. [commit] writes the confirmed plan, one transaction per title, and records every outcome in the
 * import history. Re-running an import is safe: titles are matched by their MAL id, so nothing is duplicated.
 */
class MalImporter(
    private val db: AppDatabase,
    private val media: MediaRepository,
    private val anilist: AniListProvider?,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun plan(records: List<MalRecord>, onProgress: (String) -> Unit = {}): ImportPlan {
        val unique = records.distinctBy { it.kind to it.malId }
        val dupes = records.size - unique.size
        val metaByKey = HashMap<String, MediaMetadata>()
        var note: String? = if (dupes > 0) "$dupes repeated entries in the file were ignored." else null

        // 1. Titles whose MAL id is already known locally need no online lookup.
        val unknown = unique.filter { db.mediaDao().findIdBySource(Providers.MAL, it.externalId) == null }

        // 2. Match the rest by MAL id on AniList (batch), never by title.
        if (anilist != null) {
            for (kind in MalKind.entries) {
                val ids = unknown.filter { it.kind == kind }.map { it.malId }
                if (ids.isEmpty()) continue
                onProgress("Matching ${ids.size} ${kind.key} titles on AniList…")
                anilist.lookupByMalIds(ids, kind == MalKind.ANIME).forEach { m ->
                    m.crossRefs.firstOrNull { it.provider == Providers.MAL }?.let { metaByKey[it.externalId] = m }
                }
            }
        } else if (unknown.isNotEmpty()) {
            note = (note?.plus(" ") ?: "") + "Online matching was unavailable; unmatched titles use MAL's own data."
        }

        val items = unique.map { r ->
            val existingByMal = db.mediaDao().findIdBySource(Providers.MAL, r.externalId)
            val meta = metaByKey[r.externalId]
            val existing = existingByMal ?: meta?.let { db.mediaDao().findIdBySource(it.provider, it.externalId) }
            when {
                existing != null -> {
                    val diffs = diff(db.libraryDao().getEntry(existing), r)
                    PlanItem(r, if (db.libraryDao().getEntry(existing) == null || diffs.isNotEmpty()) PlanAction.UPDATE_EXISTING else PlanAction.SAME, meta, existing, diffs)
                }
                meta != null -> PlanItem(r, PlanAction.ADD, meta, null)
                else -> {
                    val type = typeFor(r, null)
                    val same = db.mediaDao().findIdByTitleType(r.title, type.name)
                    if (same != null) PlanItem(r, PlanAction.POSSIBLE_DUPLICATE, null, same, linkToMediaId = same)
                    else PlanItem(r, PlanAction.MAL_ONLY, null, null)
                }
            }
        }
        return ImportPlan(items, note)
    }

    private fun typeFor(r: MalRecord, meta: MediaMetadata?): MediaType = meta?.mediaType
        ?: if (r.kind == MalKind.ANIME) MediaType.ANIME else (r.mediaTypeHint ?: MediaType.MANGA)

    /** What differs between the app's entry and MAL's. Empty when the entry does not exist or is identical. */
    internal fun diff(mine: LibraryEntryEntity?, r: MalRecord): List<String> {
        if (mine == null) return emptyList()
        val out = ArrayList<String>()
        if (mine.status != r.status.name) out += "Status: ${mine.status} → ${r.status.name}"
        if (mine.progress != r.progress) out += "Progress: ${mine.progress} → ${r.progress}"
        if (r.kind == MalKind.MANGA && mine.progressVolumes != r.volumes) out += "Volumes: ${mine.progressVolumes} → ${r.volumes}"
        val myScore = mine.rating?.div(10)
        if (myScore != r.score) out += "Score: ${myScore ?: "none"} → ${r.score ?: "none"}"
        return out
    }

    suspend fun commit(plan: ImportPlan, source: String, defaultPolicy: ConflictPolicy): ImportReport {
        val dao = db.importDao()
        val runId = dao.insertRun(ImportRunEntity(source = source, startedAt = clock(), status = "RUNNING", policy = defaultPolicy.name))
        var added = 0; var updated = 0; var skipped = 0; var unmatched = 0; var failed = 0
        val records = ArrayList<ImportRecordEntity>()
        for (item in plan.items) {
            val r = item.record
            fun rec(o: Outcome, detail: String?, mediaId: Long? = null) =
                ImportRecordEntity(runId = runId, malKind = r.kind.key, malId = r.malId, title = r.title, outcome = o.name, detail = detail, mediaId = mediaId)
            try {
                if (item.skip) { skipped++; records += rec(Outcome.SKIPPED, "Skipped by you"); continue }
                val result = db.withTransaction { apply(item, item.policy ?: defaultPolicy) }
                when (result.first) {
                    Outcome.ADDED -> added++
                    Outcome.UPDATED -> updated++
                    Outcome.SKIPPED -> skipped++
                    Outcome.UNMATCHED -> { added++; unmatched++ }
                    Outcome.FAILED -> failed++
                }
                records += rec(result.first, result.second, result.third)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++; records += rec(Outcome.FAILED, e.message ?: e.javaClass.simpleName)
            }
        }
        dao.insertRecords(records)
        val run = dao.getRun(runId)!!
        dao.updateRun(run.copy(finishedAt = clock(), status = "DONE", added = added, updated = updated, skipped = skipped, unmatched = unmatched, failed = failed))
        return ImportReport(runId, added, updated, skipped, unmatched, failed)
    }

    private suspend fun apply(item: PlanItem, policy: ConflictPolicy): Triple<Outcome, String?, Long?> {
        val r = item.record
        val now = clock()
        var onlyMal = false
        val mediaId: Long = when {
            item.linkToMediaId != null -> item.linkToMediaId
            item.existingMediaId != null -> {
                // Keep the catalogue entry fresh when we have metadata; this never touches personal tables.
                item.meta?.let { media.saveMetadata(it) }
                item.existingMediaId
            }
            item.meta != null -> media.saveMetadata(item.meta)
            else -> {
                onlyMal = true
                media.createManual(
                    MediaMetadata(
                        provider = Providers.MANUAL, externalId = "", mediaType = typeFor(r, null), title = r.title,
                        totalEpisodes = if (r.kind == MalKind.ANIME) r.total else null,
                        totalChapters = if (r.kind == MalKind.MANGA) r.total else null,
                        totalVolumes = r.totalVolumes
                    )
                )
            }
        }
        db.mediaDao().insertSource(MediaSourceEntity(mediaId = mediaId, provider = Providers.MAL, externalId = r.externalId, url = "https://myanimelist.net/${r.kind.key}/${r.malId}", fetchedAt = now))
        val type = MediaType.from(db.mediaDao().getById(mediaId)?.mediaType)
        val mine = db.libraryDao().getEntry(mediaId)
        val rating = r.score?.let { it * 10 }

        if (mine == null) {
            db.libraryDao().upsert(
                LibraryEntryEntity(
                    mediaId = mediaId, status = r.status.name, progress = r.progress, progressVolumes = r.volumes, rating = rating,
                    notes = r.comments, repeatCount = r.repeatCount, startedAt = r.startDate?.let(::parseDate),
                    completedAt = r.finishDate?.let(::parseDate), addedAt = now, updatedAt = now
                )
            )
            attachTags(mediaId, r.tags)
            // One note-style event; no progress event, so a bulk import does not appear as this week's activity.
            db.activityDao().insert(ActivityEventEntity(mediaId = mediaId, type = ActivityType.ADDED.name, unit = type.progressUnit.name, toValue = r.progress, runNumber = 1, timestamp = now, note = "Imported from MyAnimeList"))
            return Triple(if (onlyMal) Outcome.UNMATCHED else Outcome.ADDED, if (onlyMal) "No online match; stored with MAL's data only" else null, mediaId)
        }

        return when (policy) {
            ConflictPolicy.KEEP_MINE -> Triple(Outcome.SKIPPED, if (item.differences.isEmpty()) "Already in your library" else "Kept your record (MAL differs: ${item.differences.joinToString("; ")})", mediaId)
            ConflictPolicy.FILL_EMPTY -> {
                val filled = ArrayList<String>()
                var e = mine
                if (e.rating == null && rating != null) { e = e.copy(rating = rating); filled += "score" }
                if (e.notes == null && r.comments != null) { e = e.copy(notes = r.comments); filled += "notes" }
                if (e.startedAt == null && r.startDate != null) { e = e.copy(startedAt = parseDate(r.startDate)); filled += "start date" }
                if (e.completedAt == null && r.finishDate != null) { e = e.copy(completedAt = parseDate(r.finishDate)); filled += "finish date" }
                if (e.repeatCount == 0 && r.repeatCount > 0) { e = e.copy(repeatCount = r.repeatCount); filled += "repeat count" }
                attachTags(mediaId, r.tags)
                if (filled.isEmpty()) Triple(Outcome.SKIPPED, "Nothing to fill; your record already has these", mediaId)
                else { db.libraryDao().upsert(e.copy(updatedAt = now)); Triple(Outcome.UPDATED, "Filled: ${filled.joinToString()}", mediaId) }
            }
            ConflictPolicy.USE_MAL -> {
                if (item.differences.isEmpty()) return Triple(Outcome.SKIPPED, "Already identical", mediaId)
                db.libraryDao().upsert(
                    mine.copy(
                        status = r.status.name, progress = r.progress, progressVolumes = if (r.kind == MalKind.MANGA) r.volumes else mine.progressVolumes,
                        rating = rating, notes = mine.notes ?: r.comments, repeatCount = maxOf(mine.repeatCount, r.repeatCount),
                        startedAt = r.startDate?.let(::parseDate) ?: mine.startedAt, completedAt = r.finishDate?.let(::parseDate) ?: mine.completedAt, updatedAt = now
                    )
                )
                attachTags(mediaId, r.tags)
                db.activityDao().insert(ActivityEventEntity(mediaId = mediaId, type = ActivityType.CORRECTION.name, unit = type.progressUnit.name, fromValue = mine.progress, toValue = r.progress, runNumber = mine.repeatCount + 1, timestamp = now, note = "MyAnimeList import replaced: ${item.differences.joinToString("; ")}"))
                Triple(Outcome.UPDATED, item.differences.joinToString("; "), mediaId)
            }
        }
    }

    private suspend fun attachTags(mediaId: Long, tags: List<String>) {
        for (name in tags) {
            val dao = db.listDao()
            val tag = dao.tagByName(name) ?: run { dao.insertTag(UserTagEntity(name = name)); dao.tagByName(name) } ?: continue
            dao.addLibraryTag(LibraryTagEntity(mediaId, tag.id))
        }
    }

    private fun parseDate(s: String): Long? = runCatching {
        java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}
