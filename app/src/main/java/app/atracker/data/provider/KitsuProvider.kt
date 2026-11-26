package app.atracker.data.provider

import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.Providers
import app.atracker.domain.PublicationStatus
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Kitsu public API (no key). Used for novels (it lists Chinese/Korean/Japanese web and light
 * novels with chapter counts), and as a second source for manga, manhwa and manhua.
 * Anime is left to AniList. Kitsu has one "novel" subtype and no country field, so the
 * light-novel / web-novel split uses the original-language title keys Kitsu returns:
 * ja_jp means light novel; ko_kr or zh_cn means web novel; otherwise the category requested.
 */
class KitsuProvider(
    private val http: ApiHttp,
    private val baseUrl: String = "https://kitsu.io/api/edge"
) : MetadataProvider {
    override val id = Providers.KITSU
    override val displayName = "Kitsu"

    override fun supports(type: MediaType) = type != MediaType.ANIME

    private fun subtypeFor(type: MediaType?): String? = when (type) {
        MediaType.MANGA -> "manga"
        MediaType.MANHWA -> "manhwa"
        MediaType.MANHUA -> "manhua"
        MediaType.LIGHT_NOVEL, MediaType.WEB_NOVEL -> "novel"
        else -> null
    }

    override suspend fun search(query: String, type: MediaType?, page: Int): List<MediaMetadata> {
        if (query.isBlank() || (type != null && !supports(type))) return emptyList()
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("manga")
            .addQueryParameter("filter[text]", query.trim())
            .addQueryParameter("page[limit]", "20")
            .addQueryParameter("page[offset]", ((page - 1) * 20).toString())
            .also { b -> subtypeFor(type)?.let { b.addQueryParameter("filter[subtype]", it) } }
            .build().toString()
        return parseList(http.get(url), type)
    }

    override suspend fun detail(externalId: String): MediaMetadata? {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("manga").addPathSegment(externalId).build().toString()
        val data = lenientJson.parseToJsonElement(http.get(url))["data"].obj() ?: return null
        return parse(data, null)
    }

    override suspend fun browse(kind: BrowseKind, type: MediaType, page: Int): List<MediaMetadata> {
        if (!supports(type)) return emptyList()
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("manga")
            .addQueryParameter("page[limit]", "20")
            .addQueryParameter("page[offset]", ((page - 1) * 20).toString())
            .addQueryParameter("sort", if (kind == BrowseKind.POPULAR) "popularityRank" else "-startDate")
            .also { b -> subtypeFor(type)?.let { b.addQueryParameter("filter[subtype]", it) } }
            .build().toString()
        return parseList(http.get(url), type)
    }

    private fun parseList(body: String, requested: MediaType?): List<MediaMetadata> {
        val data = lenientJson.parseToJsonElement(body)["data"].arr()
            ?: throw ProviderException("Kitsu returned no result list")
        return data.mapNotNull { it.obj()?.let { o -> parse(o, requested) } }
            .filter { requested == null || it.mediaType == requested }
    }

    internal fun parse(m: JsonObject, requested: MediaType?): MediaMetadata? {
        val kitsuId = m["id"].str() ?: return null
        val a = m["attributes"].obj() ?: return null
        val titles = a["titles"].obj()
        val title = titles?.get("en").str() ?: a["canonicalTitle"].str() ?: titles?.get("en_jp").str() ?: return null
        val subtype = a["subtype"].str()
        val type = when (subtype) {
            "manhwa" -> MediaType.MANHWA
            "manhua" -> MediaType.MANHUA
            "novel" -> when {
                titles?.get("ja_jp").str() != null -> MediaType.LIGHT_NOVEL
                titles?.get("ko_kr").str() != null || titles?.get("zh_cn").str() != null -> MediaType.WEB_NOVEL
                requested == MediaType.LIGHT_NOVEL -> MediaType.LIGHT_NOVEL
                else -> MediaType.WEB_NOVEL
            }
            "manga", "oneshot" -> MediaType.MANGA
            // doujin, oel and anything unknown are not tracked categories
            else -> return null
        }
        val status = when (a["status"].str()) {
            "current" -> PublicationStatus.ONGOING
            "finished" -> PublicationStatus.COMPLETED
            "upcoming", "unreleased", "tba" -> PublicationStatus.UPCOMING
            else -> PublicationStatus.UNKNOWN
        }
        val start = a["startDate"].str()
        val native = titles?.get("ja_jp").str() ?: titles?.get("ko_kr").str() ?: titles?.get("zh_cn").str()
        val alt = (listOfNotNull(titles?.get("en_jp").str(), a["canonicalTitle"].str(), native) +
            a["abbreviatedTitles"].arr().orEmpty().mapNotNull { it.str() }).filter { it != title }.distinct()
        val rating = a["averageRating"].str()?.toDoubleOrNull()?.toInt()
        // A zero count means "unknown" on Kitsu, never "no chapters".
        return MediaMetadata(
            provider = Providers.KITSU,
            externalId = kitsuId,
            mediaType = type,
            title = title,
            format = subtype?.uppercase(),
            titleNative = native,
            alternativeTitles = alt,
            synopsis = cleanText(a["synopsis"].str()),
            coverUrl = a["posterImage"]["large"].str() ?: a["posterImage"]["original"].str(),
            pubStatus = status,
            totalChapters = a["chapterCount"].int()?.takeIf { it > 0 },
            totalVolumes = a["volumeCount"].int()?.takeIf { it > 0 },
            startDate = start,
            endDate = a["endDate"].str(),
            startYear = start?.take(4)?.toIntOrNull(),
            averageScore = rating,
            sourceUrl = "https://kitsu.io/manga/$kitsuId"
        )
    }
}
