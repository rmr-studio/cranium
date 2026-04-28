package riven.core.service.ingestion.adapter.exception

/**
 * The requested capability is not implemented by this adapter (e.g. calling
 * `introspectSchema` on an adapter that only supports `fetchRecords`).
 */
class AdapterCapabilityNotSupportedException(message: String, cause: Throwable? = null) : FatalAdapterException(message, cause)
