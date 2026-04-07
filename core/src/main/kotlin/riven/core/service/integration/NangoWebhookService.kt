package riven.core.service.integration

import io.github.oshai.kotlinlogging.KLogger
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowExecutionAlreadyStarted
import io.temporal.client.WorkflowOptions
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import riven.core.configuration.workflow.TemporalWorkerConfiguration
import riven.core.entity.integration.IntegrationConnectionEntity
import riven.core.entity.integration.WorkspaceIntegrationInstallationEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.integration.ConnectionStatus
import riven.core.enums.integration.InstallationStatus
import riven.core.enums.util.OperationType
import riven.core.models.integration.NangoWebhookPayload
import riven.core.models.integration.sync.IntegrationSyncWorkflowInput
import riven.core.repository.integration.IntegrationConnectionRepository
import riven.core.repository.integration.IntegrationDefinitionRepository
import riven.core.repository.integration.WorkspaceIntegrationInstallationRepository
import riven.core.service.activity.ActivityService
import riven.core.service.integration.materialization.TemplateMaterializationService
import riven.core.service.integration.sync.IntegrationSyncWorkflow
import java.util.*

/**
 * Handles inbound Nango webhook events.
 *
 * Routes auth events (successful OAuth completion) and sync events (sync execution results)
 * to their respective handlers. All exceptions are caught internally — the controller
 * must always return 200 to Nango regardless of processing outcome.
 */
