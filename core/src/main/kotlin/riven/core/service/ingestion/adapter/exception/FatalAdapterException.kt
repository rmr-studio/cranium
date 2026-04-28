package riven.core.service.ingestion.adapter.exception

/**
 * Sealed base for non-retryable adapter failures. Each concrete leaf is listed
 * by Phase 4 in Temporal's do-not-retry set via [Class.getName] reflection over
 * [kotlin.reflect.KClass.sealedSubclasses].
 */
sealed class FatalAdapterException(message: String, cause: Throwable? = null) : AdapterException(message, cause)
