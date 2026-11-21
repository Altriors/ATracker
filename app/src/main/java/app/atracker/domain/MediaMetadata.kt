package app.atracker.domain

/**
 * Provider-agnostic metadata. Every provider maps its own response into this class.
 * Null means "the provider does not know"; values are never invented.
 */
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
    val credits: List<Pair<CreditRole, String>> = emptyList(),
    val sourceUrl: String? = null,
    /** Provider-reported relations (adaptations, sequels). Shown as suggestions, never auto-linked. */
    val relations: List<ProviderRelation> = emptyList()
)

data class ProviderRelation(
    val provider: String,
    val externalId: String,
    val mediaType: MediaType?,
    val title: String,
    val relation: FranchiseRelation
)
