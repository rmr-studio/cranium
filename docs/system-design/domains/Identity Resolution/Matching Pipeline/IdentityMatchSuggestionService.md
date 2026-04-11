---
tags:
  - component/active
  - layer/service
  - architecture/component
  - domain/identity-resolution
Created: 2026-03-17
Domains:
  - "[[riven/docs/system-design/domains/Identity Resolution/Identity Resolution]]"
Sub-Domains:
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Identity Resolution/Matching Pipeline/Matching Pipeline]]"
---

# IdentityMatchSuggestionService

## Purpose

Manages the write path for match suggestions — idempotent persistence, re-suggestion when scores improve, same-cluster guard, and activity audit logging.

## Responsibilities

**Owns:**
- Suggestion creation with canonical UUID ordering (source < target)
- Idempotent create (catches DataIntegrityViolationException for duplicate pairs)
- Re-suggestion logic (new score > rejected score triggers soft-delete of old suggestion + create new)
- Same-cluster guard (skips suggestions for entities already in the same active cluster)
- Activity logging for suggestion creation

**Does NOT own:**
- Candidate finding (IdentityMatchCandidateService)
- Scoring (IdentityMatchScoringService)
- Queue management

## Dependencies

| Dependency | Type | Purpose |
|---|---|---|
| MatchSuggestionRepository | Injected | Suggestion persistence and lookup (native SQL queries) |
| ActivityService | Injected | Audit logging for suggestion create/reject |
| AuthTokenService | Injected | User ID retrieval for rejection flow |
| KLogger | Injected | Structured logging |
| IdentityClusterMemberRepository | Injected | Cluster membership lookup for same-cluster guard |
| IdentityClusterRepository | Injected | Cluster existence verification (active cluster check) |

## Consumed By

| Consumer | Method | Context |
|---|---|---|
| IdentityMatchActivitiesImpl | persistSuggestions | Temporal activity — PersistSuggestions step |

## Public Interface

### persistSuggestions

```kotlin
fun persistSuggestions(
    workspaceId: UUID,
    scoredCandidates: List<ScoredCandidate>,
    userId: UUID?
): Int
```

Persists match suggestions for scored candidates. Idempotent — duplicate pairs are silently skipped. Returns the count of suggestions created.

**Parameters:**
- `workspaceId` — workspace scope for the suggestions
- `scoredCandidates` — scored candidate pairs from ScoringService (already in canonical UUID order)
- `userId` — may be null in Temporal context (no JWT). Activity logging is skipped when null.

## Key Logic

### Canonical Ordering

`canonicalOrder(a, b)` returns (lower, higher) UUID pair. This prevents duplicate suggestion pairs (A,B vs B,A). Enforced both in application code and via a DB CHECK constraint: `source_entity_id < target_entity_id`.

### Same-Cluster Guard

Before checking for existing suggestions, `inSameCluster(sourceId, targetId)` verifies both entities aren't already members of the same active identity cluster. Uses `IdentityClusterMemberRepository.findByEntityId` to look up memberships, then verifies the shared cluster is not soft-deleted via `IdentityClusterRepository.findById` (which respects @SQLRestriction). This prevents redundant suggestions for entities already confirmed as the same identity.

### createOrResuggest Flow

1. Check if both entities are already in the same active cluster — if so, skip (no point suggesting a match for already-clustered entities)
2. Check for an active (non-deleted) suggestion for this pair — if exists, skip
3. Check for a rejected (deleted, status=REJECTED) suggestion for this pair
4. If rejected exists and new score > rejected score: soft-delete the rejected suggestion, create a new one
5. If rejected exists and new score <= rejected score: skip (not worth re-suggesting)
6. If no prior suggestion: create new

### Activity Logging

- `logSuggestionCreated`: Logs via ActivityService with suggestion details, source/target entity IDs, and composite score
- `logRejectionActivity`: Logs via ActivityService with suggestion ID and rejecting user

Activity logging is only performed when `userId` is non-null. In Temporal context (no JWT), logging is skipped.

## Data Access

| Table | Access Method | Query Type |
|---|---|---|
| match_suggestions | MatchSuggestionRepository | Native SQL for findActiveSuggestion and findRejectedSuggestion |

### Repository Query Notes

- `findActiveSuggestion` uses native SQL with `deleted = false` — this is necessary because the query needs explicit control over the deleted filter.
- `findRejectedSuggestion` uses native SQL with `deleted = true AND status = 'REJECTED'` — this bypasses the `@SQLRestriction("deleted = false")` that would normally hide soft-deleted rows.

## Gotchas

- **Native SQL bypasses @SQLRestriction** — both `findActiveSuggestion` and `findRejectedSuggestion` use native queries because they need explicit control over the `deleted` column filter. Standard JPQL queries would auto-append `AND deleted = false`, hiding rejected suggestions.
- **Canonical ordering enforced twice** — both in application code (`canonicalOrder()`) and via a DB CHECK constraint (`source_entity_id < target_entity_id`). The DB constraint is the safety net; the application code is for correctness without relying on constraint violations.
- **userId may be null** — when called from Temporal context, there is no JWT. Activity logging is skipped entirely when userId is null.
- **DataIntegrityViolationException is expected** — caught on suggestion creation for duplicate pairs. This is the idempotency mechanism, not an error condition.

## Error Handling

| Scenario | Behaviour |
|---|---|
| Duplicate pair insert | Catches DataIntegrityViolationException, returns null (idempotent) |
| Activity logging failure | Not explicitly caught — would propagate (acceptable since logging is non-critical path) |
