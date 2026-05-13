package cranium.core.models.workflow.node.config.validation

/**
 * Aggregated result of config validation.
 *
 * Contains all validation errors found. Use [isValid] to check if config passes validation.
 */
data class ConfigValidationResult(
    val errors: List<ConfigValidationError> = emptyList()
) {
    val isValid: Boolean get() = errors.isEmpty()

    companion object {
        fun valid(): ConfigValidationResult = ConfigValidationResult(emptyList())

        fun invalid(vararg errors: ConfigValidationError): ConfigValidationResult =
            ConfigValidationResult(errors.toList())
    }
}
