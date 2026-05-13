package cranium.core.service.ingestion.adapter

import cranium.core.models.ingestion.adapter.RecordBatch
import cranium.core.models.ingestion.adapter.SchemaIntrospectionResult
import cranium.core.models.ingestion.adapter.SyncMode

/**
 * Polyglot ingestion adapter contract. One `@Component` implementation per
 * [cranium.core.enums.integration.SourceType]. Stateless — connection and
 * credentials travel via [AdapterCallContext] per call.
 *
 * Implementations raise subtypes of
 * [cranium.core.service.ingestion.adapter.exception.AdapterException]; Temporal
 * uses the sealed hierarchy for retry classification (Phase 4 wiring).
 */
interface IngestionAdapter {
    /** Declares how this adapter emits records (POLL, CDC, PUSH, ONE_SHOT). */
    fun syncMode(): SyncMode

    /** Introspects the remote schema for this call context. */
    fun introspectSchema(context: AdapterCallContext): SchemaIntrospectionResult

    /**
     * Fetches a batch of records starting at [cursor] (opaque, adapter-specific,
     * or `null` for the first call). [limit] is an upper bound the adapter
     * should respect best-effort.
     */
    fun fetchRecords(context: AdapterCallContext, cursor: String?, limit: Int): RecordBatch
}
