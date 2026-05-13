package cranium.core.models.workflow.node.config.validation

/**
 * Field-level validation error for workflow node configurations.
 *
 * @property field Dot-notation path to the field (e.g., "payload.name", "entityTypeId")
 * @property message Human-readable error message
 */
data class ConfigValidationError(
    val field: String,
    val message: String
)
