package cranium.core.service.workflow.state

import org.springframework.stereotype.Service
import cranium.core.models.workflow.node.config.validation.ConfigValidationError
import cranium.core.models.workflow.node.config.validation.ConfigValidationResult
import java.time.Duration
import java.util.UUID

/**
 * Service for validating workflow node configuration fields.
 *
 * Provides reusable validation methods for common config field patterns:
 * - Template syntax validation (uses TemplateParserService)
 * - UUID validation (for entity type/entity ID references)
 * - Required field validation
 *
 * ## Validation Philosophy
 *
 * Per CONTEXT.md decisions:
 * - Validate on save, not during execution
 * - Return all errors at once (not fail-fast)
 * - Validate template SYNTAX only (not that referenced steps exist)
 * - Verify entity refs exist in workspace is service-layer concern (separate)
 *
 * @see WorkflowNodeTemplateParserService for template parsing
 */
@Service
class WorkflowNodeConfigValidationService(
    private val workflowNodeTemplateParserService: WorkflowNodeTemplateParserService
) {

    /**
     * Validates that a string is either a valid template or a valid UUID.
     *
     * Many config fields accept either:
     * - A template like "{{ steps.x.output.id }}"
     * - A static UUID string like "550e8400-e29b-41d4-a716-446655440000"
     *
     * @param value The string value to validate
     * @param fieldPath The field path for error reporting (e.g., "entityTypeId")
     * @return List of validation errors (empty if valid)
     */
    fun validateTemplateOrUuid(value: String?, fieldPath: String): List<ConfigValidationError> {
        if (value == null) {
            return listOf(ConfigValidationError(fieldPath, "Required field cannot be null"))
        }

        if (value.isBlank()) {
            return listOf(ConfigValidationError(fieldPath, "Required field cannot be blank"))
        }

        // Check if it looks like a template (contains {{ }})
        // isTemplate only returns true for valid templates, so we also check for {{ to catch malformed ones
        if (workflowNodeTemplateParserService.isTemplate(value) || value.contains("{{")) {
            return validateTemplateSyntax(value, fieldPath)
        }

        // Otherwise validate as UUID
        return try {
            UUID.fromString(value)
            emptyList()
        } catch (e: IllegalArgumentException) {
            listOf(ConfigValidationError(fieldPath, "Must be a valid UUID or template"))
        }
    }

    /**
     * Validates template syntax without checking that referenced steps exist.
     *
     * Per CONTEXT.md: "Validate template syntax on save. Don't validate that
     * referenced steps exist (that's cross-node validation)."
     *
     * @param value The template string to validate
     * @param fieldPath The field path for error reporting
     * @return List of validation errors (empty if valid syntax)
     */
    fun validateTemplateSyntax(value: String, fieldPath: String): List<ConfigValidationError> {
        return try {
            workflowNodeTemplateParserService.parse(value)
            emptyList()
        } catch (e: IllegalArgumentException) {
            listOf(ConfigValidationError(fieldPath, e.message ?: "Invalid template syntax"))
        }
    }

    /**
     * Validates that a string field is not null or blank when required.
     *
     * @param value The value to check
     * @param fieldPath The field path for error reporting
     * @param required Whether the field is required
     * @return List of validation errors (empty if valid)
     */
    fun validateRequiredString(
        value: String?,
        fieldPath: String,
        required: Boolean = true
    ): List<ConfigValidationError> {
        if (!required) return emptyList()

        return when {
            value == null -> listOf(ConfigValidationError(fieldPath, "Required field cannot be null"))
            value.isBlank() -> listOf(ConfigValidationError(fieldPath, "Required field cannot be blank"))
            else -> emptyList()
        }
    }

    /**
     * Validates a map of template-enabled values (e.g., payload fields).
     *
     * Per CONTEXT.md: "All String fields template-enabled"
     *
     * @param values Map of field name to value
     * @param parentPath Parent path for error reporting (e.g., "payload")
     * @return List of validation errors from all fields
     */
    fun validateTemplateMap(values: Map<String, String>?, parentPath: String): List<ConfigValidationError> {
        if (values == null) return emptyList()

        return values.flatMap { (fieldName, value) ->
            val fieldPath = "$parentPath.$fieldName"

            // All string values in payload can be templates
            // Check for {{ to catch both valid and malformed templates
            if (workflowNodeTemplateParserService.isTemplate(value) || value.contains("{{")) {
                validateTemplateSyntax(value, fieldPath)
            } else {
                emptyList() // Static values are always valid
            }
        }
    }

    /**
     * Validates an optional Duration field represented as ISO-8601 string or seconds.
     *
     * @param value The duration value (can be seconds as Long, or ISO-8601 string)
     * @param fieldPath The field path for error reporting
     * @return List of validation errors (empty if valid or null)
     */
    fun validateOptionalDuration(value: Any?, fieldPath: String): List<ConfigValidationError> {
        if (value == null) return emptyList()

        return when (value) {
            is Number -> {
                if (value.toLong() < 0) {
                    listOf(ConfigValidationError(fieldPath, "Duration cannot be negative"))
                } else {
                    emptyList()
                }
            }

            is String -> {
                try {
                    Duration.parse(value)
                    emptyList()
                } catch (e: Exception) {
                    listOf(
                        ConfigValidationError(
                            fieldPath,
                            "Invalid duration format. Use ISO-8601 (e.g., PT30S) or seconds"
                        )
                    )
                }
            }

            else -> listOf(ConfigValidationError(fieldPath, "Duration must be a number (seconds) or ISO-8601 string"))
        }
    }

    /**
     * Combines multiple validation results into a single result.
     *
     * @param results Vararg of validation error lists
     * @return Combined ConfigValidationResult
     */
    fun combine(vararg results: List<ConfigValidationError>): ConfigValidationResult {
        val allErrors = results.flatMap { it }
        return ConfigValidationResult(allErrors)
    }

    /**
     * Checks if a string value contains template syntax (e.g., {{ steps.x.output }}).
     *
     * @param value The string to check
     * @return true if value contains template markers
     */
    fun isTemplate(value: String): Boolean = workflowNodeTemplateParserService.isTemplate(value)
}