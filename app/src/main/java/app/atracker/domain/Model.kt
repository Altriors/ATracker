package app.atracker.domain

/** The six independently tracked media categories. Each has its own progress unit. */
enum class MediaType(val label: String, val progressUnit: ProgressUnit, val hasVolumes: Boolean) {
    ANIME("Anime", ProgressUnit.EPISODE, false),
    MANGA("Manga", ProgressUnit.CHAPTER, true),
    MANHWA("Manhwa", ProgressUnit.CHAPTER, false),
    MANHUA("Manhua", ProgressUnit.CHAPTER, false),
    LIGHT_NOVEL("Light Novel", ProgressUnit.VOLUME, true),
    WEB_NOVEL("Web Novel", ProgressUnit.CHAPTER, false);

    val isWatching: Boolean get() = this == ANIME

    companion object {
        fun from(value: String?): MediaType = entries.firstOrNull { it.name == value } ?: MANGA
    }
}

/** Units are never added together across types: an episode is not a chapter. */
enum class ProgressUnit(val singular: String, val plural: String) {
    EPISODE("episode", "episodes"),
    CHAPTER("chapter", "chapters"),
    VOLUME("volume", "volumes"),
    NONE("", "")
}

enum class PublicationStatus(val label: String) {
    UNKNOWN("Unknown"),
    UPCOMING("Upcoming"),
    ONGOING("Ongoing"),
    COMPLETED("Completed"),
    HIATUS("Hiatus"),
    CANCELLED("Cancelled");

    companion object {
        fun from(value: String?): PublicationStatus = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

enum class LibraryStatus {
    CURRENT, COMPLETED, ON_HOLD, DROPPED, PLANNED, NOT_STARTED;

    fun label(type: MediaType): String = when (this) {
        CURRENT -> if (type.isWatching) "Watching" else "Reading"
        COMPLETED -> "Completed"
        ON_HOLD -> "On Hold"
        DROPPED -> "Dropped"
        PLANNED -> if (type.isWatching) "Plan to Watch" else "Plan to Read"
        NOT_STARTED -> "Not Started"
    }

    companion object {
        fun from(value: String?): LibraryStatus = entries.firstOrNull { it.name == value } ?: PLANNED
    }
}

enum class ActivityType {
    ADDED, STARTED, PROGRESS, VOLUME_PROGRESS, COMPLETED, DROPPED, ON_HOLD, STATUS_CHANGED,
    RATING_CHANGED, REREAD_STARTED, CORRECTION
}

enum class CreditRole { AUTHOR, ARTIST, STUDIO, PUBLISHER }

enum class FranchiseRelation(val label: String) {
    SOURCE("Original source"),
    ADAPTATION("Adaptation"),
    SEQUEL("Sequel"),
    PREQUEL("Prequel"),
    SPIN_OFF("Spin-off"),
    OTHER("Related")
}

object Providers {
    const val ANILIST = "anilist"
    const val JIKAN = "jikan"
    const val MANGADEX = "mangadex"
    const val MANUAL = "manual"
}
