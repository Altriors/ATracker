package app.atracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class LibraryItem(
    @Embedded val entry: LibraryEntryEntity,
    @Relation(parentColumn = "mediaId", entityColumn = "id") val media: MediaEntity
)

data class MediaSourceRef(val provider: String, val externalId: String)

data class FranchiseMemberRow(
    @Embedded val member: FranchiseMemberEntity,
    @Relation(parentColumn = "mediaId", entityColumn = "id") val media: MediaEntity,
    @Relation(parentColumn = "mediaId", entityColumn = "mediaId") val entry: LibraryEntryEntity?
)

@Dao
interface MediaDao {
    @Insert
    suspend fun insert(media: MediaEntity): Long

    /** Updates ONLY catalogue columns in the media table. Personal tables are never touched. */
    @Update
    suspend fun update(media: MediaEntity)

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE id = :id")
    fun observeById(id: Long): Flow<MediaEntity?>

    @Query(
        """SELECT m.id FROM media m JOIN media_source s ON s.mediaId = m.id
           WHERE s.provider = :provider AND s.externalId = :externalId LIMIT 1"""
    )
    suspend fun findIdBySource(provider: String, externalId: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: MediaSourceEntity): Long

    @Query("SELECT * FROM media_source WHERE mediaId = :mediaId")
    suspend fun sourcesFor(mediaId: Long): List<MediaSourceEntity>

    @Query("DELETE FROM media_title WHERE mediaId = :mediaId")
    suspend fun clearTitles(mediaId: Long)

    @Insert
    suspend fun insertTitles(titles: List<MediaTitleEntity>)

    @Query("SELECT * FROM media_title WHERE mediaId = :mediaId")
    suspend fun titlesFor(mediaId: Long): List<MediaTitleEntity>

    @Query("SELECT * FROM media_title WHERE mediaId = :mediaId")
    fun observeTitles(mediaId: Long): Flow<List<MediaTitleEntity>>

    @Query("SELECT * FROM media_title")
    fun observeAllTitles(): Flow<List<MediaTitleEntity>>

    @Query("SELECT * FROM media_genre")
    fun observeAllGenres(): Flow<List<MediaGenreEntity>>

    @Query("SELECT * FROM media_genre")
    suspend fun observeAllGenresOnce(): List<MediaGenreEntity>

    @Query("SELECT id FROM media WHERE title = :title AND mediaType = :type LIMIT 1")
    suspend fun findIdByTitleType(title: String, type: String): Long?

    @Query("SELECT * FROM media_credit")
    fun observeAllCredits(): Flow<List<MediaCreditEntity>>

    @Query("SELECT * FROM media_source WHERE mediaId = :mediaId")
    fun observeSources(mediaId: Long): Flow<List<MediaSourceEntity>>

    @Query("SELECT COALESCE(GROUP_CONCAT(name, ', '), '') FROM media_genre WHERE mediaId = :mediaId AND kind = 'GENRE'")
    suspend fun observeGenresOnce(mediaId: Long): String

    @Query("SELECT COALESCE(GROUP_CONCAT(name, ', '), '') FROM media_credit WHERE mediaId = :mediaId AND role = 'AUTHOR'")
    suspend fun observeAuthorsOnce(mediaId: Long): String

