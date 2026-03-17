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

# IdentityMatchScoringService

## Purpose

Pure computation service that scores candidate entity pairs using weighted average of similarity signals. No database access, no Spring Security context.

## Responsibilities

- Groups candidates by candidate entity ID
- Builds MatchSignal objects from candidate matches
- Computes composite score via weighted average: `Sum(similarity * weight) / Sum(weight)`
- Filters results by minimum threshold (0.5)
- Returns canonical UUID ordering (source < target) in ScoredCandidate output

## Dependencies

| Dependency | Type | Purpose |
|---|---|---|
| KLogger | Injected | Structured logging |

No repositories, no external dependencies. This is a pure computation service.

## Used By

| Consumer | Method | Context |
|---|---|---|
| IdentityMatchActivitiesImpl | scoreCandidates | Temporal activity — ScoreCandidates step |

## Public Methods

### scoreCandidates

```kotlin
fun scoreCandidates(
    triggerEntityId: UUID,
    triggerAttributes: Map<MatchSignalType, String>,
    candidates: List<CandidateMatch>
): List<ScoredCandidate>
```

Single public method. Takes the trigger entity ID, its IDENTIFIER attribute values, and the raw candidate matches from CandidateService.

**Logic:**
1. Groups candidates by `candidateEntityId`
2. For each group: builds signals (best similarity per signal type), computes weighted average
3. Filters: composite score >= 0.5
4. Returns `List<ScoredCandidate>` with composite score, signal breakdown, and canonical UUID ordering

**Returns:** Scored candidates above the minimum threshold, with source/target UUIDs in canonical order (lower UUID = source).

## Gotchas

- Weights come from `MatchSignalType.DEFAULT_WEIGHTS` — not configurable per workspace or entity type.
- The minimum score threshold (0.5) is hardcoded.
- Canonical UUID ordering is applied here so downstream consumers (SuggestionService) receive consistently ordered pairs.
