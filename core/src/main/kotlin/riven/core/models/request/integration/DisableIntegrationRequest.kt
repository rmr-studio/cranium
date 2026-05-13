package cranium.core.models.request.integration

import jakarta.validation.constraints.NotNull
import java.util.*

data class DisableIntegrationRequest(
    @field:NotNull
    val integrationDefinitionId: UUID
)
