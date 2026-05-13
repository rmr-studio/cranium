package cranium.core.enums.workflow

/**
 * Discriminator for execution queue job types.
 *
 * Enables the execution queue to serve multiple job categories
 * while ensuring each consumer only processes its own job type.
 *
 * - WORKFLOW_EXECUTION: Standard workflow dispatch jobs (claimed by WorkflowExecutionDispatcherService)
 * - IDENTITY_MATCH: Entity identity resolution jobs (claimed by identity match dispatcher, Phase 3+)
 * - ENRICHMENT: Entity embedding enrichment jobs (claimed by enrichment pipeline)
 */
enum class ExecutionJobType {
    WORKFLOW_EXECUTION,
    IDENTITY_MATCH,
    ENRICHMENT
}
