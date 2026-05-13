package cranium.core.service.connotation

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import cranium.core.configuration.properties.ConnotationAnalysisConfigurationProperties
import cranium.core.enums.activity.Activity
import cranium.core.enums.connotation.ConnotationStatus
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.util.OperationType
import cranium.core.models.catalog.ConnotationSignals
import cranium.core.models.connotation.AnalysisTier
import cranium.core.models.connotation.SentimentAnalysisOutcome
import cranium.core.models.connotation.SentimentMetadata
import cranium.core.service.activity.ActivityService
import cranium.core.service.activity.log
import cranium.core.service.auth.AuthTokenService
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Workspace-scoped sentiment analysis dispatcher.
 *
 * Routes a single-entity analysis request to the tier declared in [ConnotationSignals.tier].
 * DETERMINISTIC delegates to [DeterministicConnotationMapper]. CLASSIFIER / INFERENCE return
 * a FAILED sentinel until those tiers are implemented in later phases — the worker path needs
 * an observable outcome (logged + persisted) rather than a thrown error that aborts the
 * workflow before activity logging fires.
 *
 * Always returns a [cranium.core.models.connotation.SentimentMetadata] — DETERMINISTIC mapping
 * failures and unsupported tiers are both encoded as `status = FAILED` rather than thrown, so
 * the enrichment pipeline can persist the sentinel without aborting RELATIONAL/STRUCTURAL
 * metadata writes.
 *
 * Caller is responsible for resolving attribute values from manifest keys to entity-level
 * values before calling. This keeps the service free of repository injections and allows
 * EnrichmentService (which already has the resolved attribute map during enrichment) to
 * pass them through cheaply.
 *
 * Activity logging fires on every analyze call regardless of outcome.
 *
 * No `@PreAuthorize` — this service runs inside a Temporal activity (via
 * [cranium.core.service.enrichment.EnrichmentService.analyzeSemantics]) where there is no JWT
 * security context. Workspace ownership is established by the caller before reaching this
 * dispatcher; activity logging uses [AuthTokenService.getUserIdOrSystem] so the seeded system
 * user appears as the actor when no JWT is present.
 */
@Service
class ConnotationAnalysisService(
    private val deterministicConnotationMapper: DeterministicConnotationMapper,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val properties: ConnotationAnalysisConfigurationProperties,
    private val logger: KLogger,
) {

    /**
     * Analyze a single entity's SENTIMENT metadata using the manifest-declared tier.
     *
     * @param entityId Workspace-scoped entity being analyzed.
     * @param workspaceId Workspace owning the entity (used for activity logging).
     * @param signals Manifest-declared connotation signals (tier + scale + theme keys).
     * @param sourceValue Pre-resolved value for `signals.sentimentAttribute` (caller does the manifest-key -> attribute UUID lookup).
     * @param themeValues Pre-resolved values for `signals.themeAttributes` (manifest key -> string value or null).
     * @return Populated [SentimentMetadata]; status `ANALYZED` on success, `FAILED` on DETERMINISTIC mapping failure or unsupported tier.
     */
    fun analyze(
        entityId: UUID,
        workspaceId: UUID,
        signals: ConnotationSignals,
        sourceValue: Any?,
        themeValues: Map<String, String?>,
    ): SentimentMetadata {
        val userId = authTokenService.getUserIdOrSystem()

        return when (signals.tier) {
            AnalysisTier.DETERMINISTIC -> runDeterministicAnalysis(entityId, workspaceId, signals, sourceValue, themeValues)
            //TODO: Implement CLASSIFIER/INFERENCE mappers and update this when we have real implementations to call
            AnalysisTier.CLASSIFIER, AnalysisTier.INFERENCE -> unsupportedTierSentinel(entityId, workspaceId, signals.tier)
        }.also {
            activityService.log(
                activity = Activity.ENTITY_CONNOTATION,
                operation = OperationType.ANALYZE,
                userId = userId,
                workspaceId = workspaceId,
                entityType = ApplicationEntityType.ENTITY_CONNOTATION,
                entityId = entityId,
                "tier" to signals.tier.name,
                "status" to it.status.name,
                "sentiment" to it.sentiment,
                "analysisVersion" to it.analysisVersion,
            )
        }


    }

    /**
     * Builds a FAILED sentinel for a tier the dispatcher cannot service yet (CLASSIFIER /
     * INFERENCE). Returning instead of throwing keeps the worker path observable: the caller's
     * `.also` activity-logger fires with status=FAILED and the snapshot upsert proceeds, so
     * RELATIONAL/STRUCTURAL metadata still lands and the unsupported-tier rejection appears
     * in the audit trail rather than crashing the workflow.
     */
    private fun unsupportedTierSentinel(
        entityId: UUID,
        workspaceId: UUID,
        tier: AnalysisTier,
    ): SentimentMetadata {
        logger.warn {
            "Connotation analysis tier $tier not implemented; emitting FAILED sentinel " +
                "for entity=$entityId workspace=$workspaceId"
        }
        return SentimentMetadata(
            status = ConnotationStatus.FAILED,
            analysisTier = tier,
            analysisVersion = properties.deterministicCurrentVersion,
            analyzedAt = ZonedDateTime.now(),
        )
    }

    private fun runDeterministicAnalysis(
        entityId: UUID,
        workspaceId: UUID,
        signals: ConnotationSignals,
        sourceValue: Any?,
        themeValues: Map<String, String?>,
    ): SentimentMetadata = when (
        val outcome = deterministicConnotationMapper.analyze(
            signals = signals,
            sourceValue = sourceValue,
            themeValues = themeValues,
            activeVersion = properties.deterministicCurrentVersion,
        )
    ) {
        is SentimentAnalysisOutcome.Success -> outcome.metadata
        is SentimentAnalysisOutcome.Failure -> {
            logger.warn {
                "DETERMINISTIC connotation analysis failed for entity=$entityId workspace=$workspaceId " +
                    "reason=${outcome.reason} message=${outcome.message}"
            }
            SentimentMetadata(
                status = ConnotationStatus.FAILED,
                analysisTier = AnalysisTier.DETERMINISTIC,
                analysisVersion = properties.deterministicCurrentVersion,
                analysisModel = "deterministic-connotation-${signals.sentimentScale.mappingType.name.lowercase()}-${properties.deterministicCurrentVersion}",
                analyzedAt = ZonedDateTime.now(),
            )
        }
    }
}
