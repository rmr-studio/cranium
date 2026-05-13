package cranium.core.models.response.catalog

import java.util.UUID

data class TemplateInstallationResponse(
    val templateKey: String,
    val templateName: String,
    val entityTypesCreated: Int,
    val relationshipsCreated: Int,
    val entityTypes: List<CreatedEntityTypeSummary>,
)

data class CreatedEntityTypeSummary(
    val id: UUID,
    val key: String,
    val displayName: String,
    val attributeCount: Int,
)
