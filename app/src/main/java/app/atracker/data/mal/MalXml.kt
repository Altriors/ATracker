package app.atracker.data.mal

import app.atracker.domain.LibraryStatus
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Parser for the official MyAnimeList XML export (anime and manga lists, as exported or merged). */
object MalXml {
    class ParseException(message: String) : Exception(message)

    fun parse(input: InputStream): List<MalRecord> {
        val factory = DocumentBuilderFactory.newInstance()
        // An export is untrusted input: no DTDs, no external entities.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.isExpandEntityReferences = false }
        val doc = try {
            factory.newDocumentBuilder().parse(input)
        } catch (e: Exception) {
            throw ParseException("This is not a readable MyAnimeList export file.")
        }
        if (doc.documentElement?.nodeName != "myanimelist") throw ParseException("This file is not a MyAnimeList export (no <myanimelist> root).")
        val out = ArrayList<MalRecord>()
        for (kind in MalKind.entries) {
            val nodes = doc.getElementsByTagName(kind.key)
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                if (el.parentNode?.nodeName != "myanimelist") continue
                fun text(n: String) = el.getElementsByTagName(n).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
                fun int(n: String) = text(n)?.toIntOrNull()
                val anime = kind == MalKind.ANIME
                val id = int(if (anime) "series_animedb_id" else "manga_mangadb_id") ?: continue
                val title = text(if (anime) "series_title" else "manga_title") ?: continue
                val score = int("my_score") ?: 0
                fun date(n: String) = text(n)?.takeIf { !it.startsWith("0000") && !it.contains("-00") }
                out += MalRecord(
                    kind = kind, malId = id, title = title,
                    status = mapStatus(text("my_status")),
                    progress = int(if (anime) "my_watched_episodes" else "my_read_chapters") ?: 0,
                    volumes = if (anime) 0 else int("my_read_volumes") ?: 0,
                    total = int(if (anime) "series_episodes" else "manga_chapters")?.takeIf { it > 0 },
                    totalVolumes = if (anime) null else int("manga_volumes")?.takeIf { it > 0 },
                    score = score.takeIf { it in 1..10 },
                    startDate = date("my_start_date"), finishDate = date("my_finish_date"),
                    repeatCount = int(if (anime) "my_times_watched" else "my_times_read") ?: 0,
                    tags = text("my_tags")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                    comments = text("my_comments"),
                    mediaTypeHint = null
                )
            }
        }
        return out
    }

    fun mapStatus(raw: String?): LibraryStatus = when (raw?.trim()?.lowercase()) {
        "watching", "reading", "1" -> LibraryStatus.CURRENT
        "completed", "2" -> LibraryStatus.COMPLETED
        "on-hold", "on hold", "onhold", "3" -> LibraryStatus.ON_HOLD
        "dropped", "4" -> LibraryStatus.DROPPED
        "plan to watch", "plan to read", "plan_to_watch", "plan_to_read", "6" -> LibraryStatus.PLANNED
        else -> LibraryStatus.PLANNED
    }
}
