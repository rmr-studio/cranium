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
import riven.core.models.identity.MatchSuggestion
import riven.core.models.identity.ScoredCandidate
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.repository.identity.IdentityClusterRepository
import riven.core.repository.identity.MatchSuggestionRepository
import riven.core.service.activity.ActivityService
import riven.core.util.ServiceUtil.findOrThrow
import java.math.BigDecimal
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
    private val clusterRepository: IdentityClusterRepository,
    private val activityService: ActivityService,
    private val logger: KLogger,
) {

    companion object {
        /** Sentinel userId for system-triggered Temporal activities that have no JWT context. */
        val SYSTEM_ACTOR_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    }

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
            // No softDeleteRejected() call needed: findRejectedSuggestion queries WHERE deleted = true,
            // so the row is already soft-deleted and excluded from the unique partial index (WHERE deleted = false).
            // The new PENDING suggestion can be inserted without constraint violation.
        }

        val entity = buildSuggestionEntity(workspaceId, sourceId, targetId, candidate)
        val suggestion = createIfNotExists(entity) ?: return null

        logSuggestionCreated(suggestion, userId ?: SYSTEM_ACTOR_ID)
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
            val isDedup = e.message?.contains("uq_match_suggestions_pair") == true
                    || e.cause?.message?.contains("uq_match_suggestions_pair") == true
            if (isDedup) {
                logger.info { "Duplicate suggestion skipped for ${entity.sourceEntityId}<->${entity.targetEntityId}" }
                null
            } else {
                throw e
            }
        }

    /**
     * Logs an activity entry for a newly created suggestion.
     * For system-triggered runs (no JWT context), [SYSTEM_ACTOR_ID] is used as the userId.
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
        if (sourceMember.clusterId != targetMember.clusterId) return false
        // Verify the shared cluster is not soft-deleted (@SQLRestriction filters deleted=false)
        return clusterRepository.findById(sourceMember.clusterId).isPresent
    }

    /**
     * Returns the canonical UUID pair (lower, higher) to satisfy the DB CHECK constraint
     * that source_entity_id < target_entity_id.
     */
    private fun canonicalOrder(a: UUID, b: UUID): Pair<UUID, UUID> = if (a < b) a to b else b to a
}
