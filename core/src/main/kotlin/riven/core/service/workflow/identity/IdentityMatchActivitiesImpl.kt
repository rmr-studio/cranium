package riven.core.service.workflow.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Component
import riven.core.models.identity.CandidateMatch
import riven.core.models.identity.ScoredCandidate
import riven.core.service.identity.IdentityMatchCandidateService
import riven.core.service.identity.IdentityMatchScoringService
import riven.core.service.identity.IdentityMatchSuggestionService
import java.util.UUID

/**
 * Temporal activity implementation for the identity matching pipeline.
 *
 * This bean is a thin delegation layer — all business logic lives in the three
 * matching services. No matching or persistence logic belongs here.
 *
 * Registered on the [riven.core.configuration.workflow.TemporalWorkerConfiguration.IDENTITY_MATCH_QUEUE]
 * task queue by [riven.core.configuration.workflow.TemporalWorkerConfiguration].
 *
 * @property candidateService finds candidate entities via pg_trgm blocking query
 * @property scoringService applies the weighted average scoring formula
 * @property suggestionService persists suggestions with idempotency and re-suggestion logic
 */
@Component
class IdentityMatchActivitiesImpl(
    private val candidateService: IdentityMatchCandidateService,
    private val scoringService: IdentityMatchScoringService,
    private val suggestionService: IdentityMatchSuggestionService,
    private val logger: KLogger,
) : IdentityMatchActivities {

    override fun findCandidates(entityId: UUID, workspaceId: UUID): List<CandidateMatch> {
        logger.info { "FindCandidates activity: entityId=$entityId workspaceId=$workspaceId" }
        return candidateService.findCandidates(entityId, workspaceId)
    }

    override fun scoreCandidates(
        triggerEntityId: UUID,
        workspaceId: UUID,
        candidates: List<CandidateMatch>,
    ): List<ScoredCandidate> {
        logger.info { "ScoreCandidates activity: entityId=$triggerEntityId candidates=${candidates.size}" }
        val triggerAttributes = candidateService.getTriggerAttributes(triggerEntityId, workspaceId)
        return scoringService.scoreCandidates(triggerEntityId, triggerAttributes, candidates)
    }

    override fun persistSuggestions(
        workspaceId: UUID,
        scoredCandidates: List<ScoredCandidate>,
        userId: UUID?,
    ): Int {
        logger.info { "PersistSuggestions activity: workspaceId=$workspaceId candidates=${scoredCandidates.size} userId=$userId" }
        return suggestionService.persistSuggestions(workspaceId, scoredCandidates, userId)
    }
}
