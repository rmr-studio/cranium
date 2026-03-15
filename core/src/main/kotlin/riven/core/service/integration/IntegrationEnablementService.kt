package riven.core.service.integration

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.integration.WorkspaceIntegrationInstallationEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.exceptions.NotFoundException
import riven.core.models.common.markDeleted
import riven.core.models.integration.SyncConfiguration
import riven.core.models.request.integration.DisableIntegrationRequest
import riven.core.models.request.integration.EnableIntegrationRequest
import riven.core.models.response.integration.IntegrationDisableResponse
import riven.core.models.response.integration.IntegrationEnablementResponse
import riven.core.entity.integration.IntegrationDefinitionEntity
import riven.core.models.integration.IntegrationSoftDeleteResult
import riven.core.repository.integration.IntegrationDefinitionRepository
import riven.core.repository.integration.WorkspaceIntegrationInstallationRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.type.EntityTypeService
import riven.core.service.integration.materialization.TemplateMaterializationService
import riven.core.util.ServiceUtil.findOrThrow
import java.time.ZonedDateTime
import java.util.*

/**
 * Orchestrates the enable/disable lifecycle for workspace integrations.
 *
 * Enable: validates definition, checks idempotency, enables Nango connection,
 * materializes catalog templates, and tracks the installation.
 *
 * Disable: soft-deletes integration entity types and relationships, disconnects
 * the Nango connection, snapshots lastSyncedAt for gap recovery, and soft-deletes
 * the installation record.
 */
@Service
class IntegrationEnablementService(
    private val installationRepository: WorkspaceIntegrationInstallationRepository,
    private val definitionRepository: IntegrationDefinitionRepository,
    private val connectionService: IntegrationConnectionService,
    private val materializationService: TemplateMaterializationService,
    private val entityTypeService: EntityTypeService,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val logger: KLogger
) {

    // ------ Public Mutations ------

    /**
     * Enables an integration for a workspace.
     *
     * Idempotent: returns existing result if already enabled. Restores a soft-deleted
     * installation on re-enable, preserving lastSyncedAt for gap recovery.
     *
     * @param workspaceId the workspace to enable the integration in
     * @param request contains integrationDefinitionId, nangoConnectionId, and optional syncConfig
     * @return enablement response with materialization counts
     * @throws NotFoundException if the integration definition does not exist
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspaceRole(#workspaceId, T(riven.core.enums.workspace.WorkspaceRoles).ADMIN)")
    fun enableIntegration(workspaceId: UUID, request: EnableIntegrationRequest): IntegrationEnablementResponse {
        val userId = authTokenService.getUserId()
        val definition = findOrThrow { definitionRepository.findById(request.integrationDefinitionId) }
        val syncConfig = request.syncConfig ?: SyncConfiguration()

        val existing = installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(
            workspaceId, request.integrationDefinitionId
        )
        if (existing != null) {
            return buildAlreadyEnabledResponse(request.integrationDefinitionId, definition.name, definition.slug, existing.syncConfig)
        }

        val softDeleted = installationRepository.findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId(
            workspaceId, request.integrationDefinitionId
        )

        connectionService.enableConnection(workspaceId, request.integrationDefinitionId, request.nangoConnectionId)

        val materializationResult = materializationService.materializeIntegrationTemplates(
            workspaceId, definition.slug, request.integrationDefinitionId
        )

        val installation = trackInstallation(softDeleted, workspaceId, request.integrationDefinitionId, definition.slug, userId, syncConfig)

        logEnableActivity(userId, workspaceId, installation.id!!, definition.slug, materializationResult.entityTypesCreated)

        return IntegrationEnablementResponse(
            integrationDefinitionId = request.integrationDefinitionId,
            integrationName = definition.name,
            integrationSlug = definition.slug,
            entityTypesCreated = materializationResult.entityTypesCreated,
            entityTypesRestored = materializationResult.entityTypesRestored,
            relationshipsCreated = materializationResult.relationshipsCreated,
            entityTypes = materializationResult.entityTypes,
            syncConfig = syncConfig
        )
    }

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
     * Tracks an installation by restoring a soft-deleted record or creating a new one.
     */
    private fun trackInstallation(
        softDeleted: WorkspaceIntegrationInstallationEntity?,
        workspaceId: UUID,
        integrationDefinitionId: UUID,
        manifestKey: String,
        userId: UUID,
        syncConfig: SyncConfiguration
    ): WorkspaceIntegrationInstallationEntity {
        if (softDeleted != null) {
            softDeleted.deleted = false
            softDeleted.deletedAt = null
            softDeleted.syncConfig = syncConfig
            return installationRepository.save(softDeleted)
        }

        val installation = WorkspaceIntegrationInstallationEntity(
            workspaceId = workspaceId,
            integrationDefinitionId = integrationDefinitionId,
            manifestKey = manifestKey,
            installedBy = userId,
            syncConfig = syncConfig
        )
        return installationRepository.save(installation)
    }

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

    /**
     * Builds an idempotent response when the integration is already enabled.
     */
    private fun buildAlreadyEnabledResponse(
        integrationDefinitionId: UUID,
        name: String,
        slug: String,
        syncConfig: SyncConfiguration
    ): IntegrationEnablementResponse {
        return IntegrationEnablementResponse(
            integrationDefinitionId = integrationDefinitionId,
            integrationName = name,
            integrationSlug = slug,
            entityTypesCreated = 0,
            entityTypesRestored = 0,
            relationshipsCreated = 0,
            entityTypes = emptyList(),
            syncConfig = syncConfig
        )
    }

    private fun logEnableActivity(
        userId: UUID,
        workspaceId: UUID,
        installationId: UUID,
        integrationSlug: String,
        entityTypesCreated: Int
    ) {
        activityService.logActivity(
            activity = Activity.INTEGRATION_ENABLEMENT,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.INTEGRATION_INSTALLATION,
            entityId = installationId,
            details = mapOf(
                "integrationSlug" to integrationSlug,
                "entityTypesCreated" to entityTypesCreated
            )
        )
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
