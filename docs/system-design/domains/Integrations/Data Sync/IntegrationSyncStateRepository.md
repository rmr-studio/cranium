---
tags:
  - layer/repository
  - component/active
  - architecture/component
Created: 2026-03-17
Domains:
  - "[[Integrations]]"
---
# IntegrationSyncStateRepository

Part of [[Data Sync]]

## Purpose

JPA repository for `IntegrationSyncStateEntity` persistence — provides connection-scoped lookups for sync state, either for all entity types on a connection or for a specific connection+entity type pair.

---

## Responsibilities

- Persist and retrieve `IntegrationSyncStateEntity` rows from `integration_sync_state`
- List all sync states for a given connection (to show sync progress across all entity types)
- Find a specific sync state by connection + entity type (to check/update sync progress for a single type)

---

## Dependencies

- `IntegrationSyncStateEntity` — JPA entity mapping for `integration_sync_state` table

## Used By

- Future sync orchestration services (not yet implemented)

---

## Public Methods

### `findByIntegrationConnectionId(integrationConnectionId: UUID): List<IntegrationSyncStateEntity>`

Lists all sync state rows for a connection — one per entity type being synced. Used to display sync progress across all entity types for a given integration connection.

### `findByIntegrationConnectionIdAndEntityTypeId(integrationConnectionId: UUID, entityTypeId: UUID): IntegrationSyncStateEntity?`

Finds the sync state for a specific connection + entity type pair. Returns `null` if no sync has been initiated for this combination. Used by the sync engine to load/update cursor position and failure state for incremental sync.

---

## Gotchas

- **No soft-delete filtering:** Unlike most repositories in this domain, `IntegrationSyncStateEntity` does not implement `SoftDeletable` and has no `@SQLRestriction`. All rows returned by queries are active — cleanup happens via CASCADE deletes on the parent connection or entity type.

---

## Related

- [[IntegrationSyncStateEntity]] — JPA entity
- [[Data Sync]] — parent subdomain
