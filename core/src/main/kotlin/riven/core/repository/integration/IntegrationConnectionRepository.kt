package cranium.core.repository.integration

import org.springframework.data.jpa.repository.JpaRepository
import cranium.core.entity.integration.IntegrationConnectionEntity
import cranium.core.enums.integration.ConnectionStatus
import java.util.*

/**
 * Repository for integration connections (workspace-scoped).
 *
 * Provides queries for finding connections by workspace, integration, and status.
 * RLS policies ensure workspace isolation at the database level.
 */
interface IntegrationConnectionRepository : JpaRepository<IntegrationConnectionEntity, UUID> {

    /**
     * Find all connections for a workspace.
     */
    fun findByWorkspaceId(workspaceId: UUID): List<IntegrationConnectionEntity>

    /**
     * Find a specific workspace's connection to an integration.
     */
    fun findByWorkspaceIdAndIntegrationId(
        workspaceId: UUID,
        integrationId: UUID
    ): IntegrationConnectionEntity?

    /**
     * Find all connections for a workspace with a specific status.
     */
    fun findByWorkspaceIdAndStatus(
        workspaceId: UUID,
        status: ConnectionStatus
    ): List<IntegrationConnectionEntity>

    /**
     * Find a connection by its Nango connection ID.
     * Used by the webhook handler to look up connections from Nango event payloads.
     */
    fun findByNangoConnectionId(nangoConnectionId: String): IntegrationConnectionEntity?
}
