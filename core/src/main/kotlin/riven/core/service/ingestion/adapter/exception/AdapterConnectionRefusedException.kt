package riven.core.service.ingestion.adapter.exception

/** Host unreachable, connection refused, 404-equivalent (resource does not exist). */
class AdapterConnectionRefusedException(message: String, cause: Throwable? = null) : FatalAdapterException(message, cause)
