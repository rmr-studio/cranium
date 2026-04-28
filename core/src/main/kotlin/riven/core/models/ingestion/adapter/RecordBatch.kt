package riven.core.models.ingestion.adapter

/**
 * Neutral batch result returned by an `IngestionAdapter.fetchRecords` call.
 *
 * This type is the orchestrator-facing contract — every adapter (Nango wrapper,
 * Postgres, future CSV/webhook) returns records in this shape.
 *
 * @property records The records fetched in this batch.
 * @property nextCursor Opaque, adapter-owned continuation token. The orchestrator
 *   round-trips this value to the next `fetchRecords` call without parsing or
 *   interpreting it. `null` indicates terminal position (combined with
 *   `hasMore = false`).
 * @property hasMore True when the adapter believes more records remain beyond
 *   this batch. When false, callers should stop polling until the next sync
 *   window.
 */
data class RecordBatch(
    val records: List<SourceRecord>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
