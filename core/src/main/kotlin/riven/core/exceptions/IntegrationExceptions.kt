package cranium.core.exceptions

/**
 * Exception thrown when Nango API rate limits the request.
 */
class RateLimitException(message: String) : RuntimeException(message)

/**
 * Exception thrown when Nango API returns an error.
 */
class NangoApiException(message: String, val statusCode: Int) : RuntimeException(message)

/**
 * Exception thrown when Nango API returns a transient server error (5xx) that should be retried.
 */
class TransientNangoException(message: String, val statusCode: Int) : RuntimeException(message)

/**
 * Exception thrown when an invalid state transition is attempted on a connection.
 */
class InvalidStateTransitionException(message: String) : RuntimeException(message)
