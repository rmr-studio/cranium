package cranium.core.service.workflow.identity

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import cranium.core.models.identity.CandidateMatch
import cranium.core.models.identity.ScoredCandidate
import java.util.UUID

/**
 * Temporal activity interface for the identity matching pipeline.
 *
 * Each activity method corresponds to one independently retryable step of the pipeline:
 * 1. [findCandidates] — pg_trgm blocking query to retrieve candidate entity pairs
 * 2. [scoreCandidates] — weighted average scoring of candidates against trigger attributes
 * 3. [persistSuggestions] — idempotent write of above-threshold suggestions to the database
 *
 * All business logic lives in the corresponding services; this interface is a thin delegation layer.
 *
 * @see IdentityMatchActivitiesImpl
 */
@ActivityInterface
interface IdentityMatchActivities {

    /**
     * Finds candidate entity pairs for the given trigger entity using pg_trgm similarity.
     *
     * @param entityId the entity whose IDENTIFIER attributes are used for blocking
     * @param workspaceId workspace scope — candidates must be in the same workspace
     * @return list of candidate matches, empty if no IDENTIFIER attributes exist
     */
    @ActivityMethod
    fun findCandidates(entityId: UUID, workspaceId: UUID): List<CandidateMatch>

    /**
     * Scores the candidate list against the trigger entity's attributes.
     *
     * Fetches trigger attributes internally, then applies the weighted average scoring formula.
     *
     * @param triggerEntityId the source entity being matched
     * @param workspaceId workspace scope for trigger attribute retrieval
     * @param candidates raw candidate matches from [findCandidates]
     * @return list of scored candidates above the minimum score threshold
     */
    @ActivityMethod
    fun scoreCandidates(triggerEntityId: UUID, workspaceId: UUID, candidates: List<CandidateMatch>): List<ScoredCandidate>

    /**
     * Persists above-threshold scored candidates as PENDING match suggestions.
     *
     * Applies idempotency (duplicate pair silently skipped), canonical UUID ordering,
     * and re-suggestion logic for previously rejected pairs.
     *
     * @param workspaceId workspace these suggestions belong to
     * @param scoredCandidates the scored pairs from [scoreCandidates]
     * @param userId the user or null for system-triggered Temporal executions
     * @return count of new suggestions successfully persisted
     */
    @ActivityMethod
    fun persistSuggestions(workspaceId: UUID, scoredCandidates: List<ScoredCandidate>, userId: UUID?): Int
}
