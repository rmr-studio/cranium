package cranium.core.models.request.entity.type

import jakarta.validation.constraints.NotEmpty
import java.util.*

data class SchemaReconcileRequest(
    @field:NotEmpty(message = "entityTypeIds must not be empty")
    val entityTypeIds: List<UUID>,
    val impactConfirmed: Boolean = false,
)
