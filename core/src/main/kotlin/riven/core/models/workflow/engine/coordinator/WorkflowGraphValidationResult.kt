package cranium.core.models.workflow.engine.coordinator

/**
 * Result of DAG validation.
 *
 * @property valid True if the graph passed all validation checks
 * @property errors List of validation error messages (empty if valid)
 */
data class WorkflowGraphValidationResult(
    val valid: Boolean,
    val errors: List<String>
)