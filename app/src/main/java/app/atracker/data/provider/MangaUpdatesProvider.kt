package app.atracker.data.provider

import app.atracker.domain.Credit
import app.atracker.domain.CreditRole
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.Providers
import app.atracker.domain.PublicationStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * MangaUpdates official API (no key). Strongest source for web novels and for manhwa/manhua.
 * Its "Novel" type does not separate light from web novels, so novels are offered as web novels.
 * Doujinshi, artbooks and adult-genre series are filtered out.
 */
class MangaUpdatesProvider(
    private val http: ApiHttp,
    private val baseUrl: String = "https://api.mangaupdates.com/v1"
) : MetadataProvider {
    override val id = Providers.MANGAUPDATES
    override val displayName = "MangaUpdates"

    override fun supports(type: MediaType) =
        type == MediaType.WEB_NOVEL || type == MediaType.MANHWA || type == MediaType.MANHUA || type == MediaType.MANGA

    override suspend fun search(query: String, type: MediaType?, page: Int): List<MediaMetadata> {
        if (query.isBlank() || (type != null && !supports(type))) return emptyList()
        val body = buildJsonObject { put("search", query.trim()); put("perpage", 25); put("page", page) }.toString()
        val root = lenientJson.parseToJsonElement(http.postJson("$baseUrl/series/search", body))
        val results = root["results"].arr() ?: throw ProviderException("MangaUpdates returned no result list")
        return results.mapNotNull { it["record"].obj()?.let(::parse) }.filter { type == null || it.mediaType == type }
    }

    override suspend fun detail(externalId: String): MediaMetadata? {
        val root = lenientJson.parseToJsonElement(http.get("$baseUrl/series/$externalId")).obj() ?: return null
        return parse(root)
    }

    internal fun parse(r: JsonObject): MediaMetadata? {
        val seriesId = (r["series_id"]?.str()) ?: return null
        val title = r["title"].str() ?: return null
        val rawType = r["type"].str()
        val type = when (rawType) {
            "Manga" -> MediaType.MANGA
            "Manhwa" -> MediaType.MANHWA
            "Manhua" -> MediaType.MANHUA
            "Novel" -> MediaType.WEB_NOVEL
            else -> return null // Doujinshi, Artbook, OEL ... are not tracked categories
        }
        val genres = r["genres"].arr().orEmpty().mapNotNull { it["genre"].str() }
        if (genres.any { it in ADULT }) return null
        val image = r["image"]["url"]
        val completed = (r["completed"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull()
        val year = r["year"].str()?.take(4)?.toIntOrNull()
        val authors = r["authors"].arr().orEmpty().mapNotNull { a ->
            val n = a["name"].str() ?: return@mapNotNull null
            when (a["type"].str()) { "Author" -> Credit(CreditRole.AUTHOR, n); "Artist" -> Credit(CreditRole.ARTIST, n); else -> null }
        }
        val assoc = r["associated"].arr().orEmpty().mapNotNull { it["title"].str() }.filter { it != title }.distinct()
        val rating = (r["bayesian_rating"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
        return MediaMetadata(
            provider = Providers.MANGAUPDATES,
            externalId = seriesId,
            mediaType = type,
            title = title,
            format = rawType?.uppercase(),
            alternativeTitles = assoc,
            synopsis = cleanText(r["description"].str()),
            coverUrl = image["original"].str() ?: image["thumb"].str(),
            pubStatus = when (completed) { true -> PublicationStatus.COMPLETED; false -> PublicationStatus.ONGOING; null -> PublicationStatus.UNKNOWN },
            startYear = year,
            startDate = year?.toString(),
            averageScore = rating?.let { (it * 10).toInt().coerceIn(0, 100) },
            genres = genres,
            credits = authors,
            sourceUrl = r["url"].str()
        )
    }

    private companion object {
        val ADULT = setOf("Adult", "Hentai", "Smut")
    }
}
