package app.atracker.data.provider

import app.atracker.domain.Credit
import app.atracker.domain.CreditRole
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.Providers
import app.atracker.domain.PublicationStatus
import app.atracker.domain.SourceRef
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * MangaDex public API. Covers manga, manhwa and manhua (not anime or novels).
 * Totals: MangaDex's "lastChapter" is only trusted when the series is finished or cancelled;
 * for ongoing series it is the last chapter that happens to be hosted, not a real total.
 */
class MangaDexProvider(
    private val http: ApiHttp,
    private val baseUrl: String = "https://api.mangadex.org",
    private val coverBaseUrl: String = "https://uploads.mangadex.org"
) : MetadataProvider {
    override val id = Providers.MANGADEX
    override val displayName = "MangaDex"

    override fun supports(type: MediaType) =
        type == MediaType.MANGA || type == MediaType.MANHWA || type == MediaType.MANHUA

    private fun languagesFor(type: MediaType?): List<String> = when (type) {
        MediaType.MANGA -> listOf("ja")
        MediaType.MANHWA -> listOf("ko")
        MediaType.MANHUA -> listOf("zh", "zh-hk", "zh-ro")
        else -> emptyList()
    }

    override suspend fun search(query: String, type: MediaType?, page: Int): List<MediaMetadata> {
        if (query.isBlank() || (type != null && !supports(type))) return emptyList()
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("manga")
            .addQueryParameter("title", query.trim())
            .addQueryParameter("limit", "20")
            .addQueryParameter("offset", ((page - 1) * 20).toString())
            .addQueryParameter("order[relevance]", "desc")
            .also { addCommon(it) }
        languagesFor(type).forEach { url.addQueryParameter("originalLanguage[]", it) }
        return parseList(http.get(url.build().toString()))
    }

    override suspend fun detail(externalId: String): MediaMetadata? {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("manga").addPathSegment(externalId)
            .also { b -> INCLUDES.forEach { b.addQueryParameter("includes[]", it) } }
        val root = lenientJson.parseToJsonElement(http.get(url.build().toString()))
        return root["data"].obj()?.let(::parse)
    }

    override suspend fun browse(kind: BrowseKind, type: MediaType, page: Int): List<MediaMetadata> {
        if (!supports(type)) return emptyList()
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("manga")
            .addQueryParameter("limit", "20")
            .addQueryParameter("offset", ((page - 1) * 20).toString())
            .addQueryParameter(if (kind == BrowseKind.POPULAR) "order[followedCount]" else "order[createdAt]", "desc")
            .also { addCommon(it) }
        languagesFor(type).forEach { url.addQueryParameter("originalLanguage[]", it) }
        return parseList(http.get(url.build().toString()))
    }

    private fun addCommon(b: okhttp3.HttpUrl.Builder) {
        INCLUDES.forEach { b.addQueryParameter("includes[]", it) }
        // Erotica and pornographic titles are excluded by default.
        listOf("safe", "suggestive").forEach { b.addQueryParameter("contentRating[]", it) }
    }

    private fun parseList(body: String): List<MediaMetadata> =
        lenientJson.parseToJsonElement(body)["data"].arr().orEmpty().mapNotNull { it.obj()?.let(::parse) }

    internal fun parse(m: JsonObject): MediaMetadata? {
        val mangaId = m["id"].str() ?: return null
        val attrs = m["attributes"].obj() ?: return null
        val titles = attrs["title"].obj()
        val alts = attrs["altTitles"].arr().orEmpty().mapNotNull { it.obj() }
        val title = titles?.get("en").str()
            ?: titles?.values?.firstNotNullOfOrNull { it.str() }
            ?: alts.firstNotNullOfOrNull { a -> a.values.firstNotNullOfOrNull { it.str() } }
            ?: return null
        val originalLanguage = attrs["originalLanguage"].str()
        val type = when (originalLanguage) {
            "ko" -> MediaType.MANHWA
            "zh", "zh-hk", "zh-ro" -> MediaType.MANHUA
            else -> MediaType.MANGA
        }
        val native = alts.firstNotNullOfOrNull { it[originalLanguage ?: "-"].str() }
        val altTitles = (alts.flatMap { a -> a.values.mapNotNull { it.str() } } + listOfNotNull(titles?.values?.drop(1)?.firstNotNullOfOrNull { it.str() }))
            .filter { it != title }.distinct()

        val status = when (attrs["status"].str()) {
            "ongoing" -> PublicationStatus.ONGOING
            "completed" -> PublicationStatus.COMPLETED
            "hiatus" -> PublicationStatus.HIATUS
            "cancelled" -> PublicationStatus.CANCELLED
            else -> PublicationStatus.UNKNOWN
        }
        val trustTotals = status == PublicationStatus.COMPLETED || status == PublicationStatus.CANCELLED
        fun numeric(s: String?): Int? = s?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }

        val tags = attrs["tags"].arr().orEmpty().mapNotNull { it.obj() }
        fun tagNames(group: String) = tags.filter { it["attributes"]["group"].str() == group }
            .mapNotNull { it["attributes"]["name"]["en"].str() }

        val rels = m["relationships"].arr().orEmpty().mapNotNull { it.obj() }
        val credits = rels.mapNotNull { r ->
            val name = r["attributes"]["name"].str() ?: return@mapNotNull null
            when (r["type"].str()) {
                "author" -> Credit(CreditRole.AUTHOR, name)
                "artist" -> Credit(CreditRole.ARTIST, name)
                else -> null
            }
        }.distinct()
        val coverFile = rels.firstOrNull { it["type"].str() == "cover_art" }?.get("attributes")["fileName"].str()

        val year = attrs["year"].int()
        val anilistId = attrs["links"]["al"].str()

        return MediaMetadata(
            provider = Providers.MANGADEX,
            externalId = mangaId,
            mediaType = type,
            title = title,
            format = "MANGA",
            titleNative = native,
            alternativeTitles = altTitles,
            synopsis = cleanText(attrs["description"]["en"].str()),
            coverUrl = coverFile?.let { "$coverBaseUrl/covers/$mangaId/$it.512.jpg" },
            pubStatus = status,
            totalChapters = if (trustTotals) numeric(attrs["lastChapter"].str()) else null,
            totalVolumes = if (trustTotals) numeric(attrs["lastVolume"].str()) else null,
            startDate = year?.toString(),
            startYear = year,
            countryOfOrigin = when (originalLanguage) { "ko" -> "KR"; "ja" -> "JP"; "zh", "zh-hk", "zh-ro" -> "CN"; else -> null },
            genres = tagNames("genre"),
            tags = tagNames("theme"),
            credits = credits,
            sourceUrl = "https://mangadex.org/title/$mangaId",
            crossRefs = listOfNotNull(anilistId?.let { SourceRef(Providers.ANILIST, it) })
        )
    }

    private companion object {
        val INCLUDES = listOf("cover_art", "author", "artist")
    }
}
