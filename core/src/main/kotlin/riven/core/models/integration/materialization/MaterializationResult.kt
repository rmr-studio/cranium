package riven.core.models.integration.materialization

/**
 * Result of materializing integration templates into workspace-scoped entity types.
 */
data class MaterializationResult(
    val entityTypesCreated: Int,
    val entityTypesRestored: Int,
    val relationshipsCreated: Int,
    val integrationSlug: String
)
