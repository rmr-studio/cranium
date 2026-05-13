package cranium.core.models.workflow.engine.state

import java.time.Instant
import java.util.UUID

/**
 * Immutable metadata for a workflow execution.
 *
 * This data class captures workflow-level context that is set once at creation
 * and never modified throughout the execution lifecycle. It provides essential
 * identifiers and timing information for observability and debugging.
 *
 * ## Immutability Guarantee
 *
 * All fields are val and the class is a data class, ensuring:
 * - Thread-safe reads from any context
 * - No synchronization needed for access
 * - Safe to share across parallel node executions
 *
 * @property executionId Unique identifier for this workflow execution run
 * @property workspaceId Multi-tenant workspace context for security/isolation
 * @property workflowDefinitionId Reference to the workflow definition being executed
 * @property version Version of the workflow definition at execution time
 * @property startedAt Timestamp when workflow execution began
 */
data class WorkflowMetadata(
    val executionId: UUID,
    val workspaceId: UUID,
    val workflowDefinitionId: UUID,
    val version: Int,
    val startedAt: Instant
)
