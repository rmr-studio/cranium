package riven.core.models.integration.sync

import java.util.UUID

/**
 * Input DTO for the IntegrationSyncWorkflow Temporal workflow.
 *
 * Contains all context needed to execute a full sync for a single connection+model combination.
 * Passed from the Nango sync webhook via NangoWebhookService into the workflow.
 *
 * @property connectionId Internal UUID of the IntegrationConnectionEntity
 * @property workspaceId Workspace that owns this connection
 * @property integrationId IntegrationDefinitionEntity UUID
 * @property nangoConnectionId Nango's opaque connection identifier (used for API calls)
 * @property providerConfigKey Nango provider config key (e.g. "hubspot")
 * @property model The data model being synced (e.g. "contacts")
 * @property modifiedAfter ISO-8601 timestamp for incremental sync cursor (null for full sync)
 */
data class IntegrationSyncWorkflowInput(
    val connectionId: UUID,
    val workspaceId: UUID,
    val integrationId: UUID,
    val nangoConnectionId: String,
    val providerConfigKey: String,
    val model: String,
    val modifiedAfter: String? = null
)
