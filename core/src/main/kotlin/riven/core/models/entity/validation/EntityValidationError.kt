package cranium.core.models.entity.validation

import java.util.*

/**
 * Validation error for a specific entity.
 */
data class EntityValidationError(
    val entityId: UUID,
    val entityName: String?,
    val errors: List<String>
)
