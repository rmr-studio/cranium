package cranium.core.models.response.integration

import java.util.*

data class IntegrationDisableResponse(
    val integrationDefinitionId: UUID,
    val integrationName: String,
    val entityTypesSoftDeleted: Int,
    val relationshipsSoftDeleted: Int
)
