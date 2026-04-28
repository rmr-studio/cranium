package riven.core.service.ingestion.adapter.exception

/** Authentication/authorisation failure (401/403, expired token, revoked connection). */
class AdapterAuthException(message: String, cause: Throwable? = null) : FatalAdapterException(message, cause)
