package cranium.core.exceptions.query

/**
 * Exception thrown when SQL query execution fails.
 *
 * Wraps database access exceptions with query context information.
 * This is NOT part of the QueryFilterException sealed hierarchy - it represents
 * runtime SQL execution errors, not filter validation errors.
 *
 * @param message Descriptive error message including query context
 * @param cause The underlying exception that caused the failure
 */
class QueryExecutionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
