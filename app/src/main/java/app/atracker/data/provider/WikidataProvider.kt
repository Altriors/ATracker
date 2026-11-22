package app.atracker.data.provider

import app.atracker.domain.Credit
import app.atracker.domain.CreditRole
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.Providers
import app.atracker.domain.PublicationStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale

/**
 * Wikidata: the identification source for web novels and light novels that AniList does not
 * have (e.g. Lord of the Mysteries, Reverend Insanity). It reliably gives a title, aliases,
 * author and (partial) publication date, but NO chapter counts, covers or publication status.
 * Those stay unknown rather than guessed; the user can fill them in by hand.
 * Only items whose description says "novel" are returned.
 */
class WikidataProvider(
    private val http: ApiHttp,
    private val apiUrl: String = "https://www.wikidata.org/w/api.php"
) : MetadataProvider {
    override val id = Providers.WIKIDATA
    override val displayName = "Wikidata"

    override fun supports(type: MediaType) = type == MediaType.WEB_NOVEL || type == MediaType.LIGHT_NOVEL

    override suspend fun search(query: String, type: MediaType?, page: Int): List<MediaMetadata> {
        if (query.isBlank() || (type != null && !supports(type)) || page > 1) return emptyList()
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("action", "wbsearchentities")
            .addQueryParameter("search", query.trim())
            .addQueryParameter("language", "en").addQueryParameter("uselang", "en")
            .addQueryParameter("type", "item").addQueryParameter("limit", "12")
            .addQueryParameter("format", "json").build().toString()
        val hits = lenientJson.parseToJsonElement(http.get(url))["search"].arr().orEmpty().mapNotNull { it.obj() }
            .filter { it["description"].str()?.contains("novel", ignoreCase = true) == true }
        if (hits.isEmpty()) return emptyList()
        val ids = hits.mapNotNull { it["id"].str() }
        return fetchEntities(ids).filter { type == null || it.mediaType == type }
    }

    override suspend fun detail(externalId: String): MediaMetadata? = fetchEntities(listOf(externalId)).firstOrNull()

    private suspend fun fetchEntities(ids: List<String>): List<MediaMetadata> {
        val root = lenientJson.parseToJsonElement(http.get(entityUrl(ids, "labels|descriptions|claims|aliases")))
        val entities = root["entities"].obj() ?: return emptyList()
        val authorIds = entities.values.flatMap { claimIds(it, "P50") }.distinct()
        val authorNames = if (authorIds.isEmpty()) emptyMap() else {
            val r = lenientJson.parseToJsonElement(http.get(entityUrl(authorIds, "labels")))
            r["entities"].obj().orEmpty().mapValues { (_, v) -> v["labels"]["en"]["value"].str() }
        }
        return ids.mapNotNull { id -> entities[id].obj()?.let { parse(id, it, authorNames) } }
    }

    private fun entityUrl(ids: List<String>, props: String) = apiUrl.toHttpUrl().newBuilder()
        .addQueryParameter("action", "wbgetentities")
        .addQueryParameter("ids", ids.joinToString("|"))
        .addQueryParameter("props", props)
        .addQueryParameter("languages", "en")
        .addQueryParameter("format", "json").build().toString()

    private fun claimIds(entity: JsonElement?, property: String): List<String> =
        entity["claims"][property].arr().orEmpty().mapNotNull { it["mainsnak"]["datavalue"]["value"]["id"].str() }

    internal fun parse(qid: String, e: JsonObject, authorNames: Map<String, String?>): MediaMetadata? {
        val label = e["labels"]["en"]["value"].str() ?: return null
        val description = e["descriptions"]["en"]["value"].str()
        val type = if (description?.contains("light novel", true) == true) MediaType.LIGHT_NOVEL else MediaType.WEB_NOVEL
        val aliases = e["aliases"]["en"].arr().orEmpty().mapNotNull { it["value"].str() }

        val native = e["claims"]["P1476"].arr().orEmpty().firstNotNullOfOrNull { it["mainsnak"]["datavalue"]["value"]["text"].str() }
            ?: e["claims"]["P11196"].arr().orEmpty().firstNotNullOfOrNull { it["mainsnak"]["datavalue"]["value"].str() }

        val authors = claimIds(e, "P50").mapNotNull { authorNames[it] }.map { Credit(CreditRole.AUTHOR, it) }

        // Publication date keeps the precision Wikidata has (year, month or day); nothing is invented.
        val time = e["claims"]["P577"].arr().orEmpty().firstOrNull()?.get("mainsnak")["datavalue"]["value"]
        val precision = time["precision"].int() ?: 0
        val timeStr = time["time"].str()
        val year = timeStr?.drop(1)?.take(4)?.toIntOrNull()?.takeIf { it > 0 && precision >= 9 }
        val month = timeStr?.drop(6)?.take(2)?.toIntOrNull()?.takeIf { it in 1..12 && precision >= 10 }
        val day = timeStr?.drop(9)?.take(2)?.toIntOrNull()?.takeIf { it in 1..31 && precision >= 11 }
        val date = when {
            year == null -> null
            month == null -> "%04d".format(Locale.ROOT, year)
            day == null -> "%04d-%02d".format(Locale.ROOT, year, month)
            else -> "%04d-%02d-%02d".format(Locale.ROOT, year, month, day)
        }

        val image = e["claims"]["P18"].arr().orEmpty().firstNotNullOfOrNull { it["mainsnak"]["datavalue"]["value"].str() }
        val cover = image?.let {
            "https://commons.wikimedia.org/wiki/Special:FilePath/" +
                java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") + "?width=400"
        }

        return MediaMetadata(
            provider = Providers.WIKIDATA,
            externalId = qid,
            mediaType = type,
            title = label,
            format = "NOVEL",
            titleNative = native?.takeIf { it != label },
            alternativeTitles = aliases.filter { it != label }.distinct(),
            synopsis = description?.replaceFirstChar { it.uppercase() }?.let { "$it." },
            coverUrl = cover,
            pubStatus = PublicationStatus.UNKNOWN,
            startDate = date,
            startYear = year,
            credits = authors,
            sourceUrl = "https://www.wikidata.org/wiki/$qid"
        )
    }
}
