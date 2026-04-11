---
Created: 2026-03-17
Domains:
  - "[[riven/docs/system-design/domains/Identity Resolution/Identity Resolution]]"
tags:
  - component/active
  - layer/entity
  - architecture/component
---

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Identity Resolution/Matching Pipeline/Matching Pipeline]]

# MatchSuggestionEntity

## Purpose

Represents a candidate match between two entities for human review. Extends `AuditableSoftDeletableEntity`. Table: `match_suggestions`.

## Key Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID, PK | Primary key |
| `workspaceId` | UUID, FK to workspaces | Workspace scope |
| `sourceEntityId` | UUID, FK to entities | Always the lower UUID of the pair |
| `targetEntityId` | UUID, FK to entities | Always the higher UUID of the pair |
| `status` | `MatchSuggestionStatus` | PENDING, CONFIRMED, REJECTED, EXPIRED |
| `confidenceScore` | BigDecimal, numeric(5,4) | Composite match confidence |
| `signals` | JSONB, `List<Map<String, Any?>>` | Per-signal breakdown with type, sourceValue, targetValue, similarity, weight |
| `rejectionSignals` | JSONB, nullable | Snapshot of signals at rejection time for re-suggestion comparison |
| `resolvedBy` | UUID?, FK to users | User who confirmed/rejected |
| `resolvedAt` | ZonedDateTime? | When the suggestion was resolved |
| *(audit fields)* | via `AuditableSoftDeletableEntity` | createdAt, updatedAt, createdBy, updatedBy, deleted, deletedAt |

## Database Constraints

- **CHECK:** `source_entity_id < target_entity_id` — canonical UUID ordering enforced at DB level
- **UNIQUE:** `(workspace_id, source_entity_id, target_entity_id) WHERE deleted = false` — prevents duplicate active suggestions for the same pair
- **Indexes:**
  - workspace + status (`WHERE deleted = false`)
  - source_entity (`WHERE deleted = false`)
  - target_entity (`WHERE deleted = false`)

## toModel()

Converts to `MatchSuggestion` domain model using `requireNotNull` for `id`, `createdAt`, `updatedAt`.

## Gotchas

- **Canonical UUID ordering** is enforced at both code level (`IdentityMatchSuggestionService.canonicalOrder()`) and DB level (CHECK constraint). The source entity is always the lower UUID of the pair, preventing duplicate mirror entries.
- **Soft-delete is used for rejected suggestions.** This enables the re-suggestion flow where a new higher-scoring match can replace an old rejected suggestion.
- **`MatchSuggestionRepository` uses native SQL queries** that bypass `@SQLRestriction("deleted = false")`. `findActiveSuggestion` explicitly checks `deleted = false`; `findRejectedSuggestion` explicitly checks `deleted = true`.

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Identity Resolution/Matching Pipeline/IdentityMatchSuggestionService]]
- [[MatchSuggestionRepository]]
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Identity Resolution/Matching Pipeline/MatchSignalType]]
