package cranium.core.models.integration

data class IntegrationSoftDeleteResult(
    val entityTypesSoftDeleted: Int,
    val relationshipsSoftDeleted: Int
)
