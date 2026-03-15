package riven.core.models.integration

import riven.core.enums.integration.ConnectionStatus
import java.time.ZonedDateTime
import java.util.*

data class IntegrationConnectionModel(
    val id: UUID,
    val workspaceId: UUID,
    val integrationId: UUID,
    val nangoConnectionId: String,
    val status: ConnectionStatus,
    val connectionMetadata: Map<String, Any>?,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?
)
