package riven.core.models.ingestion.adapter

/**
 * Declares how an adapter emits records to the orchestrator.
 *
 * Declared in `models/ingestion/adapter/` (not `enums/integration/`) because it
 * describes *adapter capability*, not a persisted property on a JPA entity.
 *
 * Values:
 * - [POLL] — Orchestrator invokes `fetchRecords` on a schedule (Temporal).
 *   Most common mode for v1 (Postgres, Nango connectors).
 * - [CDC] — Change Data Capture. Adapter streams change events. Not wired in v1.
 * - [PUSH] — Remote pushes to an orchestrator-owned endpoint (webhooks). Adapter
 *   receives and forwards. Not wired in v1.
 * - [ONE_SHOT] — Single-run import (CSV upload, manifest backfill). No continuation.
 */
enum class SyncMode {
    POLL,
    CDC,
    PUSH,
    ONE_SHOT,
}
