package cranium.core.service.ingestion.adapter.exception

/** Persistent 5xx or an otherwise unknown-but-fatal remote condition. */
class AdapterUnavailableException(message: String, cause: Throwable? = null) : FatalAdapterException(message, cause)
