package cranium.core.models.workflow.engine.state

import cranium.core.enums.workflow.WorkflowStatus
import java.time.Instant
import java.util.UUID

/**
 * Immutable record of a workflow step's execution output.
 *
 * The canonical structure for capturing node execution results. Stored in the
 * WorkflowDataStore to enable node-to-node data flow via template resolution.
 *
 * ## Design Principles
 *
 * - **Immutable:** Once stored, never modified (execution is atomic)
 * - **Self-contained:** Includes all metadata needed for debugging/replay
 * - **Status-aware:** Captures success and completion states
 * - **Duration tracking:** Includes execution time for performance analysis
 *
 * ## Template Resolution
 *
 * Step outputs are accessible via template paths:
 * - `{{ steps.nodeName.output.field }}` - Access specific output field
 * - `{{ steps.nodeName.status }}` - Access execution status
 *
 * ## Error Handling
 *
 * StepOutput does NOT include an error field. Errors throw exceptions and
 * are not stored in the datastore. Failed executions will have status=FAILED
 * but the specific error is captured at the workflow level, not per-step.
 *
 * @property nodeId Unique identifier of the executed node
 * @property nodeName Human-readable name used for template references (e.g., "fetch_leads")
 * @property status Execution status: COMPLETED, FAILED, CANCELED
 * @property output Typed action output (uses NodeOutput.toMap() for template resolution)
 * @property executedAt Timestamp when execution completed
 * @property durationMs Execution duration in milliseconds
 */
data class StepOutput(
    val nodeId: UUID,
    val nodeName: String,
    val status: WorkflowStatus,
    val output: NodeOutput,
    val executedAt: Instant,
    val durationMs: Long
)
