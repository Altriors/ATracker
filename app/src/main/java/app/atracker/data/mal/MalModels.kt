package app.atracker.data.mal

import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType

enum class MalKind(val key: String) { ANIME("anime"), MANGA("manga") }

/** What to do when the title is already in the library and MAL's values differ from yours. */
enum class ConflictPolicy(val label: String, val help: String) {
    KEEP_MINE("Keep my record", "Nothing you already track is changed."),
    FILL_EMPTY("Fill empty fields only", "MAL data is added only where your record has nothing."),
    USE_MAL("Use MAL's values", "Overwrites your status, progress, score and dates for the differing titles. Every change is written to the title's history.")
}

/** One entry of a MyAnimeList list, exactly as MAL reported it. Missing values stay null. */
data class MalRecord(
    val kind: MalKind,
    val malId: Int,
    val title: String,
    val status: LibraryStatus,
    val progress: Int,
    val volumes: Int,
    val total: Int?,
    val totalVolumes: Int?,
    /** 1..10; null when unrated (MAL stores 0). */
    val score: Int?,
    val startDate: String?,
    val finishDate: String?,
    /** Completed rewatches / rereads. */
    val repeatCount: Int,
    val tags: List<String>,
    val comments: String?,
    /** Type hint from the API (manga, novel, manhwa, manhua, light_novel ...). Null for XML exports. */
    val mediaTypeHint: MediaType?
) {
    val externalId: String get() = "${kind.key}:$malId"
}

enum class PlanAction(val label: String) {
    ADD("New: will be added"),
    UPDATE_EXISTING("Already yours: values differ"),
    SAME("Already yours: identical"),
    MAL_ONLY("No online match: MAL data only"),
    POSSIBLE_DUPLICATE("Possible duplicate: same title already here")
}

/**
 * One row of the import preview. [skip], [policy] and [linkToMediaId] are the user's decisions; nothing
 * is written until the plan is committed.
 */
data class PlanItem(
    val record: MalRecord,
    val action: PlanAction,
    val meta: MediaMetadata?,
    /** Media row this MAL entry maps to, when one is already in the app. */
    val existingMediaId: Long?,
    /** Human readable list of what differs from the existing library entry. */
    val differences: List<String> = emptyList(),
    val skip: Boolean = false,
    val policy: ConflictPolicy? = null,
    /** For POSSIBLE_DUPLICATE: attach this MAL entry to the existing title instead of adding a new one. */
    val linkToMediaId: Long? = null
)

data class ImportPlan(val items: List<PlanItem>, val note: String?) {
    fun count(a: PlanAction) = items.count { it.action == a }
}

enum class Outcome { ADDED, UPDATED, SKIPPED, UNMATCHED, FAILED }

data class ImportReport(
    val runId: Long, val added: Int, val updated: Int, val skipped: Int, val unmatched: Int, val failed: Int
)
