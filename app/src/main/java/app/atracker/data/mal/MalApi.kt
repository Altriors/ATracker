package app.atracker.data.mal

import app.atracker.data.provider.ProviderException
import app.atracker.data.provider.arr
import app.atracker.data.provider.get
import app.atracker.data.provider.int
import app.atracker.data.provider.lenientJson
import app.atracker.data.provider.obj
import app.atracker.data.provider.str
import app.atracker.domain.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Reads the signed-in user's anime and manga lists from the official MyAnimeList API v2. */
class MalApi(
    private val accessToken: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api.myanimelist.net/v2"
) {
    class Unauthorized : Exception("MyAnimeList says the sign-in expired. Connect again.")

    /** Set after a fetch: true when MAL accepted the extended field list (dates, tags, comments, rewatch counts). */
    var usedExtendedFields: Boolean = false
        private set

    suspend fun fetchList(kind: MalKind, onPage: (Int) -> Unit = {}): List<MalRecord> {
        val out = ArrayList<MalRecord>()
        val extended = tryFetch(kind, true, out, onPage)
        if (!extended) { out.clear(); tryFetch(kind, false, out, onPage) }
        usedExtendedFields = extended
        return out
    }

    /** Returns false when MAL rejected the extended field list (HTTP 400) so the caller can retry plain. */
    private suspend fun tryFetch(kind: MalKind, extended: Boolean, out: MutableList<MalRecord>, onPage: (Int) -> Unit): Boolean {
        val anime = kind == MalKind.ANIME
        val listFields = if (extended) {
            if (anime) "list_status{status,score,num_episodes_watched,is_rewatching,num_times_rewatched,start_date,finish_date,tags,comments}"
            else "list_status{status,score,num_volumes_read,num_chapters_read,is_rereading,num_times_reread,start_date,finish_date,tags,comments}"
        } else "list_status"
        var url: String? = "$baseUrl/users/@me/${kind.key}list".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "$listFields,${if (anime) "num_episodes" else "num_chapters,num_volumes"},media_type")
            .addQueryParameter("limit", "100")
            .addQueryParameter("nsfw", "true")
            .build().toString()
        var pages = 0
        while (url != null) {
            val (code, body) = get(url)
            if (code == 400 && extended && pages == 0) return false
            if (code == 401) throw Unauthorized()
            if (code !in 200..299) throw ProviderException("MyAnimeList returned HTTP $code for your ${kind.key} list.")
            val root = lenientJson.parseToJsonElement(body)
            val data = root["data"].arr() ?: throw ProviderException("MyAnimeList sent no list data.")
            data.mapNotNullTo(out) { it.obj()?.let { o -> parseNode(kind, o) } }
            pages++; onPage(out.size)
            url = root["paging"]["next"].str()
        }
        return true
    }

    private suspend fun get(url: String): Pair<Int, String> {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val res = withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(url).header("Authorization", "Bearer $accessToken")
                        .header("User-Agent", "ATracker/0.2 (personal Android app)").build()
                    client.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
                }
                if (res.first == 429 || res.first >= 500) { delay(2000L * (attempt + 1)); return@repeat }
                return res
            } catch (e: IOException) { last = e; delay(1500L * (attempt + 1)) }
        }
        throw ProviderException("Could not reach MyAnimeList: ${last?.message ?: "try again"}")
    }

    companion object {
        fun parseNode(kind: MalKind, entry: JsonObject): MalRecord? {
            val node = entry["node"].obj() ?: return null
            val ls = entry["list_status"].obj() ?: return null
            val id = node["id"].int() ?: return null
            val title = node["title"].str() ?: return null
            val anime = kind == MalKind.ANIME
            val score = ls["score"].int() ?: 0
            fun d(k: String) = ls[k].str()?.takeIf { !it.startsWith("0000") }
            return MalRecord(
                kind = kind, malId = id, title = title,
                status = MalXml.mapStatus(ls["status"].str()),
                progress = ls[if (anime) "num_episodes_watched" else "num_chapters_read"].int() ?: 0,
                volumes = if (anime) 0 else ls["num_volumes_read"].int() ?: 0,
                total = node[if (anime) "num_episodes" else "num_chapters"].int()?.takeIf { it > 0 },
                totalVolumes = if (anime) null else node["num_volumes"].int()?.takeIf { it > 0 },
                score = score.takeIf { it in 1..10 },
                startDate = d("start_date"), finishDate = d("finish_date"),
                repeatCount = ls[if (anime) "num_times_rewatched" else "num_times_reread"].int() ?: 0,
                tags = ls["tags"].arr().orEmpty().mapNotNull { it.str() },
                comments = ls["comments"].str(),
                mediaTypeHint = if (anime) null else when (node["media_type"].str()) {
                    "manhwa" -> MediaType.MANHWA
                    "manhua" -> MediaType.MANHUA
                    "light_novel" -> MediaType.LIGHT_NOVEL
                    "novel" -> MediaType.WEB_NOVEL
                    else -> MediaType.MANGA
                }
            )
        }
    }
}