    @Query("DELETE FROM media_genre WHERE mediaId = :mediaId")
    suspend fun clearGenres(mediaId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenres(genres: List<MediaGenreEntity>)

    @Query("SELECT * FROM media_genre WHERE mediaId = :mediaId")
    fun observeGenres(mediaId: Long): Flow<List<MediaGenreEntity>>

    @Query("DELETE FROM media_credit WHERE mediaId = :mediaId")
    suspend fun clearCredits(mediaId: Long)

    @Insert
    suspend fun insertCredits(credits: List<MediaCreditEntity>)

    @Query("SELECT * FROM media_credit WHERE mediaId = :mediaId")
    fun observeCredits(mediaId: Long): Flow<List<MediaCreditEntity>>

    @Query(
        """SELECT DISTINCT m.* FROM media m LEFT JOIN media_title t ON t.mediaId = m.id
           WHERE (m.title LIKE '%' || :q || '%' OR m.titleNative LIKE '%' || :q || '%'
                  OR t.title LIKE '%' || :q || '%')
             AND (:type IS NULL OR m.mediaType = :type)
           ORDER BY m.title LIMIT :limit"""
    )
    suspend fun searchLocal(q: String, type: String?, limit: Int = 50): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media")
    suspend fun count(): Int

    @Query("DELETE FROM media_relation WHERE mediaId = :mediaId")
    suspend fun clearRelations(mediaId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelations(relations: List<MediaRelationEntity>)

    @Query("SELECT * FROM media_relation WHERE mediaId = :mediaId")
    suspend fun relationsFor(mediaId: Long): List<MediaRelationEntity>

    @Query("SELECT * FROM media_relation WHERE mediaId = :mediaId")
    fun observeRelations(mediaId: Long): Flow<List<MediaRelationEntity>>

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<MediaEntity>

    @Query("DELETE FROM release_info WHERE mediaId = :mediaId AND source = :source")
    suspend fun clearReleases(mediaId: Long, source: String)

    @Insert
    suspend fun insertReleases(releases: List<ReleaseInfoEntity>)

    @Query("SELECT * FROM release_info WHERE mediaId = :mediaId ORDER BY releaseAt")
    suspend fun releasesFor(mediaId: Long): List<ReleaseInfoEntity>

    @Query("SELECT * FROM release_info WHERE releaseAt >= :from AND releaseAt < :to ORDER BY releaseAt")
    fun observeReleases(from: Long, to: Long): Flow<List<ReleaseInfoEntity>>

    /** Cache clean-up: only removes catalogue rows that the user has never added to the library. */
    @Query(
        """DELETE FROM media WHERE id NOT IN (SELECT mediaId FROM library_entry)
           AND id NOT IN (SELECT mediaId FROM franchise_member)
           AND isManual = 0 AND metadataUpdatedAt < :olderThan"""
    )
    suspend fun deleteUnusedCache(olderThan: Long): Int
}

@Dao
interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LibraryEntryEntity)

    @Query("SELECT * FROM library_entry WHERE mediaId = :mediaId")
    suspend fun getEntry(mediaId: Long): LibraryEntryEntity?

    @Query("SELECT * FROM library_entry WHERE mediaId = :mediaId")
    fun observeEntry(mediaId: Long): Flow<LibraryEntryEntity?>

