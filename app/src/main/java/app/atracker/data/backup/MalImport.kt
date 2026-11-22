package app.atracker.data.backup

import app.atracker.data.LibraryRepository
import app.atracker.data.MediaRepository
import app.atracker.data.db.ActivityEventEntity
import app.atracker.data.db.AppDatabase
import app.atracker.data.db.LibraryEntryEntity
import app.atracker.domain.ActivityType
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import androidx.room.withTransaction
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

data class MalItem(
    val title: String,
    val type: MediaType,
    val status: LibraryStatus,
    val progress: Int,
    val volumes: Int,
    val total: Int?,
    val totalVolumes: Int?,
    /** 0..100, null when the MAL score was 0 (unrated). */
    val rating: Int?,
    val startDate: String?,
    val finishDate: String?
)

data class ImportSummary(val imported: Int, val skippedExisting: Int, val failed: Int)

/**
 * Imports a MyAnimeList XML export (anime or manga list). Imported titles are stored as manual
 * entries (no network is needed); personal data already in the library is never overwritten.
 */
object MalImport {
    fun parse(input: InputStream): List<MalItem> {
        val factory = DocumentBuilderFactory.newInstance()
        // An export is untrusted input: no external entities.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.isExpandEntityReferences = false }
        val doc = factory.newDocumentBuilder().parse(input)
        val out = ArrayList<MalItem>()
        for (tag in listOf("anime", "manga")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                if (el.parentNode?.nodeName != "myanimelist") continue
                fun text(n: String) = el.getElementsByTagName(n).item(0)?.textContent?.trim()
                fun int(n: String) = text(n)?.toIntOrNull()
                val isAnime = tag == "anime"
                val title = text(if (isAnime) "series_title" else "manga_title")?.takeIf { it.isNotBlank() } ?: continue
                val status = mapStatus(text("my_status"), isAnime)
                val total = int(if (isAnime) "series_episodes" else "manga_chapters")?.takeIf { it > 0 }
                val score = int("my_score") ?: 0
                fun date(n: String) = text(n)?.takeIf { it.isNotBlank() && !it.startsWith("0000") }
                out += MalItem(
                    title = title,
                    type = if (isAnime) MediaType.ANIME else MediaType.MANGA,
                    status = status,
                    progress = int(if (isAnime) "my_watched_episodes" else "my_read_chapters") ?: 0,
                    volumes = if (isAnime) 0 else int("my_read_volumes") ?: 0,
                    total = total,
                    totalVolumes = if (isAnime) null else int("manga_volumes")?.takeIf { it > 0 },
                    rating = (score * 10).takeIf { score in 1..10 },
                    startDate = date("my_start_date"),
                    finishDate = date("my_finish_date")
                )
            }
        }
        return out
    }

    fun mapStatus(raw: String?, anime: Boolean): LibraryStatus = when (raw?.trim()?.lowercase()) {
        "watching", "reading", "1" -> LibraryStatus.CURRENT
        "completed", "2" -> LibraryStatus.COMPLETED
        "on-hold", "on hold", "onhold", "3" -> LibraryStatus.ON_HOLD
        "dropped", "4" -> LibraryStatus.DROPPED
        "plan to watch", "plan to read", "6" -> LibraryStatus.PLANNED
        else -> LibraryStatus.PLANNED
    }

    suspend fun import(
        db: AppDatabase, media: MediaRepository, items: List<MalItem>, now: Long = System.currentTimeMillis()
    ): ImportSummary {
        var imported = 0; var skipped = 0; var failed = 0
        for (item in items) {
            try {
                val done = db.withTransaction {
                    val existing = db.mediaDao().findIdByTitleType(item.title, item.type.name)
                    if (existing != null && db.libraryDao().getEntry(existing) != null) return@withTransaction false
                    val mediaId = existing ?: media.createManual(
                        MediaMetadata(
                            provider = app.atracker.domain.Providers.MANUAL, externalId = "", mediaType = item.type, title = item.title,
                            totalEpisodes = if (item.type == MediaType.ANIME) item.total else null,
                            totalChapters = if (item.type != MediaType.ANIME) item.total else null,
                            totalVolumes = item.totalVolumes
                        )
                    )
                    db.libraryDao().upsert(
                        LibraryEntryEntity(
                            mediaId = mediaId, status = item.status.name, progress = item.progress, progressVolumes = item.volumes,
                            rating = item.rating, addedAt = now, updatedAt = now,
                            startedAt = item.startDate?.let(::parseDate), completedAt = item.finishDate?.let(::parseDate)
                        )
                    )
                    // One note-style event so the history shows where this entry came from. No progress
                    // event is written, so bulk imports do not pollute "this week" activity totals.
                    db.activityDao().insert(
                        ActivityEventEntity(
                            mediaId = mediaId, type = ActivityType.ADDED.name, unit = item.type.progressUnit.name,
                            toValue = item.progress, runNumber = 1, timestamp = now, note = "Imported from MyAnimeList"
                        )
                    )
                    true
                }
                if (done) imported++ else skipped++
            } catch (e: Exception) {
                failed++
            }
        }
        return ImportSummary(imported, skipped, failed)
    }

    private fun parseDate(s: String): Long? = runCatching {
        java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}
