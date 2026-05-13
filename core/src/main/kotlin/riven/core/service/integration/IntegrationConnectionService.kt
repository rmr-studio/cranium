package cranium.core.service.integration

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import cranium.core.entity.integration.IntegrationConnectionEntity
import cranium.core.enums.activity.Activity
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.integration.ConnectionStatus
import cranium.core.enums.util.OperationType
import cranium.core.exceptions.InvalidStateTransitionException
import cranium.core.exceptions.NangoApiException
import cranium.core.exceptions.RateLimitException
import cranium.core.exceptions.TransientNangoException
import cranium.core.repository.integration.IntegrationConnectionRepository
import cranium.core.repository.integration.IntegrationDefinitionRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.integration.materialization.TemplateMaterializationService
import cranium.core.util.ServiceUtil.findOrThrow
import java.util.*

/**
 * Service for managing integration connection lifecycle.
 *
 * Enforces the 8-state connection state machine with validation on all
 * status transitions. Provides CRUD operations with workspace security
 * and role-based access control.
 *
 * Connections are created exclusively by the Nango webhook handler
 * (via createOrReconnect) after successful OAuth completion.
 */
@Service
class IntegrationConnectionService(
    private val connectionRepository: IntegrationConnectionRepository,
    private val definitionRepository: IntegrationDefinitionRepository,
    private val nangoClientWrapper: NangoClientWrapper,
    private val templateMaterializationService: TemplateMaterializationService,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val transactionTemplate: TransactionTemplate,
    private val logger: KLogger
) {

    // ------ Public Read Operations ------

    /**
     * Get all connections for a workspace.
     *
     * @param workspaceId The workspace ID
     * @return List of connections
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getConnectionsByWorkspace(workspaceId: UUID): List<IntegrationConnectionEntity> {
        return connectionRepository.findByWorkspaceId(workspaceId)
    }

    /**
     * Get a specific workspace's connection to an integration.
     *
     * @param workspaceId The workspace ID
     * @param integrationId The integration definition ID
     * @return The connection, or null if not found
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getConnection(workspaceId: UUID, integrationId: UUID): IntegrationConnectionEntity? {
        return connectionRepository.findByWorkspaceIdAndIntegrationId(workspaceId, integrationId)
    }

    // ------ Public Mutations ------

    /**
     * Update a connection's status.
     *
     * Validates state transitions using ConnectionStatus.canTransitionTo().
     *
     * @param workspaceId The workspace ID
     * @param connectionId The connection ID
     * @param newStatus The new status
     * @param metadata Optional metadata to merge with existing metadata
     * @return The updated connection
     * @throws InvalidStateTransitionException if the transition is invalid
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun updateConnectionStatus(
        workspaceId: UUID,
        connectionId: UUID,
        newStatus: ConnectionStatus,
        metadata: Map<String, Any>? = null
    ): IntegrationConnectionEntity {
        val userId = authTokenService.getUserId()
        val connection = findOrThrow { connectionRepository.findById(connectionId) }
        require(connection.workspaceId == workspaceId) { "Connection does not belong to workspace" }

        val oldStatus = connection.status

        if (!oldStatus.canTransitionTo(newStatus)) {
            throw InvalidStateTransitionException(
                "Cannot transition from $oldStatus to $newStatus"
            )
        }

        connection.status = newStatus
        if (metadata != null) {
            connection.connectionMetadata = (connection.connectionMetadata ?: emptyMap()) + metadata
        }

        val saved = connectionRepository.save(connection).also {
            logger.info { "Updated connection $connectionId status: $oldStatus -> $newStatus" }
            logConnectionActivity(
                OperationType.UPDATE, userId, workspaceId, it,
                mapOf("oldStatus" to oldStatus.name, "newStatus" to newStatus.name)
            )
        }

        if (newStatus == ConnectionStatus.CONNECTED) {
            try {
                triggerMaterialization(workspaceId, saved.integrationId)
            } catch (e: Exception) {
                logger.error(e) {
                    "Materialization failed for workspace=$workspaceId, " +
                        "integration=${saved.integrationId}. Connection status remains CONNECTED."
                }
            }
        }

        return saved
    }

    /**
     * Disconnect an integration connection.
     *
     * Requires ADMIN role. Transitions to DISCONNECTING, calls Nango to delete
     * the connection, then transitions to DISCONNECTED. Handles Nango API
     * failures gracefully by still marking the connection as DISCONNECTED locally.
     *
     * Uses programmatic transaction management to avoid holding a DB transaction
     * open during the external Nango API call.
     *
     * @param workspaceId The workspace ID
     * @param connectionId The connection ID
     * @return The disconnected connection
     * @throws InvalidStateTransitionException if disconnection is not allowed from current state
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspaceRole(#workspaceId, T(cranium.core.enums.workspace.WorkspaceRoles).ADMIN)")
    fun disconnectConnection(workspaceId: UUID, connectionId: UUID): IntegrationConnectionEntity {
        val userId = authTokenService.getUserId()

        val nangoDetails = transitionToDisconnecting(workspaceId, connectionId)

        deleteNangoConnection(nangoDetails.first, nangoDetails.second)

        return transitionToDisconnected(workspaceId, connectionId, userId)
    }

    // ------ Private Helpers ------

    /**
     * Reconnects an existing connection by validating the state transition and updating its
     * status and Nango connection ID. Logs the actual previous status rather than hardcoding.
     */
    private fun reconnectConnection(
        connection: IntegrationConnectionEntity,
        nangoConnectionId: String,
        userId: UUID,
        workspaceId: UUID
    ): IntegrationConnectionEntity {
        val previousStatus = connection.status

        if (!previousStatus.canTransitionTo(ConnectionStatus.CONNECTED)) {
            logger.warn {
                "Cannot reconnect connection ${connection.id} from status $previousStatus — " +
                    "updating nangoConnectionId only"
            }
            if (connection.nangoConnectionId != nangoConnectionId) {
                connection.nangoConnectionId = nangoConnectionId
                return connectionRepository.save(connection)
            }
            return connection
        }

        connection.status = ConnectionStatus.CONNECTED
        connection.nangoConnectionId = nangoConnectionId
        return connectionRepository.save(connection).also {
            logger.info { "Reconnected integration connection ${connection.id} for workspace=$workspaceId" }
            logConnectionActivity(
                OperationType.UPDATE, userId, workspaceId, it,
                mapOf("previousStatus" to previousStatus.name, "newStatus" to ConnectionStatus.CONNECTED.name)
            )
        }
    }

    /**
     * Creates a new connection or reconnects an existing one.
     * Called by the webhook handler after Nango confirms successful OAuth.
     *
     * If the connection is already CONNECTED, handles idempotently by updating the
     * nangoConnectionId if changed.
     */
    internal fun createOrReconnect(
        workspaceId: UUID,
        integrationId: UUID,
        nangoConnectionId: String,
        userId: UUID
    ): IntegrationConnectionEntity {
        val existing = connectionRepository.findByWorkspaceIdAndIntegrationId(workspaceId, integrationId)

        if (existing != null) {
            return reconnectConnection(existing, nangoConnectionId, userId, workspaceId)
        }

        val connection = IntegrationConnectionEntity(
            workspaceId = workspaceId,
            integrationId = integrationId,
            nangoConnectionId = nangoConnectionId,
            status = ConnectionStatus.CONNECTED
        )

        return connectionRepository.save(connection).also {
            logger.info { "Created integration connection for workspace=$workspaceId, integration=$integrationId" }
            logConnectionActivity(OperationType.CREATE, userId, workspaceId, it)
        }
    }

    /**
     * Triggers template materialization when an integration connection becomes CONNECTED.
     * Creates workspace-scoped entity types and relationships from catalog definitions.
     */
    private fun triggerMaterialization(workspaceId: UUID, integrationId: UUID) {
        val integration = definitionRepository.findById(integrationId).orElse(null)
        if (integration != null) {
            val result = templateMaterializationService.materializeIntegrationTemplates(workspaceId, integration.slug, integrationId)
            logger.info {
                "Materialized integration templates for workspace=$workspaceId, integration=${integration.slug}: " +
                    "created=${result.entityTypesCreated}, restored=${result.entityTypesRestored}, " +
                    "relationships=${result.relationshipsCreated}"
            }
        }
    }

    /**
     * Transitions a connection to DISCONNECTING state in its own transaction,
     * making the intermediate state visible to other transactions.
     *
     * @return Pair of (nangoProviderKey, nangoConnectionId) for the Nango API call
     */
    private fun transitionToDisconnecting(workspaceId: UUID, connectionId: UUID): Pair<String, String> {
        return transactionTemplate.execute { _ ->
            val connection = findOrThrow { connectionRepository.findById(connectionId) }
            require(connection.workspaceId == workspaceId) { "Connection does not belong to workspace" }

            if (!connection.status.canTransitionTo(ConnectionStatus.DISCONNECTING)) {
                throw InvalidStateTransitionException(
                    "Cannot disconnect from status ${connection.status}"
                )
            }

            connection.status = ConnectionStatus.DISCONNECTING
            connectionRepository.save(connection)

            val definition = findOrThrow { definitionRepository.findById(connection.integrationId) }
            definition.nangoProviderKey to connection.nangoConnectionId
        }!!
    }

    /**
     * Calls Nango to delete the external connection. Handles failures gracefully
     * since the local disconnect should succeed even if Nango cleanup fails.
     */
    private fun deleteNangoConnection(providerConfigKey: String, nangoConnectionId: String) {
        try {
            nangoClientWrapper.deleteConnection(
                providerConfigKey = providerConfigKey,
                connectionId = nangoConnectionId
            )
        } catch (e: NangoApiException) {
            logger.error { "Nango API error during connection deletion: ${e.message} (status=${e.statusCode})" }
        } catch (e: RateLimitException) {
            logger.error { "Rate limited during Nango connection deletion: ${e.message}" }
        } catch (e: TransientNangoException) {
            logger.error { "Transient Nango error during connection deletion: ${e.message}" }
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during Nango connection deletion" }
        }
    }

    /**
     * Transitions a connection to DISCONNECTED state in its own transaction.
     */
    private fun transitionToDisconnected(
        workspaceId: UUID,
        connectionId: UUID,
        userId: UUID
    ): IntegrationConnectionEntity {
        return transactionTemplate.execute { _ ->
            val connection = findOrThrow { connectionRepository.findById(connectionId) }
            connection.status = ConnectionStatus.DISCONNECTED
            connectionRepository.save(connection).also {
                logger.info { "Disconnected integration connection $connectionId for workspace=$workspaceId" }
                logConnectionActivity(OperationType.DELETE, userId, workspaceId, it)
            }
        }!!
    }

    private fun logConnectionActivity(
        operation: OperationType,
        userId: UUID,
        workspaceId: UUID,
        connection: IntegrationConnectionEntity,
        extraDetails: Map<String, Any> = emptyMap()
    ) {
        val details = mapOf(
            "integrationId" to connection.integrationId.toString(),
            "status" to connection.status.name
        ) + extraDetails

        activityService.logActivity(
            activity = Activity.INTEGRATION_CONNECTION,
            operation = operation,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.INTEGRATION_CONNECTION,
            entityId = connection.id,
            details = details
        )
    }
}