    @Transaction
    @Query("SELECT * FROM library_entry ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LibraryItem>>

    @Query("DELETE FROM library_entry WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: Long)

    @Query("SELECT COUNT(*) FROM library_entry")
    fun observeCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM library_entry ORDER BY mediaId")
    suspend fun allItems(): List<LibraryItem>
}

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(event: ActivityEventEntity): Long

    @Query("SELECT * FROM activity_event WHERE mediaId = :mediaId ORDER BY timestamp DESC, id DESC")
    fun observeForMedia(mediaId: Long): Flow<List<ActivityEventEntity>>

    @Query("SELECT * FROM activity_event WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC, id DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<ActivityEventEntity>>

    @Query("SELECT * FROM activity_event WHERE mediaId = :mediaId")
    suspend fun allForMedia(mediaId: Long): List<ActivityEventEntity>

    @Query("SELECT * FROM activity_event ORDER BY id")
    suspend fun allEvents(): List<ActivityEventEntity>

    @Query("UPDATE activity_event SET voidedAt = :at WHERE id = :id")
    suspend fun setVoided(id: Long, at: Long?)

    @Query("SELECT r.id AS id, r.mediaId AS mediaId, m.title AS title, m.mediaType AS mediaType, r.kind AS kind, r.number AS number, r.releaseAt AS releaseAt FROM release_info r JOIN media m ON m.id = r.mediaId JOIN library_entry l ON l.mediaId = r.mediaId WHERE r.releaseAt >= :from AND r.releaseAt < :to AND l.status IN ('CURRENT','PLANNED','ON_HOLD') ORDER BY r.releaseAt")
    fun observeLibraryReleases(from: Long, to: Long): Flow<List<ReleaseRow>>

    @Query("SELECT e.*, m.title AS title, m.mediaType AS mediaType FROM activity_event e JOIN media m ON m.id = e.mediaId WHERE e.timestamp >= :from AND e.timestamp < :to ORDER BY e.timestamp DESC, e.id DESC")
    fun observeHistory(from: Long, to: Long): Flow<List<HistoryRow>>
}

data class HistoryRow(
    @androidx.room.Embedded val event: ActivityEventEntity,
    val title: String,
    val mediaType: String
)

@Dao
interface FranchiseDao {
    @Insert
    suspend fun insertFranchise(franchise: FranchiseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: FranchiseMemberEntity)

    @Query("DELETE FROM franchise_member WHERE mediaId = :mediaId")
    suspend fun unlink(mediaId: Long)

    @Query("SELECT * FROM franchise_member WHERE mediaId = :mediaId")
    suspend fun memberFor(mediaId: Long): FranchiseMemberEntity?

    @Query("SELECT * FROM franchise_member WHERE franchiseId = :franchiseId")
    suspend fun membersOf(franchiseId: Long): List<FranchiseMemberEntity>

    @Query("SELECT * FROM franchise WHERE id = :id")
    suspend fun getFranchise(id: Long): FranchiseEntity?

    @Query("SELECT * FROM franchise ORDER BY name")
    fun observeAll(): Flow<List<FranchiseEntity>>

    @Query("DELETE FROM franchise WHERE id = :id")
    suspend fun deleteFranchise(id: Long)

    @Query("UPDATE franchise SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE franchise_member SET relation = :relation WHERE mediaId = :mediaId")
    suspend fun setRelation(mediaId: Long, relation: String)

    @Query("SELECT COUNT(*) FROM franchise_member WHERE franchiseId = :franchiseId")
    suspend fun memberCount(franchiseId: Long): Int

    @Transaction
    @Query("SELECT * FROM franchise_member WHERE franchiseId = :franchiseId ORDER BY id")
    fun observeMembers(franchiseId: Long): Flow<List<FranchiseMemberRow>>

    @Query("SELECT franchiseId FROM franchise_member WHERE mediaId = :mediaId")
    fun observeFranchiseIdOf(mediaId: Long): Flow<Long?>

    @Query("SELECT * FROM franchise WHERE id = :id")
    fun observeFranchise(id: Long): Flow<FranchiseEntity?>

    @Query("SELECT f.*, (SELECT COUNT(*) FROM franchise_member m WHERE m.franchiseId = f.id) AS memberCount FROM franchise f ORDER BY f.name COLLATE NOCASE")
    suspend fun observeSummariesOnce(): List<FranchiseSummary>

    @Query("SELECT f.*, (SELECT COUNT(*) FROM franchise_member m WHERE m.franchiseId = f.id) AS memberCount FROM franchise f ORDER BY f.name COLLATE NOCASE")
    fun observeSummaries(): Flow<List<FranchiseSummary>>
}

data class FranchiseSummary(
    @Embedded val franchise: FranchiseEntity,
    val memberCount: Int
)

@Dao
interface ListDao {
    @Insert
    suspend fun insertList(list: CustomListEntity): Long

    @Delete
    suspend fun deleteList(list: CustomListEntity)

    @Query("SELECT * FROM custom_list ORDER BY name")
    fun observeLists(): Flow<List<CustomListEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItem(item: CustomListItemEntity)

    @Query("DELETE FROM custom_list_item WHERE listId = :listId AND mediaId = :mediaId")
    suspend fun removeItem(listId: Long, mediaId: Long)

    @Query("SELECT mediaId FROM custom_list_item WHERE listId = :listId ORDER BY position, addedAt")
    fun observeItemIds(listId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: UserTagEntity): Long

    @Query("SELECT * FROM user_tag WHERE name = :name")
    suspend fun tagByName(name: String): UserTagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addLibraryTag(tag: LibraryTagEntity)

    @Query("DELETE FROM library_tag WHERE mediaId = :mediaId AND tagId = :tagId")
    suspend fun removeLibraryTag(mediaId: Long, tagId: Long)

    @Query("SELECT t.* FROM user_tag t JOIN library_tag l ON l.tagId = t.id WHERE l.mediaId = :mediaId ORDER BY t.name")
    fun observeTagsFor(mediaId: Long): Flow<List<UserTagEntity>>
}

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CatalogueCacheEntity)

    @Query("SELECT * FROM catalogue_cache WHERE cacheKey = :key")
    suspend fun get(key: String): CatalogueCacheEntity?

    @Query("DELETE FROM catalogue_cache WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long): Int
}

@Dao
interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSettingEntity)

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    fun observe(key: String): Flow<String?>
}

data class ReleaseRow(
    val id: Long, val mediaId: Long, val title: String, val mediaType: String,
    val kind: String, val number: Int?, val releaseAt: Long
)
