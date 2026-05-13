package cranium.core.models.ingestion.adapter

/**
 * Neutral, source-agnostic record produced by an ingestion adapter.
 *
 * The shape is intentionally loose to accommodate every adapter flavour:
 * - **Nango**: `payload` is the JSON object returned by the upstream integration.
 * - **Postgres**: `payload` carries typed column values (Long, String, Boolean,
 *   Timestamp, etc.) keyed by column name.
 * - **CSV**: `payload` is a string map keyed by header.
 * - **Webhook**: `payload` is the arbitrary JSON body delivered by the remote.
 *
 * Phase 3+ may narrow `payload` value types per adapter, but the orchestrator
 * must continue to treat it as a generic map.
 *
 * @property externalId Source-system identifier for this record. Must be stable
 *   across syncs so the orchestrator can match on identity. Opaque to the
 *   orchestrator.
 * @property payload The record's field values, keyed by source-system field name.
 * @property sourceMetadata Optional per-record adapter metadata (e.g. Postgres
 *   table name, Nango sync run id, CSV row number). Not required for happy-path
 *   ingestion; surfaced for debugging and lineage.
 */
data class SourceRecord(
    val externalId: String,
    val payload: Map<String, Any?>,
    val sourceMetadata: Map<String, Any?>? = null,
)
