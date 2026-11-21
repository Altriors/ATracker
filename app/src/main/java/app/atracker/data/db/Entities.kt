package app.atracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------------------------
// GLOBAL / CATALOGUE tables. These may be rewritten by metadata refreshes.
// They must never contain personal data (progress, ratings, notes ...).
// ---------------------------------------------------------------------------------------------

@Entity(tableName = "media", indices = [Index("mediaType"), Index("title")])
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: String,
    /** TV, MOVIE, OVA, ONA, SPECIAL, SHORT, NOVEL, ONE_SHOT ... free text from the provider. */
    val format: String? = null,
    val title: String,
    val titleNative: String? = null,
    val synopsis: String? = null,
    val coverUrl: String? = null,
    val pubStatus: String = "UNKNOWN",
    val totalEpisodes: Int? = null,
    val totalChapters: Int? = null,
    val totalVolumes: Int? = null,
    val episodeDurationMin: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val startYear: Int? = null,
    /** External average score normalised to 0..100. Null when the provider has none. */
    val averageScore: Int? = null,
    val countryOfOrigin: String? = null,
    /** True when the entry was created by hand (no provider). Manual data is never refreshed. */
    val isManual: Boolean = false,
    val createdAt: Long,
    val metadataUpdatedAt: Long
)

/** Links one internal media row to a provider's id. A media row may have several sources. */
@Entity(
    tableName = "media_source",
    foreignKeys = [ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mediaId"), Index(value = ["provider", "externalId"], unique = true)]
)
data class MediaSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val provider: String,
    val externalId: String,
    val url: String? = null,
    val fetchedAt: Long
)

@Entity(
    tableName = "media_title",
    foreignKeys = [ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mediaId"), Index("title")]
)
data class MediaTitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val title: String,
    val language: String? = null
)

@Entity(
    tableName = "media_genre",
    primaryKeys = ["mediaId", "name", "kind"],
    foreignKeys = [ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("name")]
)
data class MediaGenreEntity(
    val mediaId: Long,
    val name: String,
    /** GENRE or TAG */
    val kind: String = "GENRE"
)

@Entity(
    tableName = "media_credit",
    foreignKeys = [ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mediaId"), Index("name")]
)
data class MediaCreditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val role: String,
    val name: String
)

@Entity(
    tableName = "release_info",
    foreignKeys = [ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mediaId"), Index("releaseAt")]
)
data class ReleaseInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    /** EPISODE, CHAPTER, VOLUME or PREMIERE */
    val kind: String,
    val number: Int? = null,
    val releaseAt: Long,
    val source: String
)

/** Raw API responses so searches and detail pages work offline and respect rate limits. */
@Entity(tableName = "catalogue_cache")
data class CatalogueCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payload: String,
    val fetchedAt: Long,
    val expiresAt: Long
)

// ---------------------------------------------------------------------------------------------
// FRANCHISES. Manual links only; entries are never merged automatically.
// ---------------------------------------------------------------------------------------------

@Entity(tableName = "franchise")
data class FranchiseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "franchise_member",
    foreignKeys = [
        ForeignKey(FranchiseEntity::class, ["id"], ["franchiseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("franchiseId"), Index(value = ["mediaId"], unique = true)]
)
data class FranchiseMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val franchiseId: Long,
    val mediaId: Long,
    val relation: String,
    val linkedAt: Long
)

// ---------------------------------------------------------------------------------------------
// PERSONAL tables. Never touched by metadata refreshes. Media rows referenced here are never
// deleted by cache clean-up (NO_ACTION keeps the database from silently dropping user data).
// ---------------------------------------------------------------------------------------------

@Entity(
    tableName = "library_entry",
    foreignKeys = [ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index("status"), Index("updatedAt"), Index("addedAt")]
)
data class LibraryEntryEntity(
    @PrimaryKey val mediaId: Long,
    val status: String,
    /** Episodes watched / chapters read, or volumes read for light novels. */
    val progress: Int = 0,
    /** Volumes read, for types where volumes are tracked alongside chapters (manga). */
    val progressVolumes: Int = 0,
    /** Rating 0..100 in steps of 5; null when unrated. Shown to the user as 0-10 with half steps. */
    val rating: Int? = null,
    val review: String? = null,
    val notes: String? = null,
    val favorite: Boolean = false,
    /** Number of completed rewatches/rereads. The current run is run number repeatCount + 1. */
    val repeatCount: Int = 0,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val lastActivityAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val addedAt: Long,
    val updatedAt: Long
)

/**
 * Append-only event log. Corrections are new rows that void or supersede earlier rows so current
 * progress (stored in library_entry) can never be corrupted by editing history.
 */
@Entity(
    tableName = "activity_event",
    foreignKeys = [ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index("mediaId"), Index("timestamp"), Index("type")]
)
data class ActivityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val type: String,
    /** EPISODE, CHAPTER, VOLUME or NONE. Statistics group by this so units are never mixed. */
    val unit: String,
    val fromValue: Int? = null,
    val toValue: Int? = null,
    /** Units consumed by this event (can be negative for a correction). */
    val delta: Int = 0,
    val runNumber: Int = 1,
    val timestamp: Long,
    val note: String? = null,
    /** Set when this event has been voided by the user; voided events are ignored by statistics. */
    val voidedAt: Long? = null
)

@Entity(tableName = "custom_list")
data class CustomListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "custom_list_item",
    primaryKeys = ["listId", "mediaId"],
    foreignKeys = [
        ForeignKey(CustomListEntity::class, ["id"], ["listId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.NO_ACTION)
    ],
    indices = [Index("mediaId")]
)
data class CustomListItemEntity(
    val listId: Long,
    val mediaId: Long,
    val position: Int = 0,
    val addedAt: Long
)

@Entity(tableName = "user_tag", indices = [Index(value = ["name"], unique = true)])
data class UserTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "library_tag",
    primaryKeys = ["mediaId", "tagId"],
    foreignKeys = [
        ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(UserTagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tagId")]
)
data class LibraryTagEntity(val mediaId: Long, val tagId: Long)

/** Small key/value store for app settings kept inside the same database so backups include them. */
@Entity(tableName = "app_setting")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
