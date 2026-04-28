package riven.core.service.ingestion.adapter.exception

/**
 * Retryable adapter failure (network blips, rate limits, temporary
 * unavailability). Temporal will retry with backoff; adapters should raise
 * this for any condition expected to self-heal.
 */
class TransientAdapterException(message: String, cause: Throwable? = null) : AdapterException(message, cause)
