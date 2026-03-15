package riven.core.models.integration

import riven.core.enums.integration.IntegrationCategory
import java.time.ZonedDateTime
import java.util.*

data class IntegrationDefinitionModel(
    val id: UUID,
    val slug: String,
    val name: String,
    val iconUrl: String?,
    val description: String?,
    val category: IntegrationCategory,
    val nangoProviderKey: String,
    val capabilities: Map<String, Any>,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)
