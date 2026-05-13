package cranium.core.service.ingestion.adapter.exception

/**
 * Base for all adapter-raised exceptions.
 *
 * Sealed so Temporal retry classification can dispatch on leaf types. Phase 4
 * will wire the registry with something along the lines of
 * `RetryOptions.Builder.setDoNotRetry(
 *     *FatalAdapterException::class.sealedSubclasses
 *         .mapNotNull { it.qualifiedName }
 *         .toTypedArray()
 * )` — do NOT wire Temporal here; this module is pure types.
 */
sealed class AdapterException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
