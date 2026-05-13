package cranium.core.models.workflow.engine.execution

import cranium.core.enums.workflow.WorkflowStatus
import cranium.core.models.common.json.JsonValue
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

sealed interface ExecutionRecord {
    val id: UUID
    val workspaceId: UUID

    // Execution Summary
    val status: WorkflowStatus
    val startedAt: ZonedDateTime
    val completedAt: ZonedDateTime?
    val duration: Duration?

    // Execution Records
    // TODO: Replace with proper payload types
    val error: JsonValue
    val input: JsonValue
    val output: JsonValue
}