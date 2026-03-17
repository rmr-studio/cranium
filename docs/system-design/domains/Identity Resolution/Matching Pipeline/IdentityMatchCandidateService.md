---
tags:
  - component/active
  - layer/service
  - architecture/component
  - domain/identity-resolution
Created: 2026-03-17
Domains:
  - "[[Identity Resolution]]"
Sub-Domains:
  - "[[Matching Pipeline]]"
---

# IdentityMatchCandidateService

## Purpose

Finds candidate duplicate entities for a given trigger entity using pg_trgm trigram similarity on IDENTIFIER-classified attributes. Uses a two-phase approach: first leverages the GIN index with the `%` operator for blocking (fast narrowing), then extracts similarity scores for ranking.

## Responsibilities

**Owns:**
- Two-phase pg_trgm candidate query (GIN index blocking + similarity score extraction)
- Trigger entity attribute extraction from entity_attributes table
- Candidate deduplication (highest similarity per entity + signalType pair)
- Value normalization (trim + lowercase)

**Does NOT own:**
- Scoring and weighting (IdentityMatchScoringService)
- Suggestion persistence (IdentityMatchSuggestionService)
- Queue management

## Dependencies

| Dependency | Type | Purpose |
|---|---|---|
| EntityManager | Injected | Native SQL execution for pg_trgm queries |
| EntityTypeSemanticMetadataRepository | Injected | Lookup of IDENTIFIER-classified attributes for entity types |
| KLogger | Injected | Structured logging |

No Spring Security dependencies — called from Temporal context without JWT.

## Consumed By

| Consumer | Method | Context |
|---|---|---|
| IdentityMatchActivitiesImpl | findCandidates | Temporal activity — FindCandidates step |

## Public Interface

### findCandidates

```kotlin
fun findCandidates(triggerEntityId: UUID, workspaceId: UUID): List<CandidateMatch>
```

Main entry point. Queries the trigger entity's IDENTIFIER attributes, runs a candidate query per attribute, and deduplicates results (keeping highest similarity per entity + signalType pair).

**Returns:** List of CandidateMatch objects containing candidateEntityId, attributeId, value, similarity score, and signalType. Empty list if trigger has no IDENTIFIER attributes.

### getTriggerAttributes

```kotlin
fun getTriggerAttributes(entityId: UUID, workspaceId: UUID): Map<MatchSignalType, String>
```

Returns a signal type to normalized value map for the trigger entity's IDENTIFIER-classified attributes.

## Key Logic

### queryTriggerIdentifierAttributes

Native SQL joining `entity_attributes` with `entity_type_semantic_metadata` WHERE `classification = 'IDENTIFIER'`. Extracts attribute values for the trigger entity, maps SchemaType to MatchSignalType.

### runCandidateQuery

Two-phase native SQL query:
1. `SET pg_trgm.similarity_threshold = :threshold` — configures the similarity threshold for the session
2. `WHERE value % :inputValue` — leverages GIN index for fast blocking via the `%` operator
3. Returns candidateEntityId, attributeId, value, and `similarity(value, :inputValue)` score
4. Limit 50 per attribute query

### mergeCandidates

Groups results by (candidateEntityId, signalType) and keeps only the highest similarity score per group. Prevents duplicate candidate entries when multiple attributes of the same type match.

### normalizeValue

Simple normalization: `value.trim().lowercase()`. Applied to both trigger attribute values and compared against stored values.

## Data Access

| Table | Access Method | Query Type |
|---|---|---|
| entity_attributes | EntityManager native SQL | SELECT with pg_trgm `%` operator and `similarity()` function |
| entity_type_semantic_metadata | EntityManager native SQL (join) | JOIN for IDENTIFIER classification filtering |

Does not use JPA repositories for these queries — pg_trgm operators are not available in JPQL.

## Gotchas

- **No @PreAuthorize** — workspace isolation is enforced in the WHERE clause of native queries, not via Spring Security annotations. This service is called from Temporal without a JWT context.
- **EntityManager for native SQL** — pg_trgm operators (`%`, `similarity()`) are PostgreSQL-specific and not available in JPQL, requiring native SQL via EntityManager.
- **Candidate limit hardcoded at 50** — per-attribute query limit is not configurable.
- **Self-exclusion** — the trigger entity is excluded from candidate results via `AND ea.entity_id != :triggerEntityId`.

## Error Handling

- Logs and returns an empty list if the trigger entity has no IDENTIFIER attributes.
- No exceptions thrown to the caller — failures are logged and result in empty candidate lists.

## Configuration

No configuration properties. Similarity threshold is hardcoded in the native SQL query.
