package app.atracker.data.provider

import app.atracker.domain.Credit
import app.atracker.domain.CreditRole
import app.atracker.domain.FranchiseRelation
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.ProviderRelation
import app.atracker.domain.Providers
import app.atracker.domain.PublicationStatus
import app.atracker.domain.ReleaseEvent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * AniList GraphQL API. Covers anime, manga, manhwa, manhua and (Japanese) light novels.
 * It does NOT contain Chinese/Korean web novels; those come from Wikidata or manual entry.
 * Rate limit is 30 requests/minute, so calls are spaced ~2.2s apart.
 * Adult-rated titles are excluded from search and browse.
 */
class AniListProvider(
    private val http: ApiHttp,
    private val endpoint: String = "https://graphql.anilist.co"
) : MetadataProvider {
    override val id = Providers.ANILIST
    override val displayName = "AniList"

    override fun supports(type: MediaType) = type != MediaType.WEB_NOVEL

    private class Filter(val type: String?, val country: String?, val format: String?, val notFormat: String?)

    private fun filterFor(type: MediaType?): Filter = when (type) {
        null -> Filter(null, null, null, null)
        MediaType.ANIME -> Filter("ANIME", null, null, null)
        MediaType.MANGA -> Filter("MANGA", "JP", null, "NOVEL")
        MediaType.MANHWA -> Filter("MANGA", "KR", null, "NOVEL")
        MediaType.MANHUA -> Filter("MANGA", "CN", null, "NOVEL")
        MediaType.LIGHT_NOVEL -> Filter("MANGA", "JP", "NOVEL", null)
        MediaType.WEB_NOVEL -> Filter("MANGA", null, "NOVEL", null)
    }

    override suspend fun search(query: String, type: MediaType?, page: Int): List<MediaMetadata> {
        if (query.isBlank() || (type != null && !supports(type))) return emptyList()
        val f = filterFor(type)
        val vars = buildJsonObject {
            put("q", query.trim()); put("page", page)
            put("type", f.type); put("country", f.country); put("format", f.format); put("notFormat", f.notFormat)
        }
        return runPage(SEARCH_QUERY, vars)
    }

    override suspend fun browse(kind: BrowseKind, type: MediaType, page: Int): List<MediaMetadata> {
        if (!supports(type)) return emptyList()
        val f = filterFor(type)
        val vars = buildJsonObject {
            put("page", page)
            put("type", f.type); put("country", f.country); put("format", f.format); put("notFormat", f.notFormat)
            put("sort", buildJsonArray {
                add(JsonPrimitive(if (kind == BrowseKind.POPULAR) "POPULARITY_DESC" else "START_DATE_DESC"))
            })
            put("statusNot", if (kind == BrowseKind.RECENT) "NOT_YET_RELEASED" else null)
        }
        return runPage(BROWSE_QUERY, vars)
    }

    override suspend fun detail(externalId: String): MediaMetadata? {
        val idInt = externalId.toIntOrNull() ?: return null
        val vars = buildJsonObject { put("id", idInt) }
        val root = post(DETAIL_QUERY, vars)
        return root["data"]["Media"].obj()?.let { parse(it) }
    }

    private suspend fun runPage(query: String, vars: JsonObject): List<MediaMetadata> {
        val root = post(query, vars)
        val media = root["data"]["Page"]["media"].arr()
        if (media == null) {
            // A reply without a result list is a failure, never "no results" (it must not be cached as empty).
            val msg = root["errors"].arr()?.firstOrNull()?.let { it["message"].str() }
            throw ProviderException("AniList returned no result list" + (msg?.let { ": $it" } ?: ""))
        }
        return media.mapNotNull { it.obj()?.let(::parse) }
    }

    private suspend fun post(query: String, vars: JsonObject): JsonElement {
        val body = buildJsonObject { put("query", query); put("variables", vars) }.toString()
        val root = lenientJson.parseToJsonElement(http.postJson(endpoint, body))
        val errors = root["errors"].arr()
        if (!errors.isNullOrEmpty() && root["data"] is JsonNull?) {
            throw ProviderException("AniList error: ${errors.first()["message"].str() ?: "unknown"}")
        }
        return root
    }

    internal fun parse(m: JsonObject): MediaMetadata? {
        val idValue = m["id"].int() ?: return null
        val country = m["countryOfOrigin"].str()
        val type = mapType(m["type"].str(), m["format"].str(), country)
        val titles = m["title"]
        val romaji = titles["romaji"].str()
        val english = titles["english"].str()
        val native = titles["native"].str()?.trim('​', ' ')
        val title = english ?: romaji ?: native ?: return null
        val alt = (listOfNotNull(romaji, native) + m["synonyms"].arr().orEmpty().mapNotNull { it.str() })
            .filter { it != title }.distinct()

        val credits = buildList {
            m["studios"]["nodes"].arr().orEmpty().forEach { n -> n["name"].str()?.let { add(Credit(CreditRole.STUDIO, it)) } }
            m["staff"]["edges"].arr().orEmpty().forEach { e ->
                val role = e["role"].str().orEmpty()
                val name = e["node"]["name"]["full"].str() ?: return@forEach
                if (role.contains("Story", true) || role.contains("Original", true) && !role.contains("Design", true)) {
                    add(Credit(CreditRole.AUTHOR, name))
                }
                if (role.startsWith("Art", true) || role.contains("& Art", true)) add(Credit(CreditRole.ARTIST, name))
            }
        }.distinct()

        val relations = m["relations"]["edges"].arr().orEmpty().mapNotNull { e ->
            val node = e["node"].obj() ?: return@mapNotNull null
            val relId = node["id"].int() ?: return@mapNotNull null
            val relTitle = node["title"]["english"].str() ?: node["title"]["romaji"].str() ?: return@mapNotNull null
            ProviderRelation(
                provider = Providers.ANILIST, externalId = relId.toString(),
                mediaType = mapType(node["type"].str(), node["format"].str(), node["countryOfOrigin"].str()),
                title = relTitle, relation = mapRelation(e["relationType"].str())
            )
        }

        val status = mapStatus(m["status"].str())
        val releases = buildList {
            val next = m["nextAiringEpisode"].obj()
            val ep = next?.get("episode").int()
            val at = (next?.get("airingAt") as? JsonPrimitive)?.content?.toLongOrNull()
            if (ep != null && at != null) add(ReleaseEvent("EPISODE", ep, at * 1000))
            if (status == PublicationStatus.UPCOMING) {
                val d = m["startDate"]
                val y = d["year"].int(); val mo = d["month"].int(); val day = d["day"].int()
                if (y != null && mo != null && day != null) {
                    runCatching {
                        add(ReleaseEvent("PREMIERE", null, LocalDate.of(y, mo, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))
                    }
                }
            }
        }

        val cover = m["coverImage"]
        return MediaMetadata(
            provider = Providers.ANILIST,
            externalId = idValue.toString(),
            mediaType = type,
            title = title,
            format = m["format"].str(),
            titleNative = native,
            alternativeTitles = alt,
            synopsis = cleanText(m["description"].str()),
            coverUrl = cover["extraLarge"].str() ?: cover["large"].str(),
            pubStatus = status,
            totalEpisodes = m["episodes"].int(),
            totalChapters = m["chapters"].int(),
            totalVolumes = m["volumes"].int(),
            episodeDurationMin = m["duration"].int(),
            startDate = formatDate(m["startDate"]),
            endDate = formatDate(m["endDate"]),
            startYear = m["startDate"]["year"].int(),
            averageScore = m["averageScore"].int(),
            countryOfOrigin = country,
            genres = m["genres"].arr().orEmpty().mapNotNull { it.str() },
            credits = credits,
            sourceUrl = m["siteUrl"].str(),
            relations = relations,
            releases = releases
        )
    }

    internal companion object {
        /** %name is replaced with $name so GraphQL variables do not clash with Kotlin templates. */
        private fun gql(s: String) = s.replace('%', '$')

        private val FRAGMENT = """
            fragment M on Media {
              id idMal type format status countryOfOrigin chapters volumes episodes duration
              description(asHtml: false) genres synonyms averageScore siteUrl isAdult
              coverImage { large extraLarge }
              startDate { year month day } endDate { year month day }
              title { romaji english native }
              studios(isMain: true) { nodes { name } }
              staff(perPage: 6, sort: RELEVANCE) { edges { role node { name { full } } } }
              relations { edges { relationType node { id type format countryOfOrigin title { romaji english } } } }
              nextAiringEpisode { episode airingAt }
            }
        """.trimIndent()

        val SEARCH_QUERY = gql(
            FRAGMENT + """
            query(%q: String, %type: MediaType, %country: CountryCode, %format: MediaFormat, %notFormat: MediaFormat, %page: Int) {
              Page(page: %page, perPage: 20) {
                media(search: %q, type: %type, countryOfOrigin: %country, format: %format, format_not: %notFormat, isAdult: false, sort: SEARCH_MATCH) { ...M }
              }
            }"""
        )

        val BROWSE_QUERY = gql(
            FRAGMENT + """
            query(%type: MediaType, %country: CountryCode, %format: MediaFormat, %notFormat: MediaFormat, %statusNot: MediaStatus, %sort: [MediaSort], %page: Int) {
              Page(page: %page, perPage: 20) {
                media(type: %type, countryOfOrigin: %country, format: %format, format_not: %notFormat, status_not: %statusNot, isAdult: false, sort: %sort) { ...M }
              }
            }"""
        )

        val DETAIL_QUERY = gql(FRAGMENT + "\nquery(%id: Int) { Media(id: %id) { ...M } }")

        fun mapType(type: String?, format: String?, country: String?): MediaType = when {
            type == "ANIME" -> MediaType.ANIME
            format == "NOVEL" -> if (country == "JP" || country == null) MediaType.LIGHT_NOVEL else MediaType.WEB_NOVEL
            country == "KR" -> MediaType.MANHWA
            country == "CN" || country == "TW" || country == "HK" -> MediaType.MANHUA
            else -> MediaType.MANGA
        }

        fun mapStatus(s: String?) = when (s) {
            "FINISHED" -> PublicationStatus.COMPLETED
            "RELEASING" -> PublicationStatus.ONGOING
            "NOT_YET_RELEASED" -> PublicationStatus.UPCOMING
            "CANCELLED" -> PublicationStatus.CANCELLED
            "HIATUS" -> PublicationStatus.HIATUS
            else -> PublicationStatus.UNKNOWN
        }

        fun mapRelation(s: String?) = when (s) {
            "SOURCE" -> FranchiseRelation.SOURCE
            "ADAPTATION" -> FranchiseRelation.ADAPTATION
            "SEQUEL" -> FranchiseRelation.SEQUEL
            "PREQUEL" -> FranchiseRelation.PREQUEL
            "SPIN_OFF", "SIDE_STORY" -> FranchiseRelation.SPIN_OFF
            else -> FranchiseRelation.OTHER
        }

        /** Partial dates stay partial: "2020", "2020-01" or "2020-01-21". Nothing is invented. */
        fun formatDate(d: JsonElement?): String? {
            val y = d["year"].int() ?: return null
            val m = d["month"].int() ?: return "%04d".format(Locale.ROOT, y)
            val day = d["day"].int() ?: return "%04d-%02d".format(Locale.ROOT, y, m)
            return "%04d-%02d-%02d".format(Locale.ROOT, y, m, day)
        }
    }
}
