package cranium.core.enums.workflow

enum class WorkflowErrorType(val retryable: Boolean) {
    HTTP_CLIENT_ERROR(false),      // 4xx - non-retryable
    HTTP_SERVER_ERROR(true),       // 5xx - retryable
    VALIDATION_ERROR(false),       // schema/input validation - non-retryable
    CONTROL_FLOW_ERROR(false),     // CONDITION node deterministic failure - non-retryable
    SECURITY_ERROR(false),         // auth/authz - non-retryable
    DATABASE_ERROR(true),          // connection issues - retryable
    NETWORK_ERROR(true),           // timeout/connection - retryable
    EXECUTION_ERROR(true),         // generic - retryable
    UNKNOWN_ERROR(true)            // fallback - retryable
}
