package riven.core.service.connotation

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import riven.core.configuration.properties.ConnotationAnalysisConfigurationProperties
import riven.core.enums.activity.Activity
import riven.core.enums.connotation.ConnotationMetadataType
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.models.connotation.AnalysisTier
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import java.util.UUID

/**
 * Admin operations for connotation snapshot reconciliation.
 *
 * In Phase B the only supported metadata type is SENTIMENT and the only supported tier is
 * DETERMINISTIC. The op enqueues every entity in the workspace whose persisted SENTIMENT
 * analysis version differs from the active config value, letting the normal enrichment
 * dispatcher pick them up. Last-write-wins on the resulting snapshot updates.
 *
 * Synchronous — no Temporal workflow. Matches the precedent in `SchemaReconciliationService`
 * for admin-style single-call reconciliation ops.
 */
@Service
class ConnotationAdminService(
    private val executionQueueRepository: ExecutionQueueRepository,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val properties: ConnotationAnalysisConfigurationProperties,
    private val logger: KLogger,
) {

    /**
     * Enqueue every entity in the workspace whose persisted analysis version for [metadataType]
     * differs from the configured current version, returning the number of rows enqueued.
     *
     * @param metadataType Metadata type to reanalyze (Phase B: SENTIMENT only).
     * @param tier Analysis tier to use (Phase B: DETERMINISTIC only).
     * @param workspaceId Workspace owning the entities.
     * @return Number of ENRICHMENT queue rows inserted.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun reanalyzeWhereVersionMismatch(
        metadataType: ConnotationMetadataType,
        tier: AnalysisTier,
        workspaceId: UUID,
    ): Int {
        require(metadataType == ConnotationMetadataType.SENTIMENT) {
            "Phase B only supports SENTIMENT reanalyze (got $metadataType)"
        }
        require(tier == AnalysisTier.DETERMINISTIC) {
            "Phase B only supports DETERMINISTIC reanalyze (got $tier)"
        }

        val userId = authTokenService.getUserId()
        val currentVersion = properties.deterministicCurrentVersion

        val enqueued = executionQueueRepository.enqueueByMetadataVersionMismatch(
            metadataKey = metadataType.name,
            currentVersion = currentVersion,
            workspaceId = workspaceId,
        )

        logger.info {
            "Reanalyze enqueued $enqueued ENRICHMENT items for metadataType=$metadataType tier=$tier " +
                "workspace=$workspaceId currentVersion=$currentVersion"
        }
        activityService.log(
            activity = Activity.ENTITY_CONNOTATION,
            operation = OperationType.REANALYZE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_CONNOTATION,
            entityId = null,
            "metadataType" to metadataType.name,
            "tier" to tier.name,
            "currentVersion" to currentVersion,
            "enqueued" to enqueued,
        )
        return enqueued
    }
}