@Service
class NangoWebhookService(
    private val connectionRepository: IntegrationConnectionRepository,
    private val definitionRepository: IntegrationDefinitionRepository,
    private val installationRepository: WorkspaceIntegrationInstallationRepository,
    private val templateMaterializationService: TemplateMaterializationService,
    private val activityService: ActivityService,
    private val transactionTemplate: TransactionTemplate,
    private val workflowClient: WorkflowClient,
    private val logger: KLogger
) {

    // ------ Public Entry Points ------

    /**
     * Routes an inbound Nango webhook payload to the correct handler based on event type.
     */
    fun handleWebhook(payload: NangoWebhookPayload) {
        try {
            when (payload.type) {
                "auth" -> handleAuthEvent(payload)
                "sync" -> handleSyncEvent(payload)
                else -> logger.info { "Ignoring unknown Nango webhook type: ${payload.type}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error processing webhook type=${payload.type} — swallowing to return 200 to Nango" }
        }
    }

    // ------ Private Event Handlers ------

    /**
     * Handles auth webhook events fired by Nango after a successful OAuth completion.
     *
     * Extracts workspace context from tags, creates or reconnects the integration connection,
     * creates or restores the installation record, and triggers template materialization.
     * All failures are logged and swallowed — Nango must receive a 200 response.
     *
     * ## Nango Tag Field Mapping
     *
     * Nango's webhook tags have typed field names (endUserId, organizationId, endUserEmail)
     * but we repurpose them to carry Riven-specific UUIDs because Nango doesn't support
     * custom metadata fields. The mapping is:
     *
     * | Nango Tag Field   | Riven Value              | Rationale                           |
     * |-------------------|--------------------------|-------------------------------------|
     * | endUserId         | userId (UUID)            | Closest semantic match              |
     * | organizationId    | workspaceId (UUID)       | Org ~ workspace                     |
     * | endUserEmail      | integrationDefinitionId  | Abused field, no better alternative |
     *
     * These tags are set when the frontend calls `nango.openConnectUI()` with the workspace
     * context. If Nango ever adds custom metadata fields or validates endUserEmail as an
     * email format, this mapping will need to change.
     *
     * @see NangoWebhookPayload.tags for the tag structure
     */
    private fun handleAuthEvent(payload: NangoWebhookPayload) {
        val tags = payload.tags
        if (tags == null) {
            logger.error { "Auth webhook missing tags — cannot identify workspace/user context" }
            return
        }

        val userId = parseUuidTag("userId", tags.endUserId) ?: return
        val workspaceId = parseUuidTag("workspaceId", tags.organizationId) ?: return
        val integrationDefinitionId = parseUuidTag("integrationDefinitionId", tags.endUserEmail) ?: return

        val nangoConnectionId = payload.connectionId
        if (nangoConnectionId == null) {
            logger.error { "Auth webhook missing connectionId for workspace=$workspaceId" }
            return
        }

        if (payload.success != true) {
            logger.warn { "Auth webhook received with success=${payload.success} for workspace=$workspaceId — skipping" }
            return
        }

        handleAuthWebhookTransaction(workspaceId, integrationDefinitionId, userId, nangoConnectionId)
    }

    /**
     * Transactional handler that creates or reconnects the connection and installation,
     * then triggers materialization. On materialization failure, sets installation to FAILED
     * and commits — the connection is preserved in CONNECTED state.
     *
     * Uses programmatic transaction management via TransactionTemplate because Spring AOP
     * proxies cannot intercept private method calls within the same bean.
     */
    private fun handleAuthWebhookTransaction(
        workspaceId: UUID,
        integrationDefinitionId: UUID,
        userId: UUID,
        nangoConnectionId: String
    ) {
        transactionTemplate.execute { _ ->
            val definition = definitionRepository.findById(integrationDefinitionId).orElse(null)
            if (definition == null) {
                logger.error { "Auth webhook: integration definition $integrationDefinitionId not found — cannot process connection" }
                return@execute
            }

            val connection = createOrReconnectConnection(workspaceId, integrationDefinitionId, nangoConnectionId, userId)
            val installation = findOrCreateInstallation(workspaceId, integrationDefinitionId, definition.slug, userId)

            triggerMaterializationWithFallback(workspaceId, definition.slug, integrationDefinitionId, installation)

            logConnectionActivity(OperationType.CREATE, userId, workspaceId, connection)
        }
    }

    /**
     * Handles sync webhook events by dispatching a Temporal workflow for the given connection+model.
     *
     * Uses a deterministic workflow ID `sync-{connectionId}-{model}` so that duplicate
     * Nango webhook deliveries for the same in-progress sync are silently ignored.
     * [WorkflowExecutionAlreadyStarted] is the expected dedup signal and is not an error.
     */
    private fun handleSyncEvent(payload: NangoWebhookPayload) {
        val nangoConnectionId = payload.connectionId
        val model = payload.model

        if (nangoConnectionId == null) {
            logger.warn { "Sync webhook missing connectionId — skipping dispatch" }
            return
        }
        if (model == null) {
            logger.warn { "Sync webhook missing model — skipping dispatch" }
            return
        }

        val connection = connectionRepository.findByNangoConnectionId(nangoConnectionId)
        if (connection == null) {
            logger.warn { "Sync webhook for unknown nango connection '$nangoConnectionId' — skipping dispatch" }
            return
        }

        val connectionId = requireNotNull(connection.id) { "IntegrationConnectionEntity id must not be null" }
        val workflowId = "sync-$connectionId-$model"

        val stub = workflowClient.newWorkflowStub(
            IntegrationSyncWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfiguration.INTEGRATION_SYNC_QUEUE)
                .build()
        )

        try {
            WorkflowClient.start { stub.execute(
                IntegrationSyncWorkflowInput(
                    connectionId = connectionId,
                    workspaceId = connection.workspaceId,
                    integrationId = connection.integrationId,
                    nangoConnectionId = nangoConnectionId,
                    providerConfigKey = payload.providerConfigKey ?: "",
                    model = model,
                    modifiedAfter = payload.modifiedAfter
                )
            ) }
            logger.info { "Dispatched sync workflow $workflowId for connection=$connectionId, model=$model" }
        } catch (e: WorkflowExecutionAlreadyStarted) {
            logger.info { "Sync workflow $workflowId already running — ignoring duplicate webhook" }
        }
    }

    // ------ Private Helpers ------

    /**
     * Creates a new connection or reconnects an existing one based on the current connection state.
     */
    private fun createOrReconnectConnection(
        workspaceId: UUID,
        integrationDefinitionId: UUID,
        nangoConnectionId: String,
        userId: UUID
    ): IntegrationConnectionEntity {
        val existing = connectionRepository.findByWorkspaceIdAndIntegrationId(workspaceId, integrationDefinitionId)

        return when {
            existing == null -> {
                val connection = IntegrationConnectionEntity(
                    workspaceId = workspaceId,
                    integrationId = integrationDefinitionId,
                    nangoConnectionId = nangoConnectionId,
                    status = ConnectionStatus.CONNECTED
                )
                connectionRepository.save(connection).also {
                    logger.info { "Created new connection for workspace=$workspaceId, integration=$integrationDefinitionId" }
                }
            }
            existing.status == ConnectionStatus.DISCONNECTED || existing.status == ConnectionStatus.FAILED -> {
                existing.status = ConnectionStatus.CONNECTED
                existing.nangoConnectionId = nangoConnectionId
                connectionRepository.save(existing).also {
                    logger.info { "Reconnected connection ${existing.id} for workspace=$workspaceId (was ${existing.status})" }
                }
            }
            existing.status == ConnectionStatus.CONNECTED -> {
                if (existing.nangoConnectionId != nangoConnectionId) {
                    existing.nangoConnectionId = nangoConnectionId
                    connectionRepository.save(existing)
                } else {
                    existing
                }.also {
                    logger.info { "Idempotent auth webhook — connection already CONNECTED for workspace=$workspaceId" }
                }
            }
            else -> {
                logger.warn {
                    "Auth webhook for connection in unexpected status ${existing.status} for workspace=$workspaceId — updating nangoConnectionId and continuing"
                }
                existing.nangoConnectionId = nangoConnectionId
                connectionRepository.save(existing)
            }
        }
    }

    /**
     * Finds or creates a workspace integration installation record.
     * Restores soft-deleted records if found; creates new otherwise.
     */
    private fun findOrCreateInstallation(
        workspaceId: UUID,
        integrationDefinitionId: UUID,
        slug: String,
        userId: UUID
    ): WorkspaceIntegrationInstallationEntity {
        val existing = installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(workspaceId, integrationDefinitionId)
        if (existing != null) {
            existing.status = InstallationStatus.ACTIVE
            return installationRepository.save(existing)
        }

        val softDeleted = installationRepository.findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId(workspaceId, integrationDefinitionId)
        if (softDeleted != null) {
            softDeleted.deleted = false
            softDeleted.deletedAt = null
            softDeleted.status = InstallationStatus.ACTIVE
            return installationRepository.save(softDeleted).also {
                logger.info { "Restored soft-deleted installation for workspace=$workspaceId, integration=$integrationDefinitionId" }
            }
        }

        val newInstallation = WorkspaceIntegrationInstallationEntity(
            workspaceId = workspaceId,
            integrationDefinitionId = integrationDefinitionId,
            manifestKey = slug,
            installedBy = userId,
            status = InstallationStatus.ACTIVE
        )
        return installationRepository.save(newInstallation).also {
            logger.info { "Created new installation for workspace=$workspaceId, integration=$integrationDefinitionId" }
        }
    }

    /**
     * Triggers template materialization. On failure, sets installation status to FAILED
     * and saves — does NOT rethrow, so the transaction commits with the FAILED status
     * while preserving the connection in CONNECTED state.
     */
    private fun triggerMaterializationWithFallback(
        workspaceId: UUID,
        integrationSlug: String,
        integrationDefinitionId: UUID,
        installation: WorkspaceIntegrationInstallationEntity
    ) {
        try {
            templateMaterializationService.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)
            logger.info { "Materialization succeeded for workspace=$workspaceId, integration=$integrationSlug" }
        } catch (e: Exception) {
            logger.error(e) { "Materialization failed for workspace=$workspaceId, integration=$integrationSlug — marking installation as FAILED" }
            installation.status = InstallationStatus.FAILED
            installationRepository.save(installation)
            // Do NOT rethrow — transaction commits with FAILED status, connection preserved
        }
    }

    /**
     * Parses a UUID from a tag string value. Logs error and returns null on any parse failure.
     */
    private fun parseUuidTag(fieldName: String, value: String?): UUID? {
        if (value == null) {
            logger.error { "Auth webhook missing required tag field: $fieldName" }
            return null
        }
        return try {
            UUID.fromString(value)
        } catch (e: IllegalArgumentException) {
            logger.error { "Auth webhook has invalid UUID in tag field $fieldName (value redacted)" }
            null
        }
    }

    private fun logConnectionActivity(
        operation: OperationType,
        userId: UUID,
        workspaceId: UUID,
        connection: IntegrationConnectionEntity
    ) {
        activityService.logActivity(
            activity = Activity.INTEGRATION_CONNECTION,
            operation = operation,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.INTEGRATION_CONNECTION,
            entityId = connection.id,
            details = mapOf(
                "integrationId" to connection.integrationId.toString(),
                "status" to connection.status.name
            )
        )
    }
}
