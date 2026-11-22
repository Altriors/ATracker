package app.atracker.data

import androidx.room.withTransaction
import app.atracker.data.db.AppDatabase
import app.atracker.data.db.FranchiseEntity
import app.atracker.data.db.FranchiseMemberEntity
import app.atracker.data.db.MediaRelationEntity
import app.atracker.domain.FranchiseRelation
import app.atracker.domain.MediaType

enum class LinkResult {
    LINKED, UPDATED,
    /** The title already belongs to a different franchise; nothing was changed. */
    IN_OTHER_FRANCHISE,
    MOVED
}

/** A provider-reported relation of a title, resolved against the local catalogue. */
data class Suggestion(
    val relation: MediaRelationEntity,
    /** Internal id when the related title is already in the local catalogue, else null. */
    val localMediaId: Long?,
    /** True when the related title is already in the same franchise as the source title. */
    val alreadyInFranchise: Boolean
)

/**
 * Franchise management. Entries are only ever connected by an explicit user action (or by the
 * user accepting a provider suggestion); similar names never link anything. Linking never copies
 * or merges progress: every member keeps its own entry, rating, history and statistics.
 */
class FranchiseRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao get() = db.franchiseDao()
    private val mediaDao get() = db.mediaDao()

    /** Sensible default role for the first member: a novel is usually the original source. */
    fun defaultRelationFor(type: MediaType): FranchiseRelation =
        if (type == MediaType.WEB_NOVEL || type == MediaType.LIGHT_NOVEL) FranchiseRelation.SOURCE else FranchiseRelation.ADAPTATION

    suspend fun createFranchise(name: String, seedMediaId: Long, seedRelation: FranchiseRelation? = null): Long =
        db.withTransaction {
            require(name.isNotBlank()) { "A franchise needs a name" }
            val media = mediaDao.getById(seedMediaId) ?: error("Unknown title")
            check(dao.memberFor(seedMediaId) == null) { "Title is already in a franchise" }
            val id = dao.insertFranchise(FranchiseEntity(name = name.trim(), createdAt = clock()))
            dao.upsertMember(
                FranchiseMemberEntity(
                    franchiseId = id, mediaId = seedMediaId,
                    relation = (seedRelation ?: defaultRelationFor(MediaType.from(media.mediaType))).name,
                    linkedAt = clock()
                )
            )
            id
        }

    suspend fun link(franchiseId: Long, mediaId: Long, relation: FranchiseRelation, allowMove: Boolean = false): LinkResult =
        db.withTransaction {
            check(dao.getFranchise(franchiseId) != null) { "Unknown franchise" }
            check(mediaDao.getById(mediaId) != null) { "Unknown title" }
            val existing = dao.memberFor(mediaId)
            when {
                existing != null && existing.franchiseId == franchiseId -> {
                    dao.setRelation(mediaId, relation.name)
                    LinkResult.UPDATED
                }
                existing != null && !allowMove -> LinkResult.IN_OTHER_FRANCHISE
                else -> {
                    val moved = existing != null
                    if (existing != null) removeMember(mediaId)
                    dao.upsertMember(
                        FranchiseMemberEntity(franchiseId = franchiseId, mediaId = mediaId, relation = relation.name, linkedAt = clock())
                    )
                    if (moved) LinkResult.MOVED else LinkResult.LINKED
                }
            }
        }

    /**
     * Connects two titles. Uses the franchise either one already belongs to, or creates a new one
     * named after [firstId]'s title. Two titles that are in different franchises are NOT merged.
     */
    suspend fun linkPair(firstId: Long, secondId: Long, secondRelation: FranchiseRelation): Pair<Long?, LinkResult> =
        db.withTransaction {
            require(firstId != secondId) { "Cannot link a title to itself" }
            val a = dao.memberFor(firstId)
            val b = dao.memberFor(secondId)
            when {
                a != null && b != null && a.franchiseId != b.franchiseId -> null to LinkResult.IN_OTHER_FRANCHISE
                a != null -> a.franchiseId to link(a.franchiseId, secondId, secondRelation)
                b != null -> b.franchiseId to link(b.franchiseId, firstId, FranchiseRelation.OTHER)
                else -> {
                    val first = mediaDao.getById(firstId) ?: error("Unknown title")
                    val id = createFranchise(first.title, firstId)
                    id to link(id, secondId, secondRelation)
                }
            }
        }

    /** Removes a title from its franchise. A franchise with fewer than two members is deleted. */
    suspend fun unlink(mediaId: Long) = db.withTransaction { removeMember(mediaId) }

    private suspend fun removeMember(mediaId: Long) {
        val member = dao.memberFor(mediaId) ?: return
        dao.unlink(mediaId)
        if (dao.memberCount(member.franchiseId) < 2) dao.deleteFranchise(member.franchiseId)
    }

    suspend fun rename(franchiseId: Long, name: String) {
        if (name.isNotBlank()) dao.rename(franchiseId, name.trim())
    }

    suspend fun setRelation(mediaId: Long, relation: FranchiseRelation) = dao.setRelation(mediaId, relation.name)

    /** Provider-reported relations of [mediaId], resolved against the local catalogue. */
    suspend fun suggestionsFor(mediaId: Long): List<Suggestion> {
        val own = dao.memberFor(mediaId)
        return mediaDao.relationsFor(mediaId).map { r ->
            val local = mediaDao.findIdBySource(r.provider, r.externalId)
            val inSame = own != null && local != null && dao.memberFor(local)?.franchiseId == own.franchiseId
            Suggestion(r, local, inSame)
        }.filter { it.localMediaId != mediaId }
    }
}
