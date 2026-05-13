package cranium.core.models.workflow.engine.error

import cranium.core.enums.workflow.WorkflowErrorType

data class NodeExecutionError(
    val errorType: WorkflowErrorType,
    val message: String,
    val httpStatusCode: Int? = null,
    val retryAttempts: List<RetryAttempt>,
    val isFinal: Boolean,
    val stackTrace: String? = null
)
