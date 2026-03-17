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

# IdentityMatchSuggestionService

## Purpose

Manages the write path for match suggestions — idempotent persistence, re-suggestion when scores improve, rejection with signal snapshot, and activity audit logging.

## Responsibilities

**Owns:**
- Suggestion creation with canonical UUID ordering (source < target)
- Idempotent create (catches DataIntegrityViolationException for duplicate pairs)
- Re-suggestion logic (new score > rejected score triggers soft-delete of old suggestion + create new)
- Rejection with signal snapshot (copies signals to rejectionSignals)
- Activity logging for suggestion creation and rejection

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

## Consumed By

| Consumer | Method | Context |
|---|---|---|
| IdentityMatchActivitiesImpl | persistSuggestions | Temporal activity — PersistSuggestions step |
| Future REST controller | rejectSuggestion | User-initiated rejection (not yet exposed) |

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

### rejectSuggestion

```kotlin
fun rejectSuggestion(suggestionId: UUID, userId: UUID): MatchSuggestion
```

Transitions a suggestion to REJECTED status. Validates that the suggestion is currently PENDING. Snapshots the current signals as rejectionSignals for future re-suggestion comparison.

**Throws:** IllegalArgumentException if the suggestion status is not PENDING.

## Key Logic

### Canonical Ordering

`canonicalOrder(a, b)` returns (lower, higher) UUID pair. This prevents duplicate suggestion pairs (A,B vs B,A). Enforced both in application code and via a DB CHECK constraint: `source_entity_id < target_entity_id`.

### createOrResuggest Flow

1. Check for an active (non-deleted) suggestion for this pair — if exists, skip
2. Check for a rejected (deleted, status=REJECTED) suggestion for this pair
3. If rejected exists and new score > rejected score: soft-delete the rejected suggestion, create a new one
4. If rejected exists and new score <= rejected score: skip (not worth re-suggesting)
5. If no prior suggestion: create new

### Rejection Flow

1. Validate suggestion status is PENDING
2. Copy current `signals` to `rejectionSignals` (preserves the signal state at rejection time)
3. Set status to REJECTED, record `resolvedBy` (userId) and `resolvedAt` (timestamp)
4. Soft-delete: set `deleted = true`, `deletedAt` = now
5. Log rejection activity

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
| Rejection of non-PENDING suggestion | Throws IllegalArgumentException with descriptive message |
| Activity logging failure | Not explicitly caught — would propagate (acceptable since logging is non-critical path) |
