---
tags:
  - layer/model
  - component/active
  - architecture/component
Created: 2026-03-29
Domains:
  - "[[Integrations]]"
---

# ProjectionResult

Part of [[Entity Projection]]

## Purpose

Tracks projection pipeline outcomes — aggregate counts plus per-entity detail records.

## Key Types

### `ProjectionResult`

Aggregate summary of a projection run.

| Field | Type | Purpose |
|-------|------|---------|
| `created` | Int | Number of new core entities created |
| `updated` | Int | Number of existing core entities updated |
| `skipped` | Int | Number of entities skipped (no match, auto-create disabled, soft-deleted, stale) |
| `errors` | Int | Number of entities that failed during projection |
| `details` | List\<ProjectionDetail\> | Per-entity outcome records (default: empty list) |

### `ProjectionDetail`

Per-entity outcome.

| Field | Type | Purpose |
|-------|------|---------|
| `sourceEntityId` | UUID | The integration entity ID |
| `targetEntityId` | UUID? | The core entity ID (null for skips and errors) |
| `outcome` | ProjectionOutcome | The outcome enum value |
| `message` | String? | Error message or context (null for success outcomes) |

### `ProjectionOutcome` enum

| Value | Meaning |
|-------|---------|
| `CREATED` | New core entity created from integration data |
| `UPDATED` | Existing core entity updated with integration data |
| `SKIPPED_NO_MATCH` | No identity match and no rule to handle |
| `SKIPPED_AUTO_CREATE_DISABLED` | Rule has `autoCreate = false` and no match found |
| `SKIPPED_SOFT_DELETED` | Matched core entity is soft-deleted |
| `SKIPPED_STALE_VERSION` | Integration entity's syncVersion < core entity's syncVersion |
| `ERROR` | Exception during projection |

## Used By

| Consumer | Context |
|----------|---------|
| [[EntityProjectionService]] | Returns from `processProjections()` |
| [[IntegrationSyncActivitiesImpl]] | Logs result summary after Pass 3 |

## Related

- [[EntityProjectionService]]
- [[Entity Projection]]
