---
tags:
  - layer/model
  - component/active
  - architecture/component
Created: 2026-03-29
Domains:
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
---

# ResolutionResult

Part of [[Entity Projection]]

## Purpose

Sealed class representing identity resolution outcomes for a single integration entity — either matched to an existing core entity or flagged as new.

## Key Types

### `ResolutionResult.ExistingEntity`

Matched to an existing core entity.

| Field | Type | Purpose |
|-------|------|---------|
| `entityId` | UUID | The matched core entity's ID |
| `matchType` | MatchType | How the match was determined |

### `ResolutionResult.NewEntity`

No match found — eligible for auto-creation.

| Field | Type | Purpose |
|-------|------|---------|
| `warnings` | List\<String\> | Context warnings (e.g., ambiguous match details). Default: empty list. |

### `MatchType` enum

| Value | Meaning |
|-------|---------|
| `EXTERNAL_ID` | Matched via `sourceExternalId` (Check 1 — fast batch lookup) |
| `IDENTIFIER_KEY` | Matched via IDENTIFIER-classified attribute value (Check 2 — JSONB fallback) |

## Used By

| Consumer | Context |
|----------|---------|
| [[IdentityResolutionService]] | Produces resolution results from `resolveBatch()` |
| [[EntityProjectionService]] | Consumes to route projection — `ExistingEntity` triggers update, `NewEntity` triggers create |

## Related

- [[IdentityResolutionService]]
- [[EntityProjectionService]]
- [[Entity Projection]]
