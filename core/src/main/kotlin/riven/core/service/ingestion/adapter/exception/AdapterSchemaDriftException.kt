package cranium.core.service.ingestion.adapter.exception

/**
 * Remote schema changed in an incompatible way between syncs (table or column
 * disappeared, type changed). Consumed by Phase 6 HLTH-04.
 */
class AdapterSchemaDriftException(message: String, cause: Throwable? = null) : FatalAdapterException(message, cause)
