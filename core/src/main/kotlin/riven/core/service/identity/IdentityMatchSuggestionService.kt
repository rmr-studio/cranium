package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.identity.MatchSuggestionEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.identity.MatchSuggestionStatus
import riven.core.enums.util.OperationType
import riven.core.exceptions.ConflictException
import riven.core.models.identity.MatchSuggestion
import riven.core.models.identity.ScoredCandidate
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.repository.identity.MatchSuggestionRepository
import riven.core.service.activity.ActivityService
import riven.core.util.ServiceUtil.findOrThrow
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Handles the write path of the matching pipeline: suggestion persistence with idempotency,
 * rejection with signal snapshot, re-suggestion logic, and audit logging.
 *
 * This service has no @PreAuthorize — it is called from Temporal activities where there is
 * no JWT security context. Workspace ID is passed explicitly by the caller.
 */
@Service
class IdentityMatchSuggestionService(
    private val repository: MatchSuggestionRepository,
    private val memberRepository: IdentityClusterMemberRepository,
    private val activityService: ActivityService,
    private val logger: KLogger,
) {

    // ------ Public mutations ------

    /**
     * Persists match suggestions for each [ScoredCandidate] in the list.
     *
     * Applies canonical UUID ordering, checks for existing active or rejected suggestions,
     * and handles re-suggestion when a higher-scored candidate overrides a rejected pair.
     *
     * @param workspaceId The workspace these suggestions belong to.
     * @param scoredCandidates The scored entity pairs from the scoring step.
     * @param userId The user initiating the run, or null for system-triggered Temporal activities.
     * @return The number of new suggestions successfully persisted.
     */
    @Transactional
    fun persistSuggestions(workspaceId: UUID, scoredCandidates: List<ScoredCandidate>, userId: UUID?): Int =
        scoredCandidates.count { candidate ->
            createOrResuggest(candidate, workspaceId, userId) != null
        }

    /**
     * Rejects a match suggestion by transitioning its status to REJECTED and writing
     * a snapshot of the current signals to the [MatchSuggestionEntity.rejectionSignals] field.
     *
     * The rejectionSignals snapshot is the prerequisite for re-suggestion (SUGG-04): the
     * re-suggestion check compares the new score against [MatchSuggestionEntity.confidenceScore]
     * on the rejected row, so this method ensures that state is correctly persisted.
     *
     * @param suggestionId The UUID of the suggestion to reject.
     * @param userId The user performing the rejection — always required (non-null).
     * @return The updated [MatchSuggestion] domain model.
     * @throws riven.core.exceptions.NotFoundException if the suggestion does not exist.
     * @throws ConflictException if the suggestion is not in PENDING status.
     */
    @Transactional
    fun rejectSuggestion(suggestionId: UUID, userId: UUID): MatchSuggestion {
        val entity = findOrThrow { repository.findById(suggestionId) }
        validateRejectable(entity)
        applyRejection(entity, userId)
        val saved = repository.save(entity)
        logRejectionActivity(saved, userId)
        return saved.toModel()
    }

    // ------ Private helpers ------

    /**
     * Attempts to create or re-suggest a match for the given [candidate].
     *
     * Returns null if the pair is skipped due to an existing active suggestion or a
     * non-improving score against a prior rejected suggestion.
     */
    private fun createOrResuggest(candidate: ScoredCandidate, workspaceId: UUID, userId: UUID?): MatchSuggestion? {
        val (sourceId, targetId) = canonicalOrder(candidate.sourceEntityId, candidate.targetEntityId)

        if (inSameCluster(sourceId, targetId)) {
            logger.debug { "Skipping suggestion for $sourceId<->$targetId: already in same cluster" }
            return null
        }

        val active = repository.findActiveSuggestion(workspaceId, sourceId, targetId)
        if (active != null) {
            logger.debug { "Skipping suggestion for $sourceId<->$targetId: active suggestion already exists" }
            return null
        }

        val rejected = repository.findRejectedSuggestion(workspaceId, sourceId, targetId)
        if (rejected != null) {
            if (candidate.compositeScore <= rejected.confidenceScore.toDouble()) {
                logger.debug { "Skipping re-suggestion for $sourceId<->$targetId: score ${candidate.compositeScore} <= rejected ${rejected.confidenceScore}" }
                return null
            }
            softDeleteRejected(rejected)
        }

        val entity = buildSuggestionEntity(workspaceId, sourceId, targetId, candidate)
        val suggestion = createIfNotExists(entity) ?: return null

        if (userId != null) {
            logSuggestionCreated(suggestion, userId)
        }
        return suggestion
    }

    /**
     * Saves the suggestion entity, catching [DataIntegrityViolationException] for duplicate
     * constraint violations (idempotent skip pattern).
     */
    private fun createIfNotExists(entity: MatchSuggestionEntity): MatchSuggestion? =
        try {
            repository.saveAndFlush(entity).toModel()
        } catch (e: DataIntegrityViolationException) {
            logger.info { "Duplicate suggestion skipped for ${entity.sourceEntityId}<->${entity.targetEntityId}" }
            null
        }

    /**
     * Soft-deletes a previously rejected suggestion row so it no longer appears
     * in active queries and a new PENDING suggestion can be created.
     */
    private fun softDeleteRejected(rejected: MatchSuggestionEntity) {
        rejected.deleted = true
        rejected.deletedAt = ZonedDateTime.now()
        repository.save(rejected)
    }

    /**
     * Validates that the suggestion can be rejected (must be PENDING status).
     */
    private fun validateRejectable(entity: MatchSuggestionEntity) {
        if (entity.status != MatchSuggestionStatus.PENDING) {
            throw ConflictException("Suggestion is already ${entity.status}")
        }
    }

    /**
     * Applies rejection state to the entity: writes rejectionSignals snapshot,
     * sets status, resolvedBy, resolvedAt, and soft-deletes the row.
     *
     * Soft-deletion is required here because [MatchSuggestionRepository.findRejectedSuggestion]
     * looks for rows where `deleted = true AND status = REJECTED`. Callers that want to check
     * for active suggestions use [MatchSuggestionRepository.findActiveSuggestion] which filters
     * by `deleted = false`, so soft-deleting on rejection keeps both queries consistent.
     */
    private fun applyRejection(entity: MatchSuggestionEntity, userId: UUID) {
        entity.rejectionSignals = mapOf(
            "signals" to entity.signals,
            "confidenceScore" to entity.confidenceScore.toDouble(),
        )
        entity.status = MatchSuggestionStatus.REJECTED
        entity.resolvedBy = userId
        entity.resolvedAt = ZonedDateTime.now()
        entity.deleted = true
        entity.deletedAt = ZonedDateTime.now()
    }

    /**
     * Logs an activity entry for a newly created suggestion.
     * Only called when [userId] is non-null (system activities are not logged).
     */
    private fun logSuggestionCreated(suggestion: MatchSuggestion, userId: UUID) {
        activityService.logActivity(
            activity = Activity.MATCH_SUGGESTION,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = suggestion.workspaceId,
            entityType = ApplicationEntityType.MATCH_SUGGESTION,
            entityId = suggestion.id,
            details = mapOf(
                "sourceEntityId" to suggestion.sourceEntityId.toString(),
                "targetEntityId" to suggestion.targetEntityId.toString(),
                "confidenceScore" to suggestion.confidenceScore.toDouble(),
                "signalCount" to suggestion.signals.size,
            ),
        )
    }

    /**
     * Logs an activity entry for a rejected suggestion.
     */
    private fun logRejectionActivity(entity: MatchSuggestionEntity, userId: UUID) {
        activityService.logActivity(
            activity = Activity.MATCH_SUGGESTION,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = entity.workspaceId,
            entityType = ApplicationEntityType.MATCH_SUGGESTION,
            entityId = requireNotNull(entity.id) { "Suggestion ID must not be null when logging rejection" },
            details = mapOf(
                "action" to "rejected",
                "sourceEntityId" to entity.sourceEntityId.toString(),
                "targetEntityId" to entity.targetEntityId.toString(),
                "confidenceScore" to entity.confidenceScore.toDouble(),
            ),
        )
    }

    /**
     * Builds a new [MatchSuggestionEntity] from the scored candidate, applying canonical ordering.
     */
    private fun buildSuggestionEntity(
        workspaceId: UUID,
        sourceId: UUID,
        targetId: UUID,
        candidate: ScoredCandidate,
    ): MatchSuggestionEntity = MatchSuggestionEntity(
        workspaceId = workspaceId,
        sourceEntityId = sourceId,
        targetEntityId = targetId,
        status = MatchSuggestionStatus.PENDING,
        confidenceScore = BigDecimal.valueOf(candidate.compositeScore),
        signals = candidate.signals.map { it.toMap() },
    )

    /**
     * Returns true if both entities are already members of the same identity cluster.
     *
     * Uses [IdentityClusterMemberRepository.findByEntityId] which does not filter by cluster
     * soft-delete status — members of soft-deleted clusters are still returned. This is safe
     * because a soft-deleted cluster's members will have different clusterIds from any active
     * cluster the other entity belongs to, so the guard will not produce false positives.
     */
    private fun inSameCluster(sourceId: UUID, targetId: UUID): Boolean {
        val sourceMember = memberRepository.findByEntityId(sourceId) ?: return false
        val targetMember = memberRepository.findByEntityId(targetId) ?: return false
        return sourceMember.clusterId == targetMember.clusterId
    }

    /**
     * Returns the canonical UUID pair (lower, higher) to satisfy the DB CHECK constraint
     * that source_entity_id < target_entity_id.
     */
    private fun canonicalOrder(a: UUID, b: UUID): Pair<UUID, UUID> = if (a < b) a to b else b to a
}
