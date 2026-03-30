---
tags:
  - layer/entity
  - component/active
  - architecture/component
Created: 2026-03-17
Domains:
  - "[[Integrations]]"
---
# IntegrationSyncStateEntity

Part of [[Data Sync]]

## Purpose

JPA entity tracking per-connection per-entity-type sync progress. Records sync status, cursor position for incremental sync, consecutive failure count, last error message, and record metrics (synced/failed counts). Extends `AuditableEntity` for audit fields but does NOT implement `SoftDeletable` — this is system-managed state that is updated in-place and deleted via CASCADE when the parent connection or entity type is removed.

---

## Fields

| Column | Type | Purpose |
|--------|------|---------|
| `id` | `UUID` | Primary key, auto-generated |
| `integration_connection_id` | `UUID` | FK to the Nango connection this sync state belongs to |
| `entity_type_id` | `UUID` | FK to the entity type being synced |
| `status` | `SyncStatus` | Current sync status — `PENDING`, `SUCCESS`, or `FAILED` |
| `last_cursor` | `String?` | Cursor position for incremental sync (opaque string from Nango) |
| `consecutive_failure_count` | `Int` | Number of consecutive failed syncs — resets on success |
| `last_error_message` | `String?` | Error message from the most recent failed sync |
| `last_records_synced` | `Int?` | Number of records successfully synced in the last run |
| `last_records_failed` | `Int?` | Number of records that failed in the last run |
| `last_pipeline_step` | `String?` | Tracks which projection pipeline step last completed (e.g., "PROJECTION") |
| `projection_result` | `Map<String, Any>?` | JSONB summary of the last projection operation — created/updated/skipped/error counts |

### SyncStatus Enum

| Value | Meaning |
|-------|---------|
| `PENDING` | Initial state — sync has not yet completed |
| `SUCCESS` | Last sync completed successfully |
| `FAILED` | Last sync failed |

---

## Key Design Decisions

- **System-managed, not soft-deletable:** Sync state rows are owned by the system and cleaned up via CASCADE deletes on the parent connection or entity type. There is no user-facing delete operation.
- **Unique constraint on (connection, entity_type):** Enforced at the DB level (`uq_sync_state_connection_entity_type`) — at most one sync state per connection per entity type.
- **Updated in-place:** Status, cursor, failure count, and record metrics are mutated on each sync run. No versioning or history — only the latest state is retained.
- **Extends AuditableEntity:** Inherits `created_at` and `updated_at` audit columns from the base class.

---

## Related

- [[IntegrationSyncStateRepository]] — persistence layer
- [[Data Sync]] — parent subdomain
- [[IntegrationConnectionEntity]] — parent connection (CASCADE delete)
