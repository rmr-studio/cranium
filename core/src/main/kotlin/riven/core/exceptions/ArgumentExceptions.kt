package riven.core.exceptions

class NotFoundException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class SupabaseException(message: String) : RuntimeException(message)
class InvalidRelationshipException(message: String) : RuntimeException(message)
class SchemaValidationException(val reasons: List<String>) :
    RuntimeException("Schema validation failed: ${reasons.joinToString("; ")}")

class UniqueConstraintViolationException(message: String) : RuntimeException(message)

/**
 * Exception thrown when workflow DAG validation fails.
 *
 * This indicates structural problems with the workflow graph:
 * - Cycles detected
 * - Disconnected components
 * - Invalid edge references
 * - Conditional nodes without proper branching
 *
 * @param message Validation error details
 * @param cause Optional underlying exception
 */
class WorkflowValidationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exception thrown when workflow execution fails.
 *
 * This wraps errors that occur during node execution, providing context
 * about which node failed and why.
 *
 * @param message Execution error details
 * @param cause Underlying exception from node execution
 */
class WorkflowExecutionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

// ------ Storage Exceptions ------

open class StorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class StorageNotFoundException(message: String) : StorageException(message)

class StorageProviderException(message: String, cause: Throwable? = null) : StorageException(message, cause)

class ContentTypeNotAllowedException(message: String) : StorageException(message)

class FileSizeLimitExceededException(message: String) : StorageException(message)

class SignedUrlExpiredException(message: String) : StorageException(message)
