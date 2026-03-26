package riven.core.service.integration

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.integration.IntegrationDefinitionEntity
import riven.core.entity.integration.WorkspaceIntegrationInstallationEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.exceptions.NotFoundException
import riven.core.models.common.markDeleted
import riven.core.models.integration.IntegrationSoftDeleteResult
import riven.core.models.request.integration.DisableIntegrationRequest
import riven.core.models.response.integration.IntegrationDisableResponse
import riven.core.repository.integration.IntegrationDefinitionRepository
import riven.core.repository.integration.WorkspaceIntegrationInstallationRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.type.EntityTypeService
import riven.core.util.ServiceUtil.findOrThrow
import java.time.ZonedDateTime
import java.util.*

/**
 * Orchestrates the disable lifecycle for workspace integrations.
 *
 * Disable: soft-deletes integration entity types and relationships, disconnects
 * the Nango connection, snapshots lastSyncedAt for gap recovery, and soft-deletes
 * the installation record.
 *
 * Integration enablement (connection creation) is now webhook-driven — connections
 * are created by the Nango auth webhook handler after successful OAuth completion.
 */
@Service
class IntegrationEnablementService(
    private val installationRepository: WorkspaceIntegrationInstallationRepository,
    private val definitionRepository: IntegrationDefinitionRepository,
    private val connectionService: IntegrationConnectionService,
    private val entityTypeService: EntityTypeService,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val logger: KLogger
) {

    // ------ Public Mutations ------

    /**
     * Disables an integration for a workspace.
     *
     * Soft-deletes all entity types and relationships created by the integration,
     * disconnects the Nango connection, and soft-deletes the installation record.
     * Snapshots lastSyncedAt before deletion for gap recovery on re-enable.
     *
     * @param workspaceId the workspace to disable the integration in
     * @param request contains integrationDefinitionId
     * @return disable response with soft-delete counts
     * @throws NotFoundException if the integration is not enabled or the definition does not exist
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspaceRole(#workspaceId, T(riven.core.enums.workspace.WorkspaceRoles).ADMIN)")
    fun disableIntegration(workspaceId: UUID, request: DisableIntegrationRequest): IntegrationDisableResponse {
        val (definition, installation, deleteResult) = disableIntegrationTransactional(workspaceId, request)

        disconnectIfConnected(workspaceId, request.integrationDefinitionId)

        return IntegrationDisableResponse(
            integrationDefinitionId = request.integrationDefinitionId,
            integrationName = definition.name,
            entityTypesSoftDeleted = deleteResult.entityTypesSoftDeleted,
            relationshipsSoftDeleted = deleteResult.relationshipsSoftDeleted
        )
    }

    /**
     * Performs the transactional DB mutations for disabling an integration:
     * soft-deletes entity types/relationships and marks the installation as deleted.
     * Separated so the Nango disconnect can run outside the transaction.
     */
    @Transactional
    fun disableIntegrationTransactional(workspaceId: UUID, request: DisableIntegrationRequest): DisableTransactionResult {
        val userId = authTokenService.getUserId()
        val definition = findOrThrow { definitionRepository.findById(request.integrationDefinitionId) }

        val installation = installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(
            workspaceId, request.integrationDefinitionId
        ) ?: throw NotFoundException("Integration '${definition.slug}' is not enabled in this workspace")

        val deleteResult = entityTypeService.softDeleteByIntegration(workspaceId, request.integrationDefinitionId)

        installation.lastSyncedAt = ZonedDateTime.now()
        installation.markDeleted()
        installationRepository.save(installation)

        logDisableActivity(userId, workspaceId, requireNotNull(installation.id), definition.slug, deleteResult.entityTypesSoftDeleted)

        return DisableTransactionResult(definition, installation, deleteResult)
    }

    // ------ Private Helpers ------

    /**
     * Disconnects the Nango connection if one exists. Catches exceptions gracefully
     * since the disable operation should succeed even if Nango cleanup fails.
     */
    private fun disconnectIfConnected(workspaceId: UUID, integrationDefinitionId: UUID) {
        try {
            val connection = connectionService.getConnection(workspaceId, integrationDefinitionId)
            if (connection != null) {
                connectionService.disconnectConnection(workspaceId, connection.id!!)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to disconnect integration connection for workspace=$workspaceId, integration=$integrationDefinitionId" }
        }
    }

    private fun logDisableActivity(
        userId: UUID,
        workspaceId: UUID,
        installationId: UUID,
        integrationSlug: String,
        entityTypesSoftDeleted: Int
    ) {
        activityService.logActivity(
            activity = Activity.INTEGRATION_ENABLEMENT,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.INTEGRATION_INSTALLATION,
            entityId = installationId,
            details = mapOf(
                "integrationSlug" to integrationSlug,
                "entityTypesSoftDeleted" to entityTypesSoftDeleted
            )
        )
    }

    // ------ Internal Data Classes ------

    data class DisableTransactionResult(
        val definition: IntegrationDefinitionEntity,
        val installation: WorkspaceIntegrationInstallationEntity,
        val deleteResult: IntegrationSoftDeleteResult
    )
}
