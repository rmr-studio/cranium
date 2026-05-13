package cranium.core.models.integration.materialization

import cranium.core.models.response.integration.EnabledEntityTypeSummary

/**
 * Result of materializing integration templates into workspace-scoped entity types.
 */
data class MaterializationResult(
    val entityTypesCreated: Int,
    val entityTypesRestored: Int,
    val relationshipsCreated: Int,
    val integrationSlug: String,
    val entityTypes: List<EnabledEntityTypeSummary> = emptyList()
)
