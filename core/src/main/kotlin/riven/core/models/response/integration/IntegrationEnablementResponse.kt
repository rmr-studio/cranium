package riven.core.models.response.integration

import riven.core.models.integration.SyncConfiguration
import java.util.*

data class IntegrationEnablementResponse(
    val integrationDefinitionId: UUID,
    val integrationName: String,
    val integrationSlug: String,
    val entityTypesCreated: Int,
    val entityTypesRestored: Int,
    val relationshipsCreated: Int,
    val entityTypes: List<EnabledEntityTypeSummary>,
    val syncConfig: SyncConfiguration
)

data class EnabledEntityTypeSummary(
    val id: UUID,
    val key: String,
    val displayName: String,
    val attributeCount: Int
)
