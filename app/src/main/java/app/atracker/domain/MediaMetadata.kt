package app.atracker.domain

import kotlinx.serialization.Serializable

/**
 * Provider-agnostic metadata. Every provider maps its own response into this class.
 * Null means "the provider does not know"; values are never invented.
 */
@Serializable
data class MediaMetadata(
    val provider: String,
    val externalId: String,
    val mediaType: MediaType,
    val title: String,
    val format: String? = null,
    val titleNative: String? = null,
    val alternativeTitles: List<String> = emptyList(),
    val synopsis: String? = null,
    val coverUrl: String? = null,
    val pubStatus: PublicationStatus = PublicationStatus.UNKNOWN,
    val totalEpisodes: Int? = null,
    val totalChapters: Int? = null,
    val totalVolumes: Int? = null,
    val episodeDurationMin: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val startYear: Int? = null,
    val averageScore: Int? = null,
    val countryOfOrigin: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val credits: List<Credit> = emptyList(),
    val sourceUrl: String? = null,
    /** Provider-reported relations (adaptations, sequels). Shown as suggestions, never auto-linked. */
    val relations: List<ProviderRelation> = emptyList(),
    /**
     * Hard identifiers of the SAME title at other providers (e.g. MangaDex lists the AniList id).
     * Used to attach sources to one entry; names are never used to merge titles.
     */
    val crossRefs: List<SourceRef> = emptyList(),
    /** Dated release information the provider reports (next episode, premiere date). */
    val releases: List<ReleaseEvent> = emptyList()
)

@Serializable
data class Credit(val role: CreditRole, val name: String)

@Serializable
data class SourceRef(val provider: String, val externalId: String)

@Serializable
data class ReleaseEvent(
    /** EPISODE, CHAPTER, VOLUME or PREMIERE */
    val kind: String,
    val number: Int? = null,
    val atMillis: Long
)

@Serializable
data class ProviderRelation(
    val provider: String,
    val externalId: String,
    val mediaType: MediaType?,
    val title: String,
    val relation: FranchiseRelation
)
