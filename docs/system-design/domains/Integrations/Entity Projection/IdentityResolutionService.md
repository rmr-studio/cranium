---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-29
Domains:
  - "[[Integrations]]"
---

# IdentityResolutionService

## Purpose

Batch identity resolution for the projection pipeline. Resolves integration entities to existing core lifecycle entities using a two-query strategy: fast `sourceExternalId` match, then IDENTIFIER attribute value fallback. Architecturally distinct from the [[Identity Resolution]] domain's async matching pipeline — this service does deterministic, synchronous matching at ingestion time.

## Responsibilities

- Batch resolve integration entities against existing core entities of a target type
- Check 1: `sourceExternalId` match across integration boundaries (fast, O(1) per entity)
- Check 2: IDENTIFIER-classified attribute value match for entities without external ID matches
- Detect and handle ambiguous identifier matches (>1 core entity sharing same value)
- Return structured resolution results (`ExistingEntity` with matchType, or `NewEntity` with warnings)

**NOT responsible for:** Probabilistic matching (owned by IdentityMatchCandidateService), pg_trgm fuzzy matching, match suggestions, human review.

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `EntityRepository` | Batch `sourceExternalId` lookups on target entity type |
| `EntityAttributeRepository` | JSONB value extraction for identifier key matching |
| [[EntityTypeClassificationService]] | Queries which attributes are IDENTIFIER-classified for a given entity type |
| `KLogger` | Structured logging |

## Consumed By

| Consumer | Context |
|----------|---------|
| [[EntityProjectionService]] | Called once per chunk during projection processing |

## Key Methods

### `resolveBatch(entities: List<EntityEntity>, workspaceId: UUID, targetEntityTypeId: UUID): Map<UUID, ResolutionResult>`

Orchestrates both resolution checks sequentially. Check 1 runs on all entities. Check 2 runs only on entities unmatched by Check 1. Remaining unmatched entities are tagged as `ResolutionResult.NewEntity()`. Returns a map keyed by integration entity ID.

### Private: `resolveByExternalId(entities, workspaceId, targetEntityTypeId): Map<UUID, ResolutionResult>`

Builds `sourceExternalId -> integrationEntity` map (filtering out null external IDs). Queries `entityRepository.findByTypeIdAndWorkspaceIdAndSourceExternalIdIn()` to find core entities of the target type with matching external IDs. Returns `ExistingEntity` results with `matchType = MatchType.EXTERNAL_ID`.

### Private: `resolveByIdentifierKey(entities, workspaceId, targetEntityTypeId): Map<UUID, ResolutionResult>`

Gets IDENTIFIER attribute IDs from [[EntityTypeClassificationService]] via `getIdentifierAttributeIds()`. Loads all attributes for unmatched entities via `entityAttributeRepository.findByEntityIdIn()`. Extracts identifier text values from JSONB `value ->> 'value'`. Queries `entityAttributeRepository.findByIdentifierValuesForEntityType()` (native SQL with JSONB extraction). Groups matches by value to detect ambiguity: single match produces `ExistingEntity(IDENTIFIER_KEY)`, multiple distinct core entities produce `NewEntity` with a warning.

## Key Logic

**Two-query resolution strategy:**

1. **Check 1 (External ID):** Fast batch lookup. Uses the `idx_entities_identity_resolution` index on `(entity_type_id, workspace_id, source_external_id)`. Matches across integration boundaries — an entity from Zendesk can match a projected entity originally from Stripe if they share an external ID.

2. **Check 2 (Identifier Key):** Fallback for entities without external ID matches. Uses IDENTIFIER-classified attributes (e.g., email, customer ID) to find matching core entities. Native SQL query extracts JSONB text values for comparison.

**Ambiguity detection:** If Check 2 finds >1 distinct core entity sharing the same identifier value, the match is treated as `NewEntity` (not `ExistingEntity`) with a warning string. This prevents incorrect merges when identifier uniqueness has been violated.

## Gotchas

- **Not the same as the Identity Resolution domain.** This service does deterministic, synchronous matching at ingestion time. The [[Identity Resolution]] domain does probabilistic, async matching with pg_trgm fuzzy scoring and human review.
- **Cross-integration matching.** Check 1 intentionally matches across integration boundaries by entity type only (not by `source_integration_id`). This is by design — a projected entity created from one integration can be matched by a different integration's entity if they share the same `sourceExternalId`.
- **JSONB extraction.** Check 2 uses native SQL `ea.value ->> 'value'` to extract text values from the JSONB attribute column. This is specific to the normalized attribute storage format where each attribute value is `{"value": "..."}`.
- **Ambiguous matches silently downgrade.** Multiple core entities sharing an identifier value do not cause an error — they produce a `NewEntity` result with a warnings list, which may lead to duplicate entity creation. The warning is logged at WARN level.

## Testing

- **Location:** `src/test/kotlin/riven/core/service/ingestion/IdentityResolutionServiceTest.kt`
- **Key scenarios:** External ID match, identifier key match, ambiguous match detection, empty batch, no identifier attributes, mixed match types

## Flows

- [[Flow - Entity Projection Pipeline]]

## Related

- [[EntityProjectionService]] — Primary consumer
- [[EntityTypeClassificationService]] — Provides IDENTIFIER attribute IDs
- [[Identity Resolution]] — The async matching domain (distinct from this service)
- [[Entity Projection]] — Parent subdomain
