package app.atracker.data.provider

import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * A source of catalogue metadata. Add new providers by implementing this interface and
 * registering them in [ProviderRegistry]; nothing else in the app needs to change.
 */
interface MetadataProvider {
    val id: String
    val displayName: String

    /** Whether this provider can reasonably contain titles of the given type. */
    fun supports(type: MediaType): Boolean

    /** Text search. [type] null means "any type this provider supports". */
    suspend fun search(query: String, type: MediaType?, page: Int = 1): List<MediaMetadata>

    /** Full details for one title, or null when the provider no longer has it. */
    suspend fun detail(externalId: String): MediaMetadata?

    /** Browse lists for the Discover screen. Providers without browse support return empty. */
    suspend fun browse(kind: BrowseKind, type: MediaType, page: Int = 1): List<MediaMetadata> = emptyList()
}

enum class BrowseKind { POPULAR, RECENT }

internal val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

// Tolerant JSON navigation: provider data is untrusted, so every accessor returns null on mismatch.
internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray
internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
internal fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

internal fun cleanText(raw: String?): String? {
    if (raw == null) return null
    return raw
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
        .lines().joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
        .ifBlank { null }
}
