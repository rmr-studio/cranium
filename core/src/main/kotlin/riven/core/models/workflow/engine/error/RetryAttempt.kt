package cranium.core.models.workflow.engine.error

import cranium.core.enums.workflow.WorkflowErrorType
import java.time.ZonedDateTime

data class RetryAttempt(
    val attemptNumber: Int,
    val timestamp: ZonedDateTime,
    val errorType: WorkflowErrorType,
    val errorMessage: String,
    val durationMs: Long
)
