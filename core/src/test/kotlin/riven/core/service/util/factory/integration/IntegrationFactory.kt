package riven.core.service.util.factory.integration

import riven.core.entity.integration.IntegrationConnectionEntity
import riven.core.entity.integration.IntegrationDefinitionEntity
import riven.core.entity.integration.IntegrationSyncStateEntity
import riven.core.entity.integration.WorkspaceIntegrationInstallationEntity
import riven.core.enums.integration.ConnectionStatus
import riven.core.enums.integration.InstallationStatus
import riven.core.enums.integration.IntegrationCategory
import riven.core.enums.integration.SyncStatus
import riven.core.models.integration.SyncConfiguration
import java.time.ZonedDateTime
import java.util.*

object IntegrationFactory {

    /**
     * Creates an IntegrationSyncStateEntity with all fields as parameters and sensible defaults.
     */
    fun createIntegrationSyncState(
        id: UUID? = null,
        integrationConnectionId: UUID = UUID.randomUUID(),
        entityTypeId: UUID = UUID.randomUUID(),
        status: SyncStatus = SyncStatus.PENDING,
        lastCursor: String? = null,
        consecutiveFailureCount: Int = 0,
        lastErrorMessage: String? = null,
        lastRecordsSynced: Int? = null,
        lastRecordsFailed: Int? = null,
    ): IntegrationSyncStateEntity {
        return IntegrationSyncStateEntity(
            id = id,
            integrationConnectionId = integrationConnectionId,
            entityTypeId = entityTypeId,
            status = status,
            lastCursor = lastCursor,
            consecutiveFailureCount = consecutiveFailureCount,
            lastErrorMessage = lastErrorMessage,
            lastRecordsSynced = lastRecordsSynced,
            lastRecordsFailed = lastRecordsFailed,
        )
    }

    /**
     * Creates a WorkspaceIntegrationInstallationEntity with all fields as parameters and sensible defaults.
     */
    fun createWorkspaceIntegrationInstallation(
        id: UUID? = null,
        workspaceId: UUID = UUID.randomUUID(),
        integrationDefinitionId: UUID = UUID.randomUUID(),
        manifestKey: String = "test-integration",
        installedBy: UUID = UUID.randomUUID(),
        installedAt: ZonedDateTime = ZonedDateTime.now(),
        syncConfig: SyncConfiguration = SyncConfiguration(),
        lastSyncedAt: ZonedDateTime? = null,
        status: InstallationStatus = InstallationStatus.ACTIVE,
        deleted: Boolean = false,
        deletedAt: ZonedDateTime? = null,
    ): WorkspaceIntegrationInstallationEntity {
        return WorkspaceIntegrationInstallationEntity(
            id = id,
            workspaceId = workspaceId,
            integrationDefinitionId = integrationDefinitionId,
            manifestKey = manifestKey,
            installedBy = installedBy,
            installedAt = installedAt,
            syncConfig = syncConfig,
            lastSyncedAt = lastSyncedAt,
            status = status,
            deleted = deleted,
            deletedAt = deletedAt,
        )
    }

    /**
     * Creates an IntegrationDefinitionEntity with all fields as parameters and sensible defaults.
     */
    fun createIntegrationDefinition(
        id: UUID? = null,
        slug: String = "test-integration",
        name: String = "Test Integration",
        iconUrl: String? = null,
        description: String? = null,
        category: IntegrationCategory = IntegrationCategory.CRM,
        nangoProviderKey: String = "test-provider",
        capabilities: Map<String, Any> = emptyMap(),
        syncConfig: Map<String, Any> = emptyMap(),
        authConfig: Map<String, Any> = emptyMap(),
        stale: Boolean = false,
    ): IntegrationDefinitionEntity {
        return IntegrationDefinitionEntity(
            id = id,
            slug = slug,
            name = name,
            iconUrl = iconUrl,
            description = description,
            category = category,
            nangoProviderKey = nangoProviderKey,
            capabilities = capabilities,
            syncConfig = syncConfig,
            authConfig = authConfig,
            stale = stale,
        )
    }

    /**
     * Creates an IntegrationConnectionEntity with all fields as parameters and sensible defaults.
     */
    fun createIntegrationConnection(
        id: UUID? = null,
        workspaceId: UUID = UUID.randomUUID(),
        integrationId: UUID = UUID.randomUUID(),
        nangoConnectionId: String = "nango-conn-${UUID.randomUUID()}",
        status: ConnectionStatus = ConnectionStatus.CONNECTED,
        connectionMetadata: Map<String, Any>? = null,
    ): IntegrationConnectionEntity {
        return IntegrationConnectionEntity(
            id = id,
            workspaceId = workspaceId,
            integrationId = integrationId,
            nangoConnectionId = nangoConnectionId,
            status = status,
            connectionMetadata = connectionMetadata,
        )
    }
}
