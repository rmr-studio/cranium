package riven.core.models.request.integration

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import riven.core.models.integration.SyncConfiguration
import java.util.*

data class EnableIntegrationRequest(
    @field:NotNull
    val integrationDefinitionId: UUID,

    @field:NotBlank
    val nangoConnectionId: String,

    val syncConfig: SyncConfiguration? = null
)
