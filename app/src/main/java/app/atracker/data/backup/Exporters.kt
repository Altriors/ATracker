package app.atracker.data.backup

import app.atracker.data.db.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

private fun iso(ms: Long?): Any = if (ms == null) JSONObject.NULL else Instant.ofEpochMilli(ms).toString()
private fun <T> T?.orNull(): Any = this ?: JSONObject.NULL

/** Human-readable JSON of everything personal: library entries, history, franchises, lists. */
suspend fun exportJson(db: AppDatabase, now: Long = System.currentTimeMillis()): String {
    val items = db.libraryDao().allItems()
    val events = db.activityDao().allEvents()
    val genres = db.mediaDao().observeAllGenresOnce().groupBy({ it.mediaId }, { it.name })
    val titleById = items.associate { it.media.id to it.media.title }
    val root = JSONObject()
    root.put("app", "ATracker")
    root.put("format", 1)
    root.put("exportedAt", iso(now))
    root.put("library", JSONArray().also { arr ->
        items.forEach { i ->
            val e = i.entry
            arr.put(JSONObject().apply {
                put("title", i.media.title)
                put("type", i.media.mediaType)
                put("status", e.status)
                put("progress", e.progress)
                put("volumesRead", e.progressVolumes)
                put("ratingOutOf10", e.rating?.let { it / 10.0 }.orNull())
                put("favorite", e.favorite)
                put("rereads", e.repeatCount)
                put("startedAt", iso(e.startedAt))
                put("completedAt", iso(e.completedAt))
                put("addedAt", iso(e.addedAt))
                put("notes", e.notes.orNull())
                put("review", e.review.orNull())
                put("year", i.media.startYear.orNull())
                put("totalEpisodes", i.media.totalEpisodes.orNull())
                put("totalChapters", i.media.totalChapters.orNull())
                put("totalVolumes", i.media.totalVolumes.orNull())
                put("genres", JSONArray(genres[i.media.id].orEmpty()))
            })
        }
    })
    root.put("history", JSONArray().also { arr ->
        events.forEach { ev ->
            arr.put(JSONObject().apply {
                put("title", titleById[ev.mediaId].orNull())
                put("type", ev.type)
                put("unit", ev.unit)
                put("from", ev.fromValue.orNull())
                put("to", ev.toValue.orNull())
                put("delta", ev.delta)
                put("run", ev.runNumber)
                put("at", iso(ev.timestamp))
                put("voided", ev.voidedAt != null)
            })
        }
    })
    return root.toString(2)
}

private fun csvCell(v: Any?): String {
    val s = v?.toString() ?: ""
    return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

suspend fun exportCsv(db: AppDatabase): String {
    val sb = StringBuilder("title,type,status,progress,volumes_read,rating_out_of_10,favorite,rereads,started,completed,year,total_episodes,total_chapters,total_volumes,notes\n")
    db.libraryDao().allItems().forEach { i ->
        val e = i.entry
        sb.append(
            listOf(
                i.media.title, i.media.mediaType, e.status, e.progress, e.progressVolumes, e.rating?.let { it / 10.0 }, e.favorite,
                e.repeatCount, e.startedAt?.let { iso(it) }, e.completedAt?.let { iso(it) }, i.media.startYear,
                i.media.totalEpisodes, i.media.totalChapters, i.media.totalVolumes, e.notes
            ).joinToString(",") { csvCell(it) }
        ).append('\n')
    }
    return sb.toString()
}
