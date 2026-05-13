package cranium.core.exceptions.connector

/**
 * Sealed hierarchy for custom-source connection failures (Phase 2).
 *
 * Sibling to Phase 1's `AdapterException` rather than a subclass — keeps
 * Temporal do-not-retry policies configured against `FatalAdapterException`
 * unaffected and lets the service layer decide retry behaviour per subtype.
 *
 * All subtypes extend [RuntimeException] via this sealed parent so that
 * Spring `@Transactional` rollback semantics fire by default (checked
 * exceptions would require `rollbackFor` wiring). See research pitfall 6.
 */
sealed class ConnectionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** AES-GCM encrypt/decrypt failure (bad key, bad IV, authentication tag mismatch). */
class CryptoException(message: String, cause: Throwable? = null) : ConnectionException(message, cause)

/** Stored ciphertext/IV is malformed or corrupt — unrecoverable without re-entering credentials. */
class DataCorruptionException(message: String, cause: Throwable? = null) : ConnectionException(message, cause)

/** Host resolved to a blocked / internal / link-local IP — SSRF guard tripped. */
class SsrfRejectedException(message: String, cause: Throwable? = null) : ConnectionException(message, cause)

/** Supplied Postgres role failed the read-only verification probe. */
class ReadOnlyVerificationException(message: String, cause: Throwable? = null) : ConnectionException(message, cause)
