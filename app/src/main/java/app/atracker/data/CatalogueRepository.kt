package app.atracker.data

import app.atracker.data.db.AppDatabase
import app.atracker.data.db.CatalogueCacheEntity
import app.atracker.data.db.MediaEntity
import app.atracker.data.provider.AniListProvider
import app.atracker.data.provider.ApiHttp
import app.atracker.data.provider.BrowseKind
import app.atracker.data.provider.MangaDexProvider
import app.atracker.data.provider.MetadataProvider
import app.atracker.data.provider.RateLimiter
import app.atracker.data.provider.WikidataProvider
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.Providers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class SearchOutcome(
    val local: List<MediaEntity>,
    val remote: List<MediaMetadata>,
    /** provider id -> short message, for providers that failed and had nothing cached. */
    val errors: Map<String, String>,
    /** providers whose results come from an older cache because the request failed. */
    val staleProviders: Set<String>
)

data class RefreshReport(val updated: Int, val failed: List<String>)

/**
 * Catalogue access: local database first, then online providers (in parallel, each failure
 * isolated). Raw provider results are cached so searches and browse lists keep working offline.
 * Nothing found online is added to the personal library; that only happens on explicit request.
 */
class CatalogueRepository(
    private val db: AppDatabase,
    private val media: MediaRepository,
    val providers: List<MetadataProvider>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val searchTtlMs: Long = 6 * 3_600_000L,
    private val browseTtlMs: Long = 3 * 3_600_000L
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(MediaMetadata.serializer())

    private class Cached(val items: List<MediaMetadata>, val stale: Boolean)

    suspend fun search(query: String, type: MediaType?, page: Int = 1): SearchOutcome = coroutineScope {
        val q = query.trim()
        val local = if (page == 1 && q.isNotEmpty()) db.mediaDao().searchLocal(q, type?.name) else emptyList()
        val active = providers.filter { type == null || it.supports(type) }
        val results = active.map { p ->
            async {
                p.id to attempt { cached("search", p, type, q, page, searchTtlMs) { p.search(q, type, page) } }
            }
        }.awaitAll()
        collect(local, results)
    }

    suspend fun browse(kind: BrowseKind, type: MediaType, page: Int = 1): SearchOutcome = coroutineScope {
        val active = providers.filter { it.supports(type) }
        val results = active.map { p ->
            async { p.id to attempt { cached("browse-$kind", p, type, "", page, browseTtlMs) { p.browse(kind, type, page) } } }
        }.awaitAll()
        collect(emptyList(), results)
    }

    private fun collect(local: List<MediaEntity>, results: List<Pair<String, Result<Cached>>>): SearchOutcome {
        val remote = ArrayList<MediaMetadata>()
        val errors = LinkedHashMap<String, String>()
        val stale = LinkedHashSet<String>()
        results.forEach { (id, r) ->
            r.onSuccess { c -> remote += c.items; if (c.stale) stale += id }
                .onFailure { errors[id] = it.message ?: "Request failed" }
        }
        // Same (provider, id) can only appear once; titles from different providers stay separate.
        return SearchOutcome(local, remote.distinctBy { it.provider to it.externalId }, errors, stale)
    }

    /** Saves a discovered title into the local catalogue (NOT the library) and returns its id. */
    suspend fun open(meta: MediaMetadata): Long = media.saveMetadata(meta)

    /**
     * Re-fetches every provider-backed source of one title. Personal data is never touched, and a
     * title that a provider no longer returns simply keeps its stored metadata.
     */
    suspend fun refresh(mediaId: Long): RefreshReport {
        val sources = db.mediaDao().sourcesFor(mediaId).filter { it.provider != Providers.MANUAL }
        var updated = 0
        val failed = ArrayList<String>()
        for (s in sources.sortedBy { it.id }) {
            val provider = providers.firstOrNull { it.id == s.provider } ?: continue
            try {
                val fresh = provider.detail(s.externalId)
                if (fresh != null) { media.saveMetadata(fresh); updated++ }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += "${provider.displayName}: ${e.message ?: "failed"}"
            }
        }
        return RefreshReport(updated, failed)
    }

    private suspend fun attempt(block: suspend () -> Cached): Result<Cached> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun cached(
        kind: String, p: MetadataProvider, type: MediaType?, q: String, page: Int, ttl: Long,
        loader: suspend () -> List<MediaMetadata>
    ): Cached {
        val key = "$kind|${p.id}|${type?.name}|${q.lowercase()}|$page"
        val now = clock()
        val dao = db.cacheDao()
        val entry = dao.get(key)
        if (entry != null && entry.expiresAt > now) decode(entry)?.let { return Cached(it, false) }
        return try {
            val items = loader()
            dao.put(CatalogueCacheEntity(key, json.encodeToString(listSerializer, items), now, now + ttl))
            Cached(items, false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val old = entry?.let { decode(it) } ?: throw e
            Cached(old, true)
        }
    }

    private fun decode(entry: CatalogueCacheEntity): List<MediaMetadata>? =
        runCatching { json.decodeFromString(listSerializer, entry.payload) }.getOrNull()

    companion object {
        /** Real providers with rate limits that match each service's published limits. */
        fun defaultProviders(): List<MetadataProvider> = listOf(
            AniListProvider(ApiHttp(RateLimiter(2_200))),
            MangaDexProvider(ApiHttp(RateLimiter(300))),
            WikidataProvider(ApiHttp(RateLimiter(250)))
        )
    }
}
